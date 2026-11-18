import {commands} from '../src/worker.js';

for (const key of ['DISCORD_APPLICATION_ID', 'DISCORD_GUILD_ID', 'DISCORD_TOKEN']) {
  if (!process.env[key]) throw new Error(`${key} is required in .dev.vars`);
}
const response = await fetch(`https://discord.com/api/v10/applications/${process.env.DISCORD_APPLICATION_ID}/guilds/${process.env.DISCORD_GUILD_ID}/commands`, {
  method: 'PUT', headers: {'Content-Type': 'application/json', Authorization: `Bot ${process.env.DISCORD_TOKEN}`},
  body: JSON.stringify(commands)
});
if (!response.ok) throw new Error(`Command registration failed (${response.status})`);
console.log(`Registered ${(await response.json()).length} guild commands. Existing global commands are untouched.`);
