import {test} from 'node:test';
import assert from 'node:assert/strict';
import {issueCode, linkAccount, saveSnapshot, snapshot, sync} from '../src/bridge.js';
import {digest} from '../src/discord.js';
import {environment, ids, uuid} from './helpers.js';

test('codes expire, replace earlier codes and are stored only as hashes', async () => {
  const env = environment();
  const first = await issueCode(env, {uuid, username: 'Mando'});
  const second = await issueCode(env, {uuid, username: 'Mando'});
  assert.match(second.code, /^[0123456789ABCDEFGHJKMNPQRSTVWXYZ]{8}$/);
  assert.equal(env.DB.sqlite.prepare('SELECT * FROM codes').get().code_hash, await digest(second.code));
  await assert.rejects(linkAccount(env, ids.user, first.code), /Invalid or expired/);
  env.DB.sqlite.exec('UPDATE codes SET expires=0');
  await assert.rejects(linkAccount(env, ids.user, second.code), /Invalid or expired/);
});
test('linking consumes proof exactly once and prevents duplicate ownership', async () => {
  const env = environment(); const code = await issueCode(env, {uuid, username: 'Mando'});
  assert.match(await linkAccount(env, ids.user, code.code.toLowerCase()), /Account linked/);
  await assert.rejects(linkAccount(env, ids.staff, code.code), /Invalid or expired/);
  assert.equal(env.DB.sqlite.prepare('SELECT count(*) AS n FROM codes').get().n, 0);
  assert.deepEqual(await issueCode(env, {uuid, username: 'Mando'}), {linked: true});
  assert.match(await linkAccount(env, ids.user), /Mando/);
});
test('link attempts are throttled per Discord user', async () => {
  const env = environment();
  for (let i = 0; i < 5; i++) await assert.rejects(linkAccount(env, ids.user, 'invalid'), /Invalid or expired/);
  await assert.rejects(linkAccount(env, ids.user, 'invalid'), /Too many/);
});
test('short codes accept grouped typing and old issued codes still redeem', async () => {
  const env = environment();
  env.DB.sqlite.prepare('INSERT INTO codes VALUES(?,?,?,?)').run(uuid,'Mando',await digest('ABCD0123'),Math.floor(Date.now()/1000)+600);
  assert.match(await linkAccount(env,ids.user,'abcd-ol23'),/Account linked/);
  const legacy = environment();
  legacy.DB.sqlite.prepare('INSERT INTO codes VALUES(?,?,?,?)').run(uuid,'Mando',await digest('ABCDEF0123456789ABCD'),Math.floor(Date.now()/1000)+600);
  assert.match(await linkAccount(legacy,ids.user,'ABCDEF0123456789ABCD'),/Account linked/);
});
test('concurrent code redemption yields exactly one linked owner', async () => {
  const env = environment(); const code = await issueCode(env, {uuid, username: 'Mando'});
  const results = await Promise.allSettled([linkAccount(env, ids.user, code.code), linkAccount(env, ids.staff, code.code)]);
  assert.equal(results.filter(x => x.status === 'fulfilled').length, 1);
  assert.equal(env.DB.sqlite.prepare('SELECT count(*) AS n FROM links').get().n, 1);
});
test('snapshot freshness and native integer-cents bounds are enforced', async () => {
  const env = environment(); assert.equal((await snapshot(env)).stale, true);
  await saveSnapshot(env, {online: 2, maximum: 100, baltop: [{username: 'Mando', cents: 12345}]});
  assert.equal((await snapshot(env)).baltop[0].cents, 12345);
  assert.equal((await snapshot(env)).stale, false);
  env.DB.sqlite.exec('UPDATE server_snapshots SET updated=0');
  assert.equal((await snapshot(env)).stale, true);
  await assert.rejects(saveSnapshot(env, {online: 0, maximum: 100, baltop: [{username: 'Bad', cents: 1.5}]}), /Invalid snapshot/);
});

test('native leaderboard snapshots distinguish empty and unavailable boards and validate new rows', async () => {
  const env = environment();
  const base = {online: 0, maximum: 100, baltop: []};
  await saveSnapshot(env, base);
  assert.equal((await snapshot(env)).ilum, undefined);
  await saveSnapshot(env, {...base, kills: [], ilum: [{username: 'Fast_Runner', millis: 61234}]});
  const data = await snapshot(env);
  assert.deepEqual(data.kills, []);
  assert.deepEqual(data.ilum, [{username: 'Fast_Runner', millis: 61234}]);
  await assert.rejects(saveSnapshot(env, {...base, ilum: [{username: 'Fast_Runner', millis: 0}]}), /Invalid snapshot/);
  await assert.rejects(saveSnapshot(env, {...base, kills: [{username: '<@123>', kills: 2}]}), /Invalid snapshot/);
  await assert.rejects(saveSnapshot(env, {...base, kills: [null]}), /Invalid snapshot/);
});

