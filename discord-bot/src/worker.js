import {authorized, discord, message, verifySignature} from './discord.js';
import {issueCode, linkAccount, saveSnapshot, snapshot, sync, UserError, uuidPattern, idPattern} from './bridge.js';
import {controlTicket, openTicket, panel, staff, ticketForChannel} from './tickets.js';
import {gatewayEvent} from './gateway.js';

const json = (value, status = 200) => Response.json(value, {status, headers: {'Cache-Control': 'no-store'}});
export const commands = [
  {name: 'link', description: 'Link or check your Minecraft account', options: [{name: 'code', description: 'The code from /discord in Minecraft', type: 3, required: false}]},
  {name: 'baltop', description: 'View the richest players on MandoMC', options: [{type: 3, name: 'server', description: 'Defaults to production', choices: [{name: 'Production', value: 'production'}, {name: 'Development', value: 'development'}]}]},
  {name: 'players', description: 'View the online player count', options: [{type: 3, name: 'server', description: 'Defaults to production', choices: [{name: 'Production', value: 'production'}, {name: 'Development', value: 'development'}]}]},
  {name: 'ticket', description: 'Open a support ticket'},
  {name: 'ticket-panel', description: 'Post the support ticket panel', default_member_permissions: '32'}
].map(command => ({...command, type: 1})); // Guild scope is set by the registration URL.

async function handleInteraction(env, interaction) {
  const user = interaction.member.user.id;
  const server = interaction.data.options?.find(option => option.name === 'server')?.value || 'production';
  if (!['production', 'development'].includes(server)) throw new UserError('Unknown server.');
  if (interaction.type === 3) {
    if (interaction.data.custom_id === 'ticket_select') {
      const ticket = await openTicket(env, user, interaction.data.values?.[0]);
      return {content: `Your ticket: <#${ticket.channel_id}>`};
    }
    if (['claim_ticket', 'close_ticket'].includes(interaction.data.custom_id)) return {content: await controlTicket(env, interaction)};
    throw new UserError('Unknown control.');
  }
  switch (interaction.data.name) {
    case 'link': return {content: await linkAccount(env, user, interaction.data.options?.find(o => o.name === 'code')?.value)};
    case 'players': {
      const data = await snapshot(env, server);
      return {content: data.stale ? `${server}: the server is offline or its status has not updated recently.` : `${server}: ${data.online} ${data.online === 1 ? 'player' : 'players'} online (${data.maximum} slots).`};
    }
    case 'baltop': {
      const data = await snapshot(env, server);
      if (data.stale) throw new UserError('Economy data is unavailable until the server reconnects.');
      return {embeds: [{title: `MandoMC Baltop — ${server}`, color: 0xFFD700, description: data.baltop.map((row, i) =>
        `${i + 1}. **${row.username.replaceAll('_', '\\_')}** — ${(row.cents / 100).toLocaleString('en-US', {minimumFractionDigits: 2})} credits`).join('\n') || 'No balance data yet.',
        footer: {text: 'MandoMC Economy • updated every 30 seconds'}}]};
    }
    case 'ticket': {
      const ticket = await openTicket(env, user);
      return {content: `Your ticket: <#${ticket.channel_id}>`};
    }
    case 'ticket-panel': {
      if (!staff(env, interaction.member) && (BigInt(interaction.member.permissions || 0) & 32n) === 0n) throw new UserError('Manage Server permission is required.');
      await message(env, interaction.channel_id, {content: 'MandoMC Support — choose a ticket type.', components: panel});
      return {content: 'Ticket panel posted.'};
    }
    default: throw new UserError('Unknown command.');
  }
}

