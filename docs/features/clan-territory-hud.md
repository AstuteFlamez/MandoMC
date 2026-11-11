# Feature: Clan territory HUD and entry titles
Status: combined stats/territory/titles deployed; selected flag and value-color polish verified for rollout

The normal fourteen-row sidebar combines kills and deaths as `Kills/Deaths: 7/3`. Territory occupies the previous deaths row; combat keeps its existing warnings/countdown and uses its former spacer for territory. Score keys and the objective remain stable. Native values update on the regular render cycle, including movement and stationary claim changes.

TerritoryService is the single presentation owner for zone/clan identity, entry titles and sidebar value colors. Wilderness is gray, Safezone gold/orange and Warzone the established dark red. Own clan claims are green, other clans blue; a defender claim is red only to the recorded active attacking clan while its actual raid remains unexpired. Eligibility alone, unrelated third parties and the attacker's home seen by defenders do not turn red. Own land stays green. Clan names are inserted as plain component placeholders.

Entry titles preserve existing zone wording/colors. Claim titles show the owner name. Comparing zone, stable owner ID and viewer relationship suppresses repeated titles across contiguous chunks of the same owner. Existing movement/teleport hooks and a ten-tick online-player scan cover membership, ownership and raid expiry without movement or spam. The sidebar uses the same identity and formatting; its value is a component so an outer mint-colored template cannot override the actual territory color.

The user selected `⚑` U+2691 for the far-left territory icon. The installed 26.1.2 asset index 30 unifont archive contains a nonempty 8×16 flag glyph. The exact served pack was verified locally against SHA-1 a36f248a8906745be5c70db70bcba441028e64f2; it neither overrides that codepoint nor the unifont provider. No pack update is needed. Labels retain the small-caps style.

202 Java tests pass. The initial live slice passed 118 disposable native sidebar/claim/raid/combat assertions; the icon/color polish passes 124, including actual component colors for gray Wilderness, gold Safezone, dark-red Warzone and green/blue/red clan branches. Scoreboard identity, fourteen distinct row keys, row order, combat warnings/countdown and return to stats are checked. The polish overlay owns only PlayerHud, TerritoryService and the bundled HUD default; all frozen snapshot/claim/restoration classes and every unrelated release byte remain unchanged. Runtime updates change only the two territory-row icons in interface/hud.yml.

Next action: obtain the coordinated post-ore restart window, apply the scoped polish while preserving the active frozen capture journal, and verify normal startup/resume.
