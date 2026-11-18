import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker from '../src/worker.js';
import {authorized, verifySignature} from '../src/discord.js';
import {environment, ids} from './helpers.js';

async function signed(env, body, timestamp = `${Math.floor(Date.now() / 1000)}`) {
  const pair = await crypto.subtle.generateKey('Ed25519', true, ['sign', 'verify']);
  const hex = buffer => Buffer.from(buffer).toString('hex');
  env.DISCORD_PUBLIC_KEY = hex(await crypto.subtle.exportKey('raw', pair.publicKey));
  const raw = JSON.stringify(body);
  const signature = hex(await crypto.subtle.sign('Ed25519', pair.privateKey, new TextEncoder().encode(timestamp + raw)));
  return new Request('https://bot.example/interactions', {method: 'POST', body: raw,
    headers: {'X-Signature-Timestamp': timestamp, 'X-Signature-Ed25519': signature}});
}
test('signed Discord verification PING succeeds; tampered, unsigned and stale requests fail', async () => {
  const env = environment(); const request = await signed(env, {type: 1});
  assert.deepEqual(await (await worker.fetch(request, env, {})).json(), {type: 1});
  const missing = new Request('https://bot.example/interactions', {method: 'POST', body: '{"type":1}'});
  assert.equal((await worker.fetch(missing, env, {})).status, 401);
  const stale = await signed(env, {type: 1}, '1');
  assert.equal((await worker.fetch(stale, env, {})).status, 401);
  const correct = await signed(env, {type: 1});
  assert.equal(await verifySignature(correct, '{"type":2}', env.DISCORD_PUBLIC_KEY), false);
});
test('bridge rejects missing and short secrets and never follows authentication to other routes', async () => {
  const env = environment();
  assert.equal((await worker.fetch(new Request('https://bot.example/bridge/links'), env, {})).status, 401);
  assert.equal(await authorized(new Request('https://bot.example', {headers: {Authorization: 'Bearer short'}}), 'short'), false);
  const request = new Request('https://bot.example/bridge/links', {headers: {Authorization: `Bearer ${env.BRIDGE_SECRET}`}});
  assert.deepEqual(await (await worker.fetch(request, env, {})).json(), {uuids: [], next: null});
  const wrong = new Request('https://bot.example/bridge/links', {headers: {Authorization: `Bearer ${env.GATEWAY_SECRET}`}});
  assert.equal((await worker.fetch(wrong, env, {})).status, 401);
});
test('wrong guild cannot invoke commands', async () => {
  const env = environment();
  const request = await signed(env, {type: 2, guild_id: ids.log, application_id: ids.app, member: {user: {id: ids.user}}});
  assert.equal((await (await worker.fetch(request, env, {})).json()).type, 4);
});
test('command immediately defers; processing replay does not execute twice', async t => {
  const env = environment(); const tasks = [], replies = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => { replies.push(JSON.parse(options.body)); return Response.json({}); });
  const interaction = {id: ids.channel, token: 'not-a-real-token', type: 2, application_id: ids.app,
    guild_id: ids.guild, member: {user: {id: ids.user}}, data: {name: 'link'}};
  for (let i = 0; i < 2; i++) {
    const response = await worker.fetch(await signed(env, interaction), env, {waitUntil: task => tasks.push(task)});
    assert.deepEqual(await response.json(), {type: 5, data: {flags: 64}});
  }
  await Promise.all(tasks);
  assert.equal(replies.length, 1);
  assert.match(replies[0].content, /Run \/discord/);
  assert.deepEqual(replies[0].allowed_mentions, {parse: []});
});
