# Original Pawn Shop sell GUI

Status: locally staged for visual review; not published or deployed. The user's final direction supersedes the earlier wide/four-row proposals: remove the extra outer input columns, keep essentially the original artwork, and choose matching icons from the supplied artwork to replace visible dye/barrier items.

## Player-facing result

The original 256×256 Pawn Shop background is preserved pixel-for-pixel except for two added 18×18 controls. Its original seven-column, three-row selling area has 21 input cells. The roof, sign, framed selling slots, centered gold “i,” lower inventory art and hotbar are unchanged. The checkmark confirms the existing quoted sale; the X returns items and closes. Hovering the gold “i” shows the existing selling instructions. No guide lines or vanilla item icons appear in the review rendering.

The checkmark and X are the supplied pack's original beacon `confirm.png` and `cancel.png`, not newly painted substitutes. Their original dimensions and palette are retained. They are placed symmetrically around the gold info symbol, centered on their actual interactive cells. All three server-side control items use an empty item model, retaining their real hover tooltips and click handlers while the background supplies the artwork.

## Recovered workflow and sources

The pinned MandoPack archive SHA-256 is `32a578caac7c4c8976868abf0aa20f545c9926eb8d6382b94ccf5e986c95cce5`. Its `assets/minecraft/textures/custom/guis/pawnshop.png` is 256×256. Its font maps Į to that image with height256/ascent48, with U+F001 advance−256 and U+F002 advance+1.

The legacy `ShopSellGUI.java` on `bilal/legacy-main-2026-11-05` used 54 container slots, 21 inputs (10–16, 19–25, 28–34), and an invisible FLINT/CMD5 info item at49 over the baked gold icon. `ShopGUI.SHOP_TITLE` has208 +1 advances after−256 (net−48); `pawnTitle()` removes11, yielding−59. The modern adaptation corrects that eleven-pixel horizontal misalignment rather than rescaling the art.

Only needed reference assets are read privately from the user-provided Dark Sci-Fi GUI archive, using fixed safe archive member names. Its vendor readme credits GEASTRE/SlimeStudio. The archive and raw commercial asset collection are not committed or published wholesale. The report records the archive SHA-256 and both selected icon paths/hashes.

## Geometry

The installed26.1.2 `ContainerScreen`, `AbstractContainerScreen`, `ChestMenu` and bitmap glyph implementations supply the measurements. A six-row chest has a176×222 GUI canvas. Its title anchor is(8,6). Input interiors are at(26+18c,36+18r), for c0–6/r0–2, exactly matching the original top slot artwork at(66+18c,71+18r) under origin(−40,−35).

The namespaced font uses a single−48 advance and bitmap height256/ascent48, preserving one art pixel per GUI unit. Native GUI scaling handles framebuffer pixels; artwork is never globally resampled. Confirm is raw slot46, info49, return52, all on native y108; their centers are54 GUI units apart. The new18px icon canvases start at source(65,142) and(173,142); their visible pixels fit their interactive areas. Player inventory/hotbar artwork remains the original, without replacement tiles.

## Implementation and validation

`scripts/stage-sell-gui.py` builds private candidates from explicit baseline JAR/config/pack, current owner Java source, classpath, pinned legacy archive and supplied base-art archive. It compiles only the reviewed current `JawaSellInventory.java` adaptation against runtime APIs, and writes a readable source diff. It fails if the expected source patterns have changed. The changed source covers layout constants, input-slot mapping, control positions, title and invisible control presentation. The existing custody, credit escrow, quote, return and recovery implementation remains authoritative; all indexed operations use the centered input mapping.

The builder asserts every modified art pixel lies inside one of the two added icon canvases. It verifies all unrelated JAR entries and every pre-existing pack entry remain byte-identical. The three additive pack files are `assets/mandomc/font/gui/sell.json`, `assets/mandomc/textures/gui/sell.png` and `assets/mandomc/items/gui/control.json`. No default-font, credit glyph, skull, unrelated texture, chat message, item lore, price or NPC-location data is replaced. The only config addition is `text.buyer-gui-title`.

The final disposable Paper probe passed136 assertions: all21 inputs accept full stacks; a22nd stack stays in player inventory; closing conserves all22 stacks without payment; every removed/decorative cell is protected without consuming the cursor; accepted/rejected items, proportional payouts and duplicate-click protection work; shift/split/drag preserve exact quantities; the themed checkmark/X invoke sale/return; full-inventory returns remain saved; customized item metadata and saved tray/cursor recovery remain safe and idempotent.

The staged baseline is JAR`cd39f3f872a64327f179a5c359799205084efc7eec075d41c2445d5b55b408d8`, pack`d38d5b8f37145f235a2d473454df54b2d6f01e00cd49402506cf8f7f7dfa8a09`. These are testing inputs, not an instruction to overwrite newer runtime state. Rebase on the actual latest JAR/config/pack and coordinate the serialized restart window before any eventual rollout. The preview is a pixel layout rendering, not an in-game screenshot; actual client presentation remains to be verified before calling the visual complete.

Next action: review the original-art version with the matching checkmark and X before publication.
