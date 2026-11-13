# Feature: World leaderboards
Status: deployed and native-verified on 2026-11-11.
Branch: bilal/legacy-ilum-crates.

## Player experience
Two transparent, shadowed text displays show the top ten players by kills and credit balance at the
user's saved Batuu locations. Aqua headings, gray ranks and values match the Ilum leaderboard style.
They face each viewer horizontally and remain upright. The exact captured XYZ coordinates are used
with zero display-height offset; captured camera pitch is not applied to either board.

## Rules and ownership
`WorldLeaderboards` is a read-only view: native `Statistic.PLAYER_KILLS` supplies kills for online and
offline players, while the existing `CreditLedger.top(10)` supplies committed integer-cent balances
and its existing tie order. Native statistics and credit accounts remain the only authorities. No
new counters, migration, reset, reward or gameplay/protection change is introduced.

Displays refresh every ten seconds, and one tick after a player death so native kill statistics have
updated. Refresh preserves entity identity; a missing owned display is recreated at its configured
anchor. Each feature startup registers one typed lifecycle owner. Repeated starts are ignored, and
shutdown removes only this owner's tagged, nonpersistent displays, listener and scheduled work.

## Parameters
`leaderboards/world.yml` schema 1 controls enabled state, the two named board worlds/coordinates,
and message templates. These settings take effect on an ordinary restart. Both Bukkit TextDisplay
background controls are explicit: default background false and ARGB zero on spawn and refresh.
The existing Ilum owner uses the same explicit controls without changing any other display property.

## Verification and pause note
The existing Java suite passes 183 tests. Narrow native Paper probes verify Ilum existing/new
background behavior and the new boards' offline rankings, updates, exact anchors, transparency,
duplicate ownership, missing-display recovery, lifecycle cleanup and restart behavior.
183 Java tests, 21 native Ilum assertions, 52 initial board assertions and 41 restart assertions pass.
After direct user approval, one ordinary restart applied the scoped overlay; 39 read-only live
assertions pass. All unrelated JAR entries, existing configs, native data, port and resource pack are
preserved. The actual idle event state and next Jabba's Palace were restored, with no verification
plugin installed. Next action: review the exact-anchor placement in the Minecraft client.