async function complete(env, interaction) {
  let body;
  try {
    const dedup = await env.DB.prepare('INSERT OR IGNORE INTO leases(id,owner,expires) VALUES(?,?,?)')
      .bind(`interaction:${interaction.id}`, 'interaction', Date.now() + 900000).run();
    if (!dedup.meta.changes) return;
    body = await handleInteraction(env, interaction);
  } catch (error) {
    body = {content: error instanceof UserError ? error.message : 'Service temporarily unavailable. Please try again or contact staff.'};
    if (!(error instanceof UserError)) console.error('Interaction failed', error.name);
  }
  // Tokens never appear in logs or exception messages.
  try {
    await discord(env, `/webhooks/${env.DISCORD_APPLICATION_ID}/${interaction.token}/messages/@original`, 'PATCH', {...body, allowed_mentions: {parse: []}});
  } catch { console.error('Interaction reply delivery failed'); }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    try {
      if (url.pathname === '/health' && request.method === 'GET') return json({service: 'MandoMC Discord', ok: true});
      if (['/interactions', '/api/interactions'].includes(url.pathname) && request.method === 'POST') {
        const raw = await request.text();
        if (raw.length > 65536) return json({error: 'Too large'}, 413);
        if (!await verifySignature(request, raw, env.DISCORD_PUBLIC_KEY)) return json({error: 'Invalid signature'}, 401);
        const interaction = JSON.parse(raw);
        if (interaction.type === 1) return json({type: 1});
        if (interaction.guild_id !== env.DISCORD_GUILD_ID || !interaction.member?.user ||
            interaction.application_id !== env.DISCORD_APPLICATION_ID) return json({type: 4, data: {content: 'Use this bot in the MandoMC server.', flags: 64}});
        if (![2, 3].includes(interaction.type)) return json({type: 4, data: {content: 'Unsupported interaction.', flags: 64}});
        ctx.waitUntil(complete(env, interaction));
        const isPublic = interaction.type === 2 && ['baltop', 'players'].includes(interaction.data.name);
        return json({type: 5, data: {flags: isPublic ? 0 : 64}});
      }
      if (url.pathname.startsWith('/bridge/')) {
        const server = await authorized(request, env.BRIDGE_SECRET) ? 'production'
          : await authorized(request, env.DEV_BRIDGE_SECRET) ? 'development' : null;
        if (!server) return json({error: 'Unauthorized'}, 401);
        if (url.pathname === '/bridge/links' && request.method === 'GET') {
          const after = url.searchParams.get('after') || '';
          if (after && !uuidPattern.test(after)) throw new UserError('Invalid cursor');
          const {results} = await env.DB.prepare('SELECT uuid FROM links WHERE uuid > ? ORDER BY uuid LIMIT 100').bind(after).all();
          return json({uuids: results.map(row => row.uuid), next: results.length === 100 ? results.at(-1).uuid : null});
        }
        if (request.method !== 'POST') return json({error: 'Method not allowed'}, 405);
        const raw = await request.text();
        if (raw.length > 65536) return json({error: 'Too large'}, 413);
        const body = JSON.parse(raw);
        if (url.pathname === '/bridge/code') return json(await issueCode(env, body));
        if (url.pathname === '/bridge/snapshot') return json(await saveSnapshot(env, body, server));
        if (url.pathname === '/bridge/sync') return json(await sync(env, body, server));
      }
      if (url.pathname.startsWith('/gateway/')) {
        if (!await authorized(request, env.GATEWAY_SECRET)) return json({error: 'Unauthorized'}, 401);
        if (url.pathname === '/gateway/status' && request.method === 'GET') return json(await snapshot(env));
        if (url.pathname === '/gateway/event' && request.method === 'POST') {
          const raw = await request.text();
          if (raw.length > 65536) return json({error: 'Too large'}, 413);
          return json(await gatewayEvent(env, JSON.parse(raw)));
        }
        if (url.pathname === '/gateway/ticket' && request.method === 'POST') {
          const body = await request.json();
          if (body.user && idPattern.test(body.user)) return json(await openTicket(env, body.user));
          if (body.channel && idPattern.test(body.channel)) return json(await ticketForChannel(env, body.channel, true) || {});
          throw new UserError('Invalid ticket lookup');
        }
      }
      return json({error: 'Not found'}, 404);
    } catch (error) {
      if (error instanceof UserError || error instanceof SyntaxError) return json({error: error.message}, 400);
      console.error('Request failed', error.name);
      return json({error: 'Temporarily unavailable'}, 503);
    }
  }
};
