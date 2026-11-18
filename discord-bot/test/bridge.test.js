import {test} from 'node:test';
import assert from 'node:assert/strict';
import {issueCode, linkAccount, saveSnapshot, snapshot, sync} from '../src/bridge.js';
import {digest} from '../src/discord.js';
import {environment, ids, uuid} from './helpers.js';

test('codes expire, replace earlier codes and are stored only as hashes', async () => {
  const env = environment();
  const first = await issueCode(env, {uuid, username: 'Mando'});
  const second = await issueCode(env, {uuid, username: 'Mando'});
  assert.match(second.code, /^[0-9A-F]{20}$/);
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

test('end-to-end bidirectional sync, lost replies, partial failure and departures', async t => {
  const env = environment(); const code = await issueCode(env, {uuid, username: 'Mando'});
  await linkAccount(env, ids.user, code.code);
  let roles = ['101010101010101010'], absent = false, failRole = false;
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    calls.push({url, options});
    if (absent) return Response.json({}, {status: 404});
    if (options.method === 'GET') return Response.json({roles});
    const role = url.split('/').at(-1);
    if (failRole) return Response.json({}, {status: 429});
    if (options.method === 'PUT') roles = [...new Set([...roles, role])];
    else roles = roles.filter(r => r !== role);
    return new Response(null, {status: 204});
  });
  const send = ranks => sync(env, {mapping: JSON.parse(env.RANK_ROLES), players: [{uuid, ranks}]});
  await send(['default']);
  assert.ok(roles.includes(ids.linked));
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
  assert.ok(calls.every(call => !call.url.includes('/members/') || !call.options.body));
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
