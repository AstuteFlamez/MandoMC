# Feature: World leaderboards
Status: deployed to local development with automatic restart on 2026-11-13; client visual confirmation pending.
Branch: bilal/leaderboard-style.

## Player experience
Kills, balance and Ilum boards use the reference layout: bold uppercase title, TOP TEN PLAYERS,
one blank spacer, then ten numbered positions. Missing entries contain only the rank; no fake
players or zero values fill them. Kills is red, balance green and Ilum purple. Populated rows use
`rank. name: value`; balance prefixes the existing resource-pack credit glyph. No border or
background is added. Shadowed text stays upright and turns horizontally toward each viewer.

The saved locations remain the ground anchors. A configurable 0.35-block display offset lifts
the bottom row clear of the ground. The thirteen-line panel retains the same height whether
zero, two or ten players have records, keeping its center around viewing height instead of
collapsing toward the floor. Existing coordinates and ranking data are preserved.

## Rules and ownership
`WorldLeaderboards` reads native PLAYER_KILLS statistics and the committed CreditLedger top ten.
ParkourService remains the sole Ilum record owner. LeaderboardLayout only composes the fixed
presentation; it does not write rankings, scores, balances, rewards or locations.

World displays refresh every ten seconds and one tick after a player death. Refresh retains
entity identity; a missing owned display is recreated at the raised anchor. Shutdown removes
only owned nonpersistent displays, listeners, tasks and chunk tickets. Ilum's chat `/parkour top`
output and its course hints/NPCs are unchanged.

## Parameters
`leaderboards/world.yml` controls saved worlds/locations, display-height-offset, and per-board
message templates. `parkour/ilum.yml` controls leaderboard-height-offset and its leaderboard
message templates. Defaults and production overrides match. Apply with a normal restart.
Both TextDisplay background controls remain explicit: default background false and ARGB zero.

## Verification
The shared-layout regression covers empty, partial, full and over-capacity input, checking ten
positions and stable header/spacer placement. The isolated Paper probe checks all three real
boards, transparency, height, upright tracking, refresh identity and missing-display recreation.
In-client visual confirmation after the manual production restart remains the next action.
