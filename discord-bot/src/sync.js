/** Explicit memberships only. Inherited permissions never become Discord grants. */
export function rankMapping(raw) {
  const map = typeof raw === 'string' ? JSON.parse(raw) : raw;
  if (!map || Array.isArray(map) || typeof map !== 'object') throw new Error('Invalid RANK_ROLES');
  if (Object.keys(map).length > 8) throw new Error('At most eight rank mappings are supported');
  const seen = new Set();
  for (const [rank, role] of Object.entries(map)) {
    if (!/^[a-z][a-z0-9_]{0,31}$/.test(rank) || rank === 'default' || !/^\d{17,20}$/.test(role) || seen.has(role)) {
      throw new Error('RANK_ROLES must contain unique role IDs and non-default native rank IDs');
    }
    seen.add(role);
  }
  return map;
}

export const canonical = ranks => [...new Set(ranks)].sort();
export const same = (a, b) => JSON.stringify(canonical(a)) === JSON.stringify(canonical(b));

/** Minecraft wins a simultaneous change. First link also starts from Minecraft.
 * A durable pending target survives Discord failures and a dropped server response.
 */
export function reconcile(game, discord, previous, mapping) {
  const keys = Object.keys(mapping).sort();
  const filter = values => canonical(values.filter(r => keys.includes(r)));
  game = filter(game); discord = filter(discord);
  const signature = JSON.stringify(Object.entries(mapping).sort(([a], [b]) => a.localeCompare(b)));
  if (!previous || previous.signature !== signature) return {signature, game, target: game};
  if ((previous.pending || previous.discordApplied === false) && same(game, previous.game)) return {...previous, game};
  if (previous.discordApplied === false) return {signature, game, target: game};
  const baseline = previous.target;
  const target = keys.filter(rank => {
    const g = game.includes(rank), d = discord.includes(rank), b = baseline.includes(rank);
    return g !== b ? g : d;
  });
  return {signature, game, target};
}
