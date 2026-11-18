import {DatabaseSync} from 'node:sqlite';
import {readFileSync, readdirSync} from 'node:fs';

export function database() {
  const sqlite = new DatabaseSync(':memory:');
  for (const file of readdirSync(new URL('../migrations/', import.meta.url)).filter(name => name.endsWith('.sql')).sort())
    sqlite.exec(readFileSync(new URL('../migrations/' + file, import.meta.url), 'utf8'));
  const wrap = (sql, params = []) => ({
    bind: (...values) => wrap(sql, values),
    first: async () => sqlite.prepare(sql).get(...params) || null,
    all: async () => ({results: sqlite.prepare(sql).all(...params)}),
    run: async () => { const result = sqlite.prepare(sql).run(...params); return {meta: {changes: result.changes}}; }
  });
  return {sqlite, prepare: sql => wrap(sql), batch: async statements => {
    sqlite.exec('BEGIN');
    try { const results = []; for (const statement of statements) results.push(await statement.run()); sqlite.exec('COMMIT'); return results; }
    catch (error) { sqlite.exec('ROLLBACK'); throw error; }
  }};
}

export const ids = {guild: '111111111111111111', user: '222222222222222222', role: '333333333333333333',
  linked: '444444444444444444', app: '555555555555555555', category: '666666666666666666',
  channel: '777777777777777777', staff: '888888888888888888', log: '999999999999999999'};
export const uuid = '12345678-1234-1234-1234-123456789012';
export function environment() {
  return {DB: database(), DISCORD_TOKEN: 'test-only', DISCORD_GUILD_ID: ids.guild, DISCORD_APPLICATION_ID: ids.app,
    RANK_ROLES: JSON.stringify({warrior: ids.role}), LINKED_ROLE_ID: ids.linked, CITIZEN_ROLE_ID: '',
    BRIDGE_SECRET: 'a'.repeat(64), DEV_BRIDGE_SECRET: 'd'.repeat(64), GATEWAY_SECRET: 'b'.repeat(64),
    TICKET_CATEGORY_ID: ids.category, TICKET_LOG_CHANNEL_ID: ids.log, STAFF_ROLE_IDS: JSON.stringify([ids.staff])};
}
