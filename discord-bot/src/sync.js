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

/** One shared desired rank set, with independently acknowledged server snapshots.
 * Production establishes the initial baseline; neither a new dev connection nor
 * a delayed acknowledgment can replace a newer target from the other server.
 */
export function reconcileShared(game, discord, previous, mapping, server, ack = 0, ackSignature = '') {
  const keys = Object.keys(mapping).sort();
  const filter = values => canonical(values.filter(rank => keys.includes(rank)));
  game = filter(game); discord = filter(discord);
  const signature = JSON.stringify(Object.entries(mapping).sort(([a], [b]) => a.localeCompare(b)));
  let state;
  if (!previous || previous.signature !== signature) {
    if (server !== 'production') return null;
    state = {signature, revision: (previous?.revision || 0) + 1, target: game,
      discordBaseline: game, discordApplied: false, sources: {}};
  } else if (!previous.sources) {
    state = {signature, revision: 1, target: previous.target, discordBaseline: previous.target,
      discordApplied: previous.discordApplied !== false,
      sources: {production: {game: previous.game, pending: previous.pending
        ? {revision: 1, target: previous.target, expected: previous.game} : null}}};
  } else state = structuredClone(previous);
  const source = state.sources[server];
  let baseline = source?.game;
  if (source?.pending) {
    const pending = source.pending;
    if (ackSignature === signature && ack === pending.revision) baseline = pending.target;
    else if (same(game, pending.target)) baseline = pending.target;
    else if (same(game, pending.expected)) baseline = game; // Unapplied command: replay current shared target.
    // A genuinely new local edit remains a change relative to the last observed game set.
  }
  const target = keys.filter(rank => {
    if (baseline && game.includes(rank) !== baseline.includes(rank)) return game.includes(rank);
    if (state.discordApplied && discord.includes(rank) !== state.discordBaseline.includes(rank)) return discord.includes(rank);
    return state.target.includes(rank);
  });
  if (!same(target, state.target)) state.revision++;
  state.target = target;
  state.sources[server] = {game, pending: same(game, target) ? null
    : {revision: state.revision, target, expected: game}};
  return state;
}
