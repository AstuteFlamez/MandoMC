import {pathToFileURL} from 'node:url';
import {discord} from '../src/discord.js';
import {commands} from '../src/worker.js';

export async function registerCommands(env) {
  for (const key of ['DISCORD_APPLICATION_ID', 'DISCORD_GUILD_ID', 'DISCORD_TOKEN']) {
    if (!env[key]) throw new Error(`${key} is required in .dev.vars`);
  }
  const app = `/applications/${env.DISCORD_APPLICATION_ID}`;
  const registered = await discord(env, `${app}/guilds/${env.DISCORD_GUILD_ID}/commands`, 'PUT', commands);
  // The legacy application also installed a global slash command with the same name.
  const global = await discord(env, `${app}/commands`);
  const retired = global.find(command => command.name === 'baltop' && command.type === 1);
  if (retired) await discord(env, `${app}/commands/${retired.id}`, 'DELETE');
  return {registered: registered.length, retired: Boolean(retired)};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  const result = await registerCommands(process.env);
  console.log(`Registered ${result.registered} guild commands.`);
  if (result.retired) console.log('Removed the old global /baltop command.');
}
