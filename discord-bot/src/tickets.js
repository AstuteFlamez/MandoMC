import {discord, message, notify} from './discord.js';
import {idPattern, UserError} from './bridge.js';

export const categories = ['Support', 'Bug Report', 'Player Report', 'Staff Application'];
export const controls = [{type: 1, components: [
  {type: 2, style: 3, label: 'Claim', custom_id: 'claim_ticket'},
  {type: 2, style: 4, label: 'Close', custom_id: 'close_ticket'}
]}];
export const panel = [{type: 1, components: [{type: 3, custom_id: 'ticket_select', placeholder: 'Choose ticket type…',
  options: categories.map(label => ({label, value: label}))}]}];

export function staff(env, member) {
  const ids = JSON.parse(env.STAFF_ROLE_IDS || '[]');
  return (BigInt(member?.permissions || 0) & 8n) !== 0n || member?.roles?.some(role => ids.includes(role));
}

export async function openTicket(env, user, category = 'Support') {
  if (!idPattern.test(user) || !categories.includes(category)) throw new UserError('Invalid ticket');
  if (!idPattern.test(env.TICKET_CATEGORY_ID) || !idPattern.test(env.TICKET_LOG_CHANNEL_ID)) throw new UserError('Tickets are not configured yet.');
  let row = await env.DB.prepare("SELECT * FROM tickets WHERE discord_id=? AND status!='closed'").bind(user).first();
  if (row?.channel_id) return row;
  if (!row) {
    const channels = await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/channels`);
    const existing = channels.find(channel => channel.parent_id === env.TICKET_CATEGORY_ID && channel.topic === user && channel.type === 0);
    if (existing) return ticketForChannel(env, existing.id, true);
  }
  const id = crypto.randomUUID();
  // The reservation also prevents concurrent button/DM requests from creating duplicate channels.
  if (row) throw new UserError('Your ticket is still being created. Please contact staff if it stays unavailable.');
  try {
    await env.DB.prepare("INSERT INTO tickets(id,discord_id,category,status) VALUES(?,?,?,'creating')").bind(id, user, category).run();
  } catch { throw new UserError('You already have a ticket being created.'); }
  let channel;
  try {
    const bot = await discord(env, '/users/@me');
    channel = await discord(env, `/guilds/${env.DISCORD_GUILD_ID}/channels`, 'POST', {
      name: `ticket-${user}`, type: 0, parent_id: env.TICKET_CATEGORY_ID, topic: user,
      permission_overwrites: [
        {id: env.DISCORD_GUILD_ID, type: 0, deny: '1024', allow: '0'},
        {id: user, type: 1, allow: '68608', deny: '0'},
        {id: bot.id, type: 1, allow: '68608', deny: '0'},
        ...JSON.parse(env.STAFF_ROLE_IDS || '[]').map(id => ({id, type: 0, allow: '68608', deny: '0'}))
      ]
    });
    // Record the channel immediately; later message failures leave a recoverable owned ticket.
    await env.DB.prepare("UPDATE tickets SET channel_id=?,status='open' WHERE id=?").bind(channel.id, id).run();
    await message(env, channel.id, {content: `${category} ticket for <@${user}>. You can talk to staff here.`, components: controls});
    await notify(env, user, `Your MandoMC ticket is open: https://discord.com/channels/${env.DISCORD_GUILD_ID}/${channel.id}`);
    return {id, discord_id: user, channel_id: channel.id, category, status: 'open'};
  } catch (error) {
    // An ambiguous channel-create timeout retains its reservation for operator recovery.
    if (channel) throw new UserError(`Your ticket was created: <#${channel.id}>. Staff can restore its controls.`);
    throw error;
  }
}

export async function ticketForChannel(env, channelId, adopt = false) {
  let row = await env.DB.prepare("SELECT * FROM tickets WHERE channel_id=? AND status!='closed'").bind(channelId).first();
  if (row || !adopt) return row;
  const channel = await discord(env, `/channels/${channelId}`);
  // Old bot tickets stored the owner as the channel topic. Category + guild are mandatory.
  if (channel.guild_id !== env.DISCORD_GUILD_ID || channel.parent_id !== env.TICKET_CATEGORY_ID || !idPattern.test(channel.topic || '')) return null;
  await env.DB.prepare("INSERT OR IGNORE INTO tickets(id,discord_id,channel_id,category,status) VALUES(?,?,?,'Support','open')")
    .bind(crypto.randomUUID(), channel.topic, channelId).run();
  return env.DB.prepare("SELECT * FROM tickets WHERE channel_id=? AND status!='closed'").bind(channelId).first();
}

async function transcript(env, row) {
  const messages = []; let before = '';
  for (let page = 0; page < 10; page++) {
    const batch = await discord(env, `/channels/${row.channel_id}/messages?limit=100${before ? `&before=${before}` : ''}`);
    messages.push(...batch);
    if (batch.length < 100) return messages.reverse().map(m => `${m.timestamp} ${m.author.username} (${m.author.id}): ${m.content}\n${(m.embeds || []).map(e => e.description || '').join('\n')}\n${(m.attachments || []).map(a => a.url).join('\n')}`).join('\n');
    before = batch.at(-1).id;
  }
  throw new UserError('This ticket is too large to export automatically. Staff must archive it before deleting it.');
}

export async function controlTicket(env, interaction) {
  if (!staff(env, interaction.member)) throw new UserError('Staff only.');
  const row = await ticketForChannel(env, interaction.channel_id, true);
  if (!row) throw new UserError('This is not a recognized ticket channel.');
  const user = interaction.member.user.id;
  if (interaction.data.custom_id === 'claim_ticket') {
    const result = await env.DB.prepare("UPDATE tickets SET claimed_by=? WHERE id=? AND claimed_by IS NULL AND status='open' RETURNING id").bind(user, row.id).first();
    if (!result) throw new UserError('This ticket is already claimed or closing.');
    await message(env, row.channel_id, {content: `Ticket claimed by <@${user}>.`});
    await notify(env, row.discord_id, 'A staff member has claimed your MandoMC ticket.');
    return 'Ticket claimed.';
  }
  const lease = await env.DB.prepare("UPDATE tickets SET status='closing' WHERE id=? AND status='open' RETURNING id").bind(row.id).first();
  if (!lease) throw new UserError('This ticket is already closing.');
  try {
    const text = await transcript(env, row);
    const form = new FormData();
    form.set('payload_json', JSON.stringify({content: `Ticket for <@${row.discord_id}>, closed by <@${user}>.`, allowed_mentions: {parse: []}}));
    form.set('files[0]', new Blob([text || 'No messages recorded.'], {type: 'text/plain'}), `ticket-${row.channel_id}.txt`);
    await discord(env, `/channels/${env.TICKET_LOG_CHANNEL_ID}/messages`, 'POST', form);
    await discord(env, `/channels/${row.channel_id}`, 'DELETE');
    await env.DB.prepare("UPDATE tickets SET status='closed' WHERE id=?").bind(row.id).run();
    await notify(env, row.discord_id, 'Your MandoMC ticket has been closed. Thank you!');
    return 'Ticket closed and transcript saved.';
  } catch (error) {
    await env.DB.prepare("UPDATE tickets SET status='open' WHERE id=? AND status='closing'").bind(row.id).run();
    throw error;
  }
}
