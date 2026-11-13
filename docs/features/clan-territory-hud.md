# Feature: Clan territory HUD and entry titles
Status: deployed, including combat row removal

The normal fourteen-row sidebar combines kills and deaths as `Kills/Deaths: 7/3`. Territory occupies the previous deaths row. Combat uses thirteen rows, removes Territory completely, and preserves both warnings and the countdown. Normal fourteen-row layout returns after combat. Surplus score entries are removed, and the backing layout stays current while hidden; scoreboard and objective identity remain stable. Native values update on the regular render cycle, including movement and stationary claim changes.

TerritoryService is the single presentation owner for zone/clan identity, entry titles and sidebar value colors. Wilderness is gray, Safezone gold/orange and Warzone the established dark red. Own clan claims are green, other clans blue; a defender claim is red only to the recorded active attacking clan while its actual raid remains unexpired. Eligibility alone, unrelated third parties and the attacker's home seen by defenders do not turn red. Own land stays green. Clan names are inserted as plain component placeholders.

Entry titles preserve existing zone wording/colors. Claim titles show the owner name. Comparing zone, stable owner ID and viewer relationship suppresses repeated titles across contiguous chunks of the same owner. Existing movement/teleport hooks and a ten-tick online-player scan cover membership, ownership and raid expiry without movement or spam. The sidebar uses the same identity and formatting; its value is a component so an outer mint-colored template cannot override the actual territory color.

The user selected `⚑` U+2691 for the far-left territory icon. The installed 26.1.2 asset index 30 unifont archive contains a nonempty 8×16 flag glyph. The exact served pack was verified locally against SHA-1 a36f248a8906745be5c70db70bcba441028e64f2; it neither overrides that codepoint nor the unifont provider. No pack update is needed. Labels retain the small-caps style.

217 Java tests pass. The combat fix uses a four-entry scoped overlay (PlayerHud, its View, HudSettings and bundled HUD default), plus removal of the one runtime combat Territory row. Public APIs and all unrelated live JAR entries are verified. 288 disposable native assertions cover manual clear, natural expiry, re-tagging, hidden/revealed scoreboards, world transition and respawn, alongside existing colors/claims/raids/NPC compatibility.

Deployed after native validation with a stopped full backup and clean normal startup. All 1,434 frozen originals and unrelated release entries remain unchanged; active Jabba restored and restart window released. Next action: ordinary in-game combat/sidebar playtest.
