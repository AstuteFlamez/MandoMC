import {test} from 'node:test';
import assert from 'node:assert/strict';
import {gatewayEvent} from '../src/gateway.js';
import worker from '../src/worker.js';
import {environment, ids} from './helpers.js';

const event = () => ({event: 'message', id: ids.app, user: ids.user, channel: ids.channel,
  name: 'Traveler', content: 'Help', attachments: []});
test('Gateway events require the separate secret', async () => {
  const response = await worker.fetch(new Request('https://bot/gateway/event', {method:'POST', body: JSON.stringify(event())}), environment(), {});
  assert.equal(response.status, 401);
});
test('member join assigns only the configured default Citizen role', async t => {
  const env = environment(); env.CITIZEN_ROLE_ID = ids.linked;
  const mutations = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (options.method !== 'GET') mutations.push([url, options.method]);
    return Response.json({roles: []});
  });
  await gatewayEvent(env, {event:'join', user:ids.user});
  assert.deepEqual(mutations, [[`https://discord.com/api/v10/guilds/${ids.guild}/members/${ids.user}/roles/${ids.linked}`, 'PUT']]);
});
test('outsiders and non-staff cannot relay ticket messages', async t => {
  const env = environment(); let writes = 0;
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (options.method !== 'GET') writes++;
    return Response.json(url.endsWith('/roles') ? [] : {roles:[]});
  });
  assert.deepEqual(await gatewayEvent(env, {...event(), guild:ids.guild}), {ignored:true});
  assert.equal(writes,0);
});
test('DM relay reuses tickets and supplies retry-safe message nonces without mentions', async t => {
  const env = environment(), sent=[];
  env.DB.sqlite.prepare('INSERT INTO tickets VALUES(?,?,?,?,?,NULL)').run('ticket',ids.user,ids.channel,'Support','open');
  t.mock.method(globalThis, 'fetch', async (url,options) => {
    if (url.endsWith('/messages')) sent.push(JSON.parse(options.body));
    return Response.json({roles:[], id:ids.channel});
  });
  const input={...event(),attachments:['https://cdn.discordapp.com/attachments/example.png','https://example.com/evil']};
  await gatewayEvent(env,input); await gatewayEvent(env,input);
  assert.equal(sent.length,2);
  assert.equal(sent[0].nonce,sent[1].nonce); assert.equal(sent[0].enforce_nonce,true);
  assert.deepEqual(sent[0].allowed_mentions,{parse:[]});
  assert.match(sent[0].embeds[0].description,/cdn.discordapp.com/);
  assert.doesNotMatch(sent[0].embeds[0].description,/evil/);
});
test('invalid content is rejected before Discord writes', async () => {
  await assert.rejects(gatewayEvent(environment(),{...event(),content:'x'.repeat(4001)}),/Invalid message content/);
});
