import {canonical, reconcile, rankMapping, same} from './sync.js';
import {digest, discord, DiscordError} from './discord.js';

export class UserError extends Error {}
export const uuidPattern = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;
export const idPattern = /^\d{17,20}$/;
const nowSeconds = () => Math.floor(Date.now() / 1000);

export async function issueCode(env, body) {
  if (!uuidPattern.test(body.uuid) || !/^[A-Za-z0-9_]{1,16}$/.test(body.username)) throw new UserError('Invalid account');
  const existing = await env.DB.prepare('SELECT discord_id FROM links WHERE uuid = ?').bind(body.uuid).first();
  if (existing) return {linked: true};
  const code = [...crypto.getRandomValues(new Uint8Array(10))].map(n => n.toString(16).padStart(2, '0')).join('').toUpperCase();
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
  if (!/^[A-F0-9]{20}$/.test(code)) throw new UserError('Invalid or expired code.');
  const hash = await digest(code);
  let result;
  try {
    result = await env.DB.batch([
      env.DB.prepare('INSERT INTO links(uuid,discord_id,username) SELECT uuid,?,username FROM codes WHERE code_hash=? AND expires>=?')
        .bind(user, hash, nowSeconds()),
      env.DB.prepare('DELETE FROM codes WHERE code_hash=? AND uuid IN (SELECT uuid FROM links WHERE discord_id=?)').bind(hash, user)
    ]);
  } catch { throw new UserError('This Minecraft or Discord account is already linked.'); }
  if (result[0].meta.changes !== 1) throw new UserError('Invalid or expired code.');
  // Roles are applied by the retrying bridge, so a transient Discord failure cannot consume a successful link.
  return 'Account linked. Your Discord roles will synchronize on the next server update.';
}

async function syncPlayer(env, player, mapping) {
  if (!uuidPattern.test(player.uuid) || !Array.isArray(player.ranks) || player.ranks.length > 32 ||
      player.ranks.some(r => typeof r !== 'string' || !/^[a-z][a-z0-9_]{0,31}$/.test(r))) throw new UserError('Invalid ranks');
  const row = await env.DB.prepare('SELECT * FROM links WHERE uuid=?').bind(player.uuid).first();
  if (!row) return null;
  let member;
  try { member = await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/members/${row.discord_id}`); }
  catch (error) {
    // A guild departure is not a demotion. Retain the link and all Minecraft memberships.
    if (error instanceof DiscordError && error.status === 404) return {uuid: player.uuid, absent: true};
    throw error;
  }
  const current = Object.keys(mapping).filter(rank => member.roles.includes(mapping[rank]));
  const state = reconcile(player.ranks, current, row.state ? JSON.parse(row.state) : null, mapping);
  state.pending = !same(player.ranks.filter(r => r in mapping), state.target);
  state.discordApplied = false;
  // Persist intent before any external role mutation. Partial success and lost replies are replayed.
  await env.DB.prepare('UPDATE links SET state=? WHERE uuid=?').bind(JSON.stringify(state), player.uuid).run();
  const wanted = new Set(state.target.map(rank => mapping[rank]));
  for (const role of [env.LINKED_ROLE_ID, env.CITIZEN_ROLE_ID].filter(Boolean)) wanted.add(role);
  const managed = new Set([...Object.values(mapping), env.LINKED_ROLE_ID, env.CITIZEN_ROLE_ID].filter(Boolean));
  for (const role of managed) {
    if (wanted.has(role) === member.roles.includes(role)) continue;
    await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/members/${row.discord_id}/roles/${role}`, wanted.has(role) ? 'PUT' : 'DELETE');
  }
  state.discordApplied = true;
  await env.DB.prepare('UPDATE links SET state=? WHERE uuid=?').bind(JSON.stringify(state), player.uuid).run();
  return {uuid: player.uuid, expected: canonical(player.ranks), ranks: state.target};
}

export async function sync(env, body) {
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
      try { const result = await syncPlayer(env, player, mapping); if (result) updates.push(result); }
      catch (error) {
        errors.push({uuid: player.uuid, error: error instanceof UserError ? error.message : 'Discord unavailable; retrying next cycle'});
      }
    }
    return {updates, errors};
  } finally {
    await env.DB.prepare("DELETE FROM leases WHERE id='sync' AND owner=?").bind(owner).run();
  }
}

export async function saveSnapshot(env, body) {
  if (!Number.isInteger(body.online) || body.online < 0 || !Number.isInteger(body.maximum) || body.maximum < body.online ||
      !Array.isArray(body.baltop) || body.baltop.length > 10 || body.baltop.some(row =>
        !/^[A-Za-z0-9_]{1,16}$/.test(row.username) || !Number.isSafeInteger(row.cents) || row.cents < 0)) throw new UserError('Invalid snapshot');
  await env.DB.prepare('INSERT INTO snapshots(id,body,updated) VALUES(1,?,?) ON CONFLICT(id) DO UPDATE SET body=excluded.body,updated=excluded.updated')
    .bind(JSON.stringify({online: body.online, maximum: body.maximum, baltop: body.baltop}), nowSeconds()).run();
  await env.DB.batch([
    env.DB.prepare('DELETE FROM attempts WHERE window < ?').bind(Math.floor(nowSeconds() / 60) - 60),
    env.DB.prepare('DELETE FROM leases WHERE expires < ?').bind(Date.now())
  ]);
  return {ok: true};
}

export async function snapshot(env) {
  const row = await env.DB.prepare('SELECT * FROM snapshots WHERE id=1').first();
  return row ? {...JSON.parse(row.body), updated: row.updated, stale: nowSeconds() - row.updated > 120} : {stale: true};
}
