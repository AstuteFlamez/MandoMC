import {canonical, reconcileShared, rankMapping} from './sync.js';
import {digest, discord, DiscordError} from './discord.js';

export class UserError extends Error {}
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export const idPattern = /^\d{17,20}$/;
const usernamePattern = /^[A-Za-z0-9_]{1,16}$/;
const nowSeconds = () => Math.floor(Date.now() / 1000);

export async function issueCode(env, body) {
  if (!uuidPattern.test(body.uuid) || typeof body.username !== 'string' || !usernamePattern.test(body.username)) {
    throw new UserError('Invalid account');
  }
  const existing = await env.DB.prepare('SELECT discord_id FROM links WHERE uuid = ?').bind(body.uuid).first();
  if (existing) return {linked: true};
  const alphabet = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
  const code = [...crypto.getRandomValues(new Uint8Array(8))].map(n => alphabet[n & 31]).join('');
  await env.DB.batch([
    env.DB.prepare('DELETE FROM codes WHERE expires < ?').bind(nowSeconds()),
    env.DB.prepare('INSERT INTO codes(uuid, username, code_hash, expires) VALUES(?,?,?,?) ON CONFLICT(uuid) DO UPDATE SET username=excluded.username,code_hash=excluded.code_hash,expires=excluded.expires')
      .bind(body.uuid, body.username, await digest(code), nowSeconds() + 600)
  ]);
  return {code, expires_in: 600};
}

