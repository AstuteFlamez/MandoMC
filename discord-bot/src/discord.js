export class DiscordError extends Error {
  constructor(status) { super(`Discord API returned ${status}`); this.status = status; }
}

export async function discord(env, path, method = 'GET', body) {
  const form = body instanceof FormData;
  const result = await fetch(`https://discord.com/api/v10${path}`, {
    method,
    headers: {Authorization: `Bot ${env.DISCORD_TOKEN}`, ...(!form && body !== undefined ? {'Content-Type': 'application/json'} : {})},
    body: body === undefined ? undefined : form ? body : JSON.stringify(body),
    signal: AbortSignal.timeout(5000)
  });
  if (!result.ok) throw new DiscordError(result.status);
  return result.status === 204 ? null : result.json();
}

export function message(env, channel, body) {
  return discord(env, `/channels/${channel}/messages`, 'POST', {allowed_mentions: {parse: []}, ...body});
}

export async function notify(env, user, content) {
  try {
    const channel = await discord(env, '/users/@me/channels', 'POST', {recipient_id: user});
    await message(env, channel.id, {content});
  } catch { /* DMs may be disabled. The ticket channel is authoritative. */ }
}

export async function verifySignature(request, raw, key, now = Date.now()) {
  const signature = request.headers.get('X-Signature-Ed25519') || '';
  const timestamp = request.headers.get('X-Signature-Timestamp') || '';
  if (!/^[0-9a-f]{128}$/i.test(signature) || !/^[0-9a-f]{64}$/i.test(key || '') ||
      !/^\d+$/.test(timestamp) || Math.abs(now / 1000 - Number(timestamp)) > 300) return false;
  const bytes = hex => Uint8Array.from(hex.match(/../g), pair => parseInt(pair, 16));
  try {
    const publicKey = await crypto.subtle.importKey('raw', bytes(key), {name: 'Ed25519'}, false, ['verify']);
    return await crypto.subtle.verify('Ed25519', publicKey, bytes(signature), new TextEncoder().encode(timestamp + raw));
  } catch { return false; }
}

export async function digest(value) {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256', new TextEncoder().encode(value)))].map(x => x.toString(16).padStart(2, '0')).join('');
}

export async function authorized(request, secret) {
  if (!secret || secret.length < 32) return false;
  const provided = request.headers.get('Authorization') || '';
  const a = await digest(provided), b = await digest(`Bearer ${secret}`);
  let difference = 0;
  for (let i = 0; i < a.length; i++) difference |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return difference === 0;
}
