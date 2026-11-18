import {discord, DiscordError, message} from './discord.js';
import {idPattern, UserError} from './bridge.js';
import {openTicket, staff, ticketForChannel} from './tickets.js';

async function member(env, user) {
  try { return await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/members/${user}`); }
  catch (error) { if (error instanceof DiscordError && error.status === 404) return null; throw error; }
}

export async function gatewayEvent(env, event) {
  if (!idPattern.test(event.user || '')) throw new UserError('Invalid event user');
  if (event.event === 'join') {
    if (!await member(env, event.user)) return {ignored: true};
    if (idPattern.test(env.CITIZEN_ROLE_ID || ''))
      await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/members/${event.user}/roles/${env.CITIZEN_ROLE_ID}`, 'PUT');
    return {ok: true};
  }
  if (event.event !== 'message' || !idPattern.test(event.id || '') || !idPattern.test(event.channel || '')) throw new UserError('Invalid message event');
  if (typeof event.content !== 'string' || event.content.length > 4000 || !Array.isArray(event.attachments) || event.attachments.length > 10) throw new UserError('Invalid message content');
  const membership = await member(env, event.user);
  if (!membership) return {ignored: true};
  let destination;
  if (event.guild) {
    if (event.guild !== env.DISCORD_GUILD_ID) return {ignored: true};
    let isStaff = staff(env, membership);
    if (!isStaff) {
      const roles = await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/roles`);
      isStaff = roles.some(role => membership.roles.includes(role.id) && (BigInt(role.permissions) & 8n) !== 0n);
    }
    if (!isStaff) return {ignored: true};
    const ticket = await ticketForChannel(env, event.channel, true);
    if (!ticket || ticket.status !== 'open') return {ignored: true};
    destination = (await discord(env, '/users/@me/channels', 'POST', {recipient_id: ticket.discord_id})).id;
  } else destination = (await openTicket(env, event.user)).channel_id;
  const urls = event.attachments.filter(value => {
    try { const url = new URL(value); return url.protocol === 'https:' && ['cdn.discordapp.com', 'media.discordapp.net'].includes(url.hostname); }
    catch { return false; }
  });
  const content = (event.content + (urls.length ? '\n' + urls.join('\n') : '')).slice(0, 12000) || '[No text content]';
  for (let start = 0; start < content.length; start += 4000) {
    await message(env, destination, {nonce: `${event.id}:${start / 4000}`, enforce_nonce: true,
      embeds: [{description: content.slice(start, start + 4000), color: 0x3498DB,
        author: {name: typeof event.name === 'string' ? event.name.slice(0, 128) : 'MandoMC member'}}]});
  }
  return {ok: true};
}