export async function linkAccount(env, user, code) {
  const existing = await env.DB.prepare('SELECT username FROM links WHERE discord_id = ?').bind(user).first();
  if (existing) return `Your account is linked to ${existing.username}.`;
  if (!code) return 'Run /discord in Minecraft, then use /link code:<code> here.';
  const window = Math.floor(nowSeconds() / 60);
  const attempt = await env.DB.prepare(`INSERT INTO attempts(discord_id,window,count) VALUES(?,?,1)
    ON CONFLICT(discord_id) DO UPDATE SET window=excluded.window,count=CASE WHEN attempts.window=excluded.window THEN attempts.count+1 ELSE 1 END RETURNING count`)
    .bind(user, window).first();
  if (attempt.count > 5) throw new UserError('Too many attempts. Try again in a minute.');
  code = code.toUpperCase().trim();
  if (code.replaceAll('-', '').length === 8) code = code.replaceAll('-', '').replaceAll('O', '0').replace(/[IL]/g, '1');
  // Previously issued codes remain valid for their original ten-minute lifetime.
  if (!/^(?:[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{8}|[A-F0-9]{20})$/.test(code)) throw new UserError('Invalid or expired code.');
  const hash = await digest(code);
  let result;
  try {
    result = await env.DB.batch([
      env.DB.prepare('INSERT INTO links(uuid,discord_id,username,nickname_sync_after) SELECT uuid,?,username,0 FROM codes WHERE code_hash=? AND expires>=?')
        .bind(user, hash, nowSeconds()),
      env.DB.prepare('DELETE FROM codes WHERE code_hash=? AND uuid IN (SELECT uuid FROM links WHERE discord_id=?)').bind(hash, user)
    ]);
  } catch { throw new UserError('This Minecraft or Discord account is already linked.'); }
  if (result[0].meta.changes !== 1) throw new UserError('Invalid or expired code.');
  // Durable pending state lets the bridge retry Discord updates after committing the link.
  return 'Account linked. Your roles and Minecraft nickname will synchronize shortly. C0-M5 will credit your one-time 5,000-credit reward after your Minecraft account has joined the server.';
}

async function syncNickname(env, row, member, path) {
  let next = null;
  try {
    if (member.nick !== row.username) await discord(env, path, 'PATCH', {nick: row.username});
  } catch (error) {
    if (!(error instanceof DiscordError) || error.status !== 403) return; // Retry transient failures next cycle.
    next = nowSeconds() + 3600;
    console.warn('C0-M5 could not set a nickname. Check Manage Nicknames and the Discord role hierarchy.');
  }
  await env.DB.prepare('UPDATE links SET nickname_sync_after=? WHERE uuid=?').bind(next, row.uuid).run();
}

async function syncPlayer(env, player, mapping, server) {
  if (!uuidPattern.test(player.uuid) || !Array.isArray(player.ranks) || player.ranks.length > 32 ||
      player.ranks.some(r => typeof r !== 'string' || !/^[a-z][a-z0-9_]{0,31}$/.test(r))) throw new UserError('Invalid ranks');
  const row = await env.DB.prepare('SELECT * FROM links WHERE uuid=?').bind(player.uuid).first();
  if (!row) return null;
  const previous = row.state ? JSON.parse(row.state) : null;
  const awaitingPrimary = server !== 'production' && (!previous ||
    previous.signature !== JSON.stringify(Object.entries(mapping).sort(([a], [b]) => a.localeCompare(b))));
  const nicknamePending = row.nickname_sync_after != null && row.nickname_sync_after <= nowSeconds();
  if (awaitingPrimary && !nicknamePending) return {uuid: player.uuid, awaiting_primary: true};
  const memberPath = `/guilds/${env.DISCORD_GUILD_ID}/members/${row.discord_id}`;
  let member;
  try { member = await discord(env, memberPath); }
  catch (error) {
    // A guild departure is not a demotion. Retain the link and all Minecraft memberships.
    if (error instanceof DiscordError && error.status === 404) return {uuid: player.uuid, absent: true};
    throw error;
  }
  // The shared lease permits either bridge to apply the linked name before any role changes.
  if (nicknamePending) {
    try { await syncNickname(env, row, member, memberPath); }
    catch { console.warn('C0-M5 could not save nickname sync progress; retrying next cycle.'); }
  }
  if (awaitingPrimary) return {uuid: player.uuid, awaiting_primary: true};
  const current = Object.keys(mapping).filter(rank => member.roles.includes(mapping[rank]));
  const state = reconcileShared(player.ranks, current, previous, mapping, server,
    Number.isSafeInteger(player.ack) ? player.ack : 0, player.ack_signature || '');
  const wanted = new Set(state.target.map(rank => mapping[rank]));
  for (const role of [env.LINKED_ROLE_ID, env.CITIZEN_ROLE_ID].filter(Boolean)) wanted.add(role);
  const managed = new Set([...Object.values(mapping), env.LINKED_ROLE_ID, env.CITIZEN_ROLE_ID].filter(Boolean));
  const changes = [...managed].filter(role => wanted.has(role) !== member.roles.includes(role));
  if (changes.length) {
    state.discordApplied = false;
    // Persist intent before external role mutations. Steady-state polling does not rewrite rows.
    await env.DB.prepare('UPDATE links SET state=? WHERE uuid=?').bind(JSON.stringify(state), player.uuid).run();
  }
  for (const role of changes) {
    await discord(env, `${memberPath}/roles/${role}`, wanted.has(role) ? 'PUT' : 'DELETE');
  }
  state.discordApplied = true;
  state.discordBaseline = state.target;
  const encoded = JSON.stringify(state);
  if (encoded !== row.state) await env.DB.prepare('UPDATE links SET state=? WHERE uuid=?').bind(encoded, player.uuid).run();
  return {uuid: player.uuid, expected: canonical(player.ranks), ranks: state.target,
    revision: state.revision, signature: state.signature, reward: server === 'production'};
}

export async function sync(env, body, server = 'production') {
  if (!Array.isArray(body.players) || body.players.length > 2) throw new UserError('Batch limit is two');
  const mapping = rankMapping(env.RANK_ROLES || '{}');
  if ([env.CITIZEN_ROLE_ID, env.LINKED_ROLE_ID].filter(Boolean).some(role => Object.values(mapping).includes(role))) {
    throw new UserError('Linked and citizen roles must be separate from rank roles');
  }
  // Both ends must approve exactly the same mapping, including privileged ranks.
  if (JSON.stringify(Object.entries(mapping).sort()) !== JSON.stringify(Object.entries(rankMapping(body.mapping)).sort())) {
    throw new UserError('Rank mapping mismatch; synchronize both configurations');
  }
  const owner = crypto.randomUUID(), time = Date.now();
  const lock = await env.DB.prepare(`INSERT INTO leases(id,owner,expires) VALUES('sync',?,?)
    ON CONFLICT(id) DO UPDATE SET owner=excluded.owner,expires=excluded.expires WHERE leases.expires<? RETURNING owner`)
    .bind(owner, time + 120000, time).first();
  if (!lock) throw new UserError('Another sync is running; retry later');
  try {
    const updates = [], errors = [];
    for (const player of body.players) {
      try { const result = await syncPlayer(env, player, mapping, server); if (result) updates.push(result); }
      catch (error) {
        errors.push({uuid: player.uuid, error: error instanceof UserError ? error.message : 'Discord unavailable; retrying next cycle'});
      }
    }
    return {updates, errors};
  } finally {
    await env.DB.prepare("DELETE FROM leases WHERE id='sync' AND owner=?").bind(owner).run();
  }
}

function validBoard(rows, field, minimum = 0) {
  return Array.isArray(rows) && rows.length <= 10 && rows.every(row =>
    typeof row?.username === 'string' && usernamePattern.test(row.username) &&
    Number.isSafeInteger(row[field]) && row[field] >= minimum);
}

export async function saveSnapshot(env, body, server = 'production') {
  const {online, maximum, baltop, kills, ilum} = body;
  if (!Number.isInteger(online) || online < 0 || !Number.isInteger(maximum) || maximum < online ||
      !validBoard(baltop, 'cents') || (kills !== undefined && !validBoard(kills, 'kills')) ||
      (ilum !== undefined && !validBoard(ilum, 'millis', 1))) throw new UserError('Invalid snapshot');
  await env.DB.prepare('INSERT INTO server_snapshots(server,body,updated) VALUES(?,?,?) ON CONFLICT(server) DO UPDATE SET body=excluded.body,updated=excluded.updated')
    .bind(server, JSON.stringify({online, maximum, baltop, kills, ilum}), nowSeconds()).run();
  await env.DB.batch([
    env.DB.prepare('DELETE FROM attempts WHERE window < ?').bind(Math.floor(nowSeconds() / 60) - 60),
    env.DB.prepare('DELETE FROM leases WHERE expires < ?').bind(Date.now())
  ]);
  return {ok: true};
}

export async function snapshot(env, server = 'production') {
  const row = await env.DB.prepare('SELECT * FROM server_snapshots WHERE server=?').bind(server).first();
  return row ? {...JSON.parse(row.body), updated: row.updated, stale: nowSeconds() - row.updated > 120} : {stale: true};
}
