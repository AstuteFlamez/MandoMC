import {test} from 'node:test';
import assert from 'node:assert/strict';
import {reconcileShared} from '../src/sync.js';
import {issueCode, linkAccount, saveSnapshot, snapshot, sync} from '../src/bridge.js';
import {environment, ids, uuid} from './helpers.js';
import worker from '../src/worker.js';

const map = {warrior: ids.role, marshal: '131313131313131313', creator: '141414141414141414'};
const commit = value => ({...value, discordApplied: true, discordBaseline: value.target});
const step = (state, server, game, discord, ack = 0) => commit(reconcileShared(game, discord, state, map, server, ack, state?.signature));

test('production establishes first baseline; development bootstraps without overriding it', () => {
  assert.equal(reconcileShared(['marshal'], [], null, map, 'development'), null);
  let state = step(null, 'production', ['warrior'], []);
  state = step(state, 'development', ['marshal'], ['warrior']);
  assert.deepEqual(state.target, ['warrior']);
  assert.equal(state.sources.development.pending.revision, 1);
});
test('delayed acknowledgments from either server cannot undo a newer shared target', () => {
  let state = step(null, 'production', ['warrior'], []);
  state = step(state, 'development', [], ['warrior']);
  state = step(state, 'production', ['warrior'], []); // Discord demotion -> revision 2
  assert.deepEqual(state.target, []);
  state = step(state, 'development', ['warrior'], [], 1); // Old grant acknowledgment
  assert.deepEqual(state.target, []);
  state = step(state, 'development', [], [], 2);
  state = step(state, 'development', ['marshal'], [], 2); // Fresh dev edit -> revision 3
  assert.deepEqual(state.target, ['marshal']);
  state = step(state, 'production', [], ['marshal'], 2); // Old prod demotion acknowledgment
  assert.deepEqual(state.target, ['marshal']);
});
test('acknowledgment plus a fresh local edit preserves concurrent changes from the other server', () => {
  let state = step(null, 'production', ['warrior'], []);
  state = step(state, 'development', [], ['warrior']);
  state = step(state, 'production', ['marshal'], ['warrior']);
  assert.deepEqual(state.target, ['marshal']);
  state = step(state, 'development', ['warrior', 'creator'], ['marshal'], 1);
  assert.deepEqual(state.target, ['creator', 'marshal']);
});
test('both-server steady state is stable after restarts and uses no extra rank-state writes', async t => {
  const env = environment(); const code = await issueCode(env, {uuid, username: 'Mando'});
  await linkAccount(env, ids.user, code.code);
  t.mock.method(globalThis, 'fetch', async () => Response.json({roles: [ids.role, ids.linked]}));
  const send = (server, ranks, ack = 0, signature = '') => sync(env, {mapping: JSON.parse(env.RANK_ROLES), players: [{uuid, ranks, ack, ack_signature: signature}]}, server);
  const first = (await send('production', ['warrior'])).updates[0];
  assert.equal(first.reward, true);
  assert.equal((await send('development', ['warrior'])).updates[0].reward, false);
  env.DB.sqlite.exec('CREATE TABLE rank_write_log(n INTEGER); CREATE TRIGGER rank_changed AFTER UPDATE ON links BEGIN INSERT INTO rank_write_log VALUES(1); END;');
  for (let n = 0; n < 5; n++) {
    await send('production', ['warrior'], first.revision, first.signature);
    await send('development', ['warrior'], first.revision, first.signature);
  }
  assert.equal(env.DB.sqlite.prepare('SELECT count(*) AS n FROM rank_write_log').get().n, 0);
});
test('development and production player/balance snapshots remain isolated', async () => {
  const env = environment();
  await saveSnapshot(env, {online: 3, maximum: 100, baltop: [{username: 'Prod', cents: 100}]}, 'production');
  await saveSnapshot(env, {online: 1, maximum: 20, baltop: [{username: 'Dev', cents: 900}]}, 'development');
  assert.equal((await snapshot(env)).online, 3);
  assert.equal((await snapshot(env)).baltop[0].username, 'Prod');
  assert.equal((await snapshot(env, 'development')).online, 1);
  assert.equal((await snapshot(env, 'development')).baltop[0].username, 'Dev');
});
test('separate bridge credentials derive server identity; the request cannot select another server', async () => {
  const env = environment();
  const request = new Request('https://bot.example/bridge/snapshot', {method: 'POST',
    headers: {Authorization: `Bearer ${env.DEV_BRIDGE_SECRET}`},
    body: JSON.stringify({server: 'production', online: 2, maximum: 20, baltop: []})});
  assert.equal((await worker.fetch(request, env, {})).status, 200);
  assert.equal((await snapshot(env)).stale, true);
  assert.equal((await snapshot(env, 'development')).online, 2);
});