test('end-to-end bidirectional sync, lost replies, partial failure and departures', async t => {
  const env = environment(); const code = await issueCode(env, {uuid, username: 'Mando'});
  await linkAccount(env, ids.user, code.code);
  let roles = ['101010101010101010'], nick = null, absent = false, failRole = false;
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({url, options});
    if (absent) return Response.json({}, {status: 404});
    if (options.method === 'GET') return Response.json({roles, nick});
    if (options.method === 'PATCH') {
      nick = JSON.parse(options.body).nick;
      return Response.json({roles, nick});
    }
    const role = url.split('/').at(-1);
    if (failRole) return Response.json({}, {status: 429});
    if (options.method === 'PUT') roles = [...new Set([...roles, role])];
    else roles = roles.filter(r => r !== role);
    return new Response(null, {status: 204});
  });
  const send = ranks => sync(env, {mapping: JSON.parse(env.RANK_ROLES), players: [{uuid, ranks}]});
  await send(['default']);
  assert.ok(roles.includes(ids.linked));
  assert.equal(nick, 'Mando');
  roles.push(ids.role);
  const grant = await send(['default']);
  assert.deepEqual(grant.updates[0].ranks, ['warrior']);
  assert.deepEqual((await send(['default'])).updates[0].ranks, ['warrior']); // dropped reply
  await send(['default', 'warrior']); // durable Minecraft acknowledgment
  roles = roles.filter(role => role !== ids.role);
  assert.deepEqual((await send(['default', 'warrior'])).updates[0].ranks, []);
  await send(['default']);
  failRole = true;
  assert.equal((await send(['default', 'warrior'])).errors.length, 1);
  failRole = false;
  assert.deepEqual((await send(['default', 'warrior'])).updates[0].ranks, ['warrior']);
  assert.ok(roles.includes(ids.role));
  assert.ok(roles.includes('101010101010101010')); // unrelated role preserved
  absent = true;
  assert.deepEqual((await send(['default', 'warrior'])).updates[0], {uuid, absent: true});
  assert.ok(calls.filter(call => call.url.includes('/roles/')).every(call => !call.options.body));
  const nicknameWrites = calls.filter(call => call.options.method === 'PATCH');
  assert.equal(nicknameWrites.length, 1);
  assert.deepEqual(JSON.parse(nicknameWrites[0].options.body), {nick: 'Mando'});
});
test('nickname retries preserve rank and reward sync and respect permission backoff', async t => {
  const env = environment();
  const code = await issueCode(env, {uuid, username: 'Linked_IGN'});
  await linkAccount(env, ids.user, code.code);
  let status = 429, patches = 0, nick = 'Previous nickname';
  t.mock.method(console, 'warn', () => {});
  t.mock.method(globalThis, 'fetch', async (_url, options) => {
    if (options.method === 'GET') return Response.json({roles: [ids.linked], nick});
    assert.equal(options.method, 'PATCH');
    patches++;
    if (status !== 200) return Response.json({}, {status});
    nick = JSON.parse(options.body).nick;
    return Response.json({nick});
  });
  const send = () => sync(env, {mapping: JSON.parse(env.RANK_ROLES), players: [{uuid, ranks: []}]});
  assert.equal((await send()).updates[0].reward, true);
  status = 403;
  assert.equal((await send()).updates[0].reward, true);
  assert.equal(patches, 2);
  status = 200;
  await send();
  assert.equal(patches, 2);
  env.DB.sqlite.exec('UPDATE links SET nickname_sync_after=0');
  await send();
  assert.equal(nick, 'Linked_IGN');
  assert.equal(patches, 3);
  nick = 'Manually picked';
  await send();
  assert.equal(patches, 3);
  assert.equal(nick, 'Manually picked');
});
test('development can set the nickname before production initializes rank sync', async t => {
  const env = environment();
  const code = await issueCode(env, {uuid, username: 'Mando'});
  await linkAccount(env, ids.user, code.code);
  const writes = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (options.method === 'GET') return Response.json({roles: []});
    writes.push({url, method: options.method, body: options.body});
    return Response.json({});
  });
  const result = await sync(env, {mapping: JSON.parse(env.RANK_ROLES), players: [{uuid, ranks: ['warrior']}]},
    'development');
  assert.deepEqual(result.updates, [{uuid, awaiting_primary: true}]);
  assert.deepEqual(writes, [{url: `https://discord.com/api/v10/guilds/${ids.guild}/members/${ids.user}`, method: 'PATCH',
    body: '{"nick":"Mando"}'}]);
  assert.equal(env.DB.sqlite.prepare('SELECT nickname_sync_after FROM links').get().nickname_sync_after, null);
});
test('mismatched mapping and oversized batches fail before Discord access', async () => {
  const env = environment();
  await assert.rejects(sync(env, {mapping: {}, players: []}), /mapping mismatch/);
  await assert.rejects(sync(env, {mapping: {}, players: [{}, {}, {}]}), /Batch limit/);
});
test('sync lease serializes competing server processes', async () => {
  const env = environment();
  env.DB.sqlite.prepare('INSERT INTO leases VALUES(?,?,?)').run('sync', 'other', Date.now() + 60000);
  await assert.rejects(sync(env, {mapping: JSON.parse(env.RANK_ROLES), players: []}), /Another sync/);
});
