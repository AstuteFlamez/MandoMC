import {test} from 'node:test';
import assert from 'node:assert/strict';
import {controlTicket, openTicket, panel, controls, ticketForChannel} from '../src/tickets.js';
import {environment, ids} from './helpers.js';

test('legacy ticket custom IDs remain identical', () => {
  assert.equal(panel[0].components[0].custom_id, 'ticket_select');
  assert.deepEqual(controls[0].components.map(x => x.custom_id), ['claim_ticket', 'close_ticket']);
});
test('tickets are private, persisted and deduplicated across process restarts', async t => {
  const env = environment(), created = [];
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.endsWith(`/guilds/${ids.guild}/channels`) && options.method === 'GET') return Response.json([]);
    if (url.endsWith('/users/@me')) return Response.json({id: ids.app});
    if (url.endsWith(`/guilds/${ids.guild}/channels`)) {
      created.push(JSON.parse(options.body)); return Response.json({id: ids.channel});
    }
    if (url.endsWith('/users/@me/channels')) return Response.json({id: ids.user});
    return Response.json({id: 'message'});
  });
  const row = await openTicket(env, ids.user, 'Bug Report');
  assert.equal(row.channel_id, ids.channel);
  const overwrites = created[0].permission_overwrites;
  assert.equal(overwrites.find(x => x.id === ids.guild).deny, '1024');
  assert.ok(overwrites.some(x => x.id === ids.user && x.type === 1));
  assert.equal((await openTicket(env, ids.user)).channel_id, ids.channel);
  assert.equal(created.length, 1);
});
test('existing old-bot channel is adopted instead of duplicated', async t => {
  const env = environment();
  const channel = {id: ids.channel, parent_id: ids.category, guild_id: ids.guild, topic: ids.user, type: 0};
  t.mock.method(globalThis, 'fetch', async url => Response.json(url.endsWith(`/guilds/${ids.guild}/channels`) ? [channel] : channel));
  assert.equal((await openTicket(env, ids.user)).channel_id, ids.channel);
  assert.equal(env.DB.sqlite.prepare('SELECT count(*) AS n FROM tickets').get().n, 1);
});
test('arbitrary channels with numeric topics are not adopted', async t => {
  const env = environment();
  t.mock.method(globalThis, 'fetch', async () => Response.json({guild_id: ids.guild, parent_id: ids.log, topic: ids.user}));
  assert.equal(await ticketForChannel(env, ids.channel, true), null);
});
test('non-staff cannot claim or close; claims are exclusive', async t => {
  const env = environment();
  env.DB.sqlite.prepare("INSERT INTO tickets VALUES(?,?,?,?,?,NULL)").run('ticket', ids.user, ids.channel, 'Support', 'open');
  const interaction = {channel_id: ids.channel, member: {roles: [], user: {id: ids.user}}, data: {custom_id: 'claim_ticket'}};
  await assert.rejects(controlTicket(env, interaction), /Staff only/);
  interaction.member.roles = [ids.staff];
  t.mock.method(globalThis, 'fetch', async () => Response.json({id: ids.channel}));
  assert.equal(await controlTicket(env, interaction), 'Ticket claimed.');
  await assert.rejects(controlTicket(env, interaction), /already claimed/);
});
test('close exports actual channel history before deletion', async t => {
  const env = environment(), order = [];
  env.DB.sqlite.prepare("INSERT INTO tickets VALUES(?,?,?,?,?,NULL)").run('ticket', ids.user, ids.channel, 'Support', 'open');
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (url.includes('/messages?')) return Response.json([{timestamp: 'now', author: {id: ids.user, username: 'Mando'}, content: 'help', attachments: []}]);
    if (url.endsWith(`/channels/${ids.log}/messages`)) {
      order.push('transcript'); assert.ok(options.body instanceof FormData); assert.match(await options.body.get('files[0]').text(), /help/);
    }
    if (options.method === 'DELETE') { order.push('delete'); return Response.json({}); }
    return Response.json({id: ids.user});
  });
  await controlTicket(env, {channel_id: ids.channel, member: {roles: [ids.staff], user: {id: ids.staff}}, data: {custom_id: 'close_ticket'}});
  assert.deepEqual(order, ['transcript', 'delete']);
  assert.equal(env.DB.sqlite.prepare('SELECT status FROM tickets').get().status, 'closed');
});
test('transcript failure never deletes the ticket', async t => {
  const env = environment(); let deleted = false;
  env.DB.sqlite.prepare("INSERT INTO tickets VALUES(?,?,?,?,?,NULL)").run('ticket', ids.user, ids.channel, 'Support', 'open');
  t.mock.method(globalThis, 'fetch', async (url, options) => {
    if (options.method === 'DELETE') deleted = true;
    return Response.json({}, {status: 403});
  });
  await assert.rejects(controlTicket(env, {channel_id: ids.channel, member: {roles: [ids.staff], user: {id: ids.staff}}, data: {custom_id: 'close_ticket'}}));
  assert.equal(deleted, false);
  assert.equal(env.DB.sqlite.prepare('SELECT status FROM tickets').get().status, 'open');
});
