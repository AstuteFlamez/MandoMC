# Legacy sell GUI visual migration

Status: locally staged for human visual review. No GUI pack publication or shared-server write/restart has occurred. The current 36-slot Jawa menu remains the layout owner: inputs 0–26, info 27, sell 31, return/close 35. Selling still requires the existing confirmation button; closing returns items. The info book's hover tooltip explains those rules.

## Recovered sources

The recorded MandoPack archive SHA-256 is `32a578caac7c4c8976868abf0aa20f545c9926eb8d6382b94ccf5e986c95cce5` at the existing pinned commit. Its `assets/minecraft/textures/custom/guis/pawnshop.png` is 256×256. `assets/minecraft/font/default.json` maps Į to that bitmap with height 256 and ascent 48, plus U+F001 advance −256 and U+F002 advance +1.

On `bilal/legacy-main-2026-11-05`, `src/main/java/net/mandomc/server/shop/gui/ShopSellGUI.java` used 54 container slots, 21 input slots (10–16, 19–25, 28–34), info slot 49 and `pawnTitle()`. `ShopGUI.SHOP_TITLE` has 208 +1 characters after −256 (net −48). `pawnTitle()` removes eleven +1 characters and changes the final glyph to Į, giving net −59. The old info item is FLINT with custom-model value 5; its model is blank, while the gold info symbol is baked into the background. The current info book and explicit sell/return buttons remain interactive items.

The supplied Dark Sci-Fi GUI archive contains matching themed vanilla GUI/container/sprite assets and ItemsAdder/Nexo configurations; no separate custom pawnshop/sell panel or button art is present. Only the generic chest texture and vendor readme were extracted privately for reference, with path-traversal checks. The readme credits GEASTRE and SlimeStudio. No complete commercial archive or raw reference payload is added to Git or scheduled for publication. No additional user asset is needed for this sell-only adaptation.

## Current-client measurements

Measured from the installed 26.1.2 client classes `ContainerScreen`, `AbstractContainerScreen`, `ChestMenu` and bitmap-font glyphs:

- Four chest rows: 176×186 GUI units; title anchor (8,6).
- Input slot interiors: (8 + 18c, 18 + 18r), for columns 0–8 and rows 0–2.
- Control-row interiors: y=72, at x=8,80,152 for info/sell/return.
- Player inventory: first row y=103, then 121 and 139; hotbar y=161; same 18-unit column pitch.
- One texture pixel maps to one GUI unit. A GUI scale of four maps it to four framebuffer pixels; the source art is never globally resampled.

The proposal uses a 256×238 bitmap, declared height 238/ascent 66, and a single −48 space advance. With the client's bitmap bearing, its origin relative to the menu is (−40,−53). The artwork's input interiors at x=48+18c,y=71+18r therefore match the unchanged native inputs exactly. The legacy pawn's −59 advance would offset those hitboxes eleven pixels to the left.

The adaptation repeats the existing 18-pixel tray tiles into two extra columns, removes one unused interior row, and relocates original slot tiles to the exact lower-inventory/hotbar positions. The original roof, Pawn Shop sign, decorative outer frame and palette remain. The old baked center info symbol is removed because the actual info/sell/return items are now authoritative. The annotated comparison is a layout rendering with native item icons, not an in-game screenshot. Actual client rendering, including roof visibility at different GUI scales/window sizes, remains to be reviewed before calling the visual complete.

## Implementation and safety

`scripts/stage-sell-gui.py` produces private candidate outputs from explicit baseline JAR/config/pack and pinned legacy inputs. It never publishes or changes a running server. `scripts/PatchSellGui.java` edits only the sell-service `open` title key and the Tray constructor's decorative pane-fill call. Every unrelated method's bytecode is asserted identical. In particular, custody journaling, cursor handling, accepted/rejected items, payouts, full-inventory returns, disconnect and saved-custody recovery are unchanged.

The separate `mandomc:gui/sell` font adds two new pack files: its provider JSON and the sell texture. It does not replace `minecraft/font/default.json`, credit providers, the Rancor Skull, or any existing asset. The config merge adds only `text.buyer-gui-title`; current chat, item lore, offers, prices and all NPC locations stay intact. Empty decoration cells remain protected by the existing event rules; their glass-pane items are removed so they cannot obscure the artwork.

The final GUI-only native probe passed 83 assertions on disposable Paper: actual opened inventory/title, 27 input cells, info tooltip, protected empty control cells/cursor conservation, mixed accepted/rejected items, proportional payouts, duplicate-click protection, shift/split/drag behavior, full-inventory saved returns, metadata-safe recovery and idempotence. An earlier broader probe also passed 89 assertions. The tracked staging tool reproduces the tested JAR entries and image exactly. All pre-existing pack files are byte-identical to baseline.

Staged against JAR `cd39f3f872a64327f179a5c359799205084efc7eec075d41c2445d5b55b408d8` and pack `d38d5b8f37145f235a2d473454df54b2d6f01e00cd49402506cf8f7f7dfa8a09`. Jawa owner confirmed source `69079ac` has no in-flight sell-service edits. Any eventual publication must rebase on the actual latest pack, including separately staged credit changes, and any server overlay must rebase/CAS against the actual latest JAR/config after the decoration owner's restart window is released.

Next action: review the revised four-row artwork before publishing this GUI.
