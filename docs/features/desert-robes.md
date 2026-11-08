# Feature: Desert Robes and custom item administration
Status: implementation; client appearance check pending
Branch: bilal/desert-robes

## Player experience
Admins can browse custom items by category and issue them to themselves or another online player. The first armor set is the recovered Desert Robes: Hood, Tunic, Pants and Boots. Yellow names, Uncommon/Armor glyph badges and legacy gray descriptions are preserved, with the accepted blank line between badges and description.

## Rules
Robes retain ordinary leather armor protection, durability, dyeing, enchantment and stacking behavior. Stable IDs are robes_helmet, robes_chestplate, robes_leggings and robes_boots, stored as mandomc:item_id. Names/lore are presentation, never identity. Native equipment data is copied with only its rendering asset changed; ordinary leather textures are unaffected.

The admin permission mandomc.items.give defaults to operators. Click grants one; shift-click grants native maximum stack. Online recipients only, no inventory overflow drops, no preview transfers. Permissions are checked at command dispatch and each delivery. Disconnects invalidate the recipient. GUI navigation checks the still-open inventory before executing next tick. Reload rejects malformed content without replacing the working catalog.

## Smallest playable version
Four robes, E11 and Standard Plasma Cells in a paginated category browser. Legacy bantha-hide recipes and acquisition are deferred to supplies/progression. No unrelated legacy item families or WeaponMechanics demo guns are included.

## Ownership and integrations
MandoMC owns native item definitions, immutable catalog snapshots, PDC identity, presentation and administration. WeaponMechanics owns weapon/ammo generation; the public API adapter is isolated in integrations/weapons. Its allowlist in items.yml references E11 and Standard_Plasma_Cells. Missing vendor support affects those entries only.

Legacy review: the old factory already cloned cached stacks, but silently substituted stone for invalid materials, overwrote duplicate IDs, cleared the live registry before loading, split metadata across maps and coupled recipes to item creation. The replacement keeps cloned templates and stable IDs, validates content first, swaps one catalog snapshot, and leaves acquisition separate. The old give command also ignored full-inventory leftovers; delivery now checks capacity first.

## Parameters
Defaults live in plugin/src/main/resources/items.yml and items/robes.yml; deployed copies live under plugins/MandoMC. Files use schema-version: 1. Every items/*.yml file supplies an items mapping. Definitions require material, name, rarity, category, lore, custom-model-data and item-model; equipment-model is optional for wearable armor. Unknown fields, invalid materials/keys and duplicate IDs fail reload. Rarity/category badge names resolve through items.yml. Messages and GUI labels use messages.yml. `/mmc items reload` applies item changes; messages/code changes require restart. Existing inventory stacks are not rewritten.

Add a new native item by adding its definition, badge mappings if needed, and namespaced pack models/textures; it appears in its category automatically. External weapons/ammo go in the explicit external-items allowlist and retain vendor-generated metadata. Commands and permissions are documented in ../commands.md.

## Verification
The live Paper probe passed 80 assertions. Ten unit tests pass, including strict item-definition parsing and pagination boundaries. The pack builds with 45 files and validates equipment texture references. scripts/build-items-probe.sh builds a temporary probe for live Paper APIs, isolated simulated-player inventories and temporary config files. Never leave its JAR installed for ordinary play. Client texture, worn appearance and actual mouse interactions still require an in-game check.

## Pause note
Robes and admin browser implemented. Crafting remains deferred. Next action: use `/mmc give` in the client and inspect all four robes, worn textures and item spacing.

## Icon correction
The recovered 32px inventory icons are already colored. They use a white default dye tint and a single model layer; explicit leather dye colors still tint them. Removed the unrelated 16px leather icon overlays and vanilla chestplate overlay, which obscured the custom silhouettes. Worn equipment layers are unchanged and were confirmed good by the user. Revised inventory appearance needs a client check.

## Invisible GUI icon diagnosis
Only robes were affected; E11, ammo and vanilla items remained visible. Compared the exact installed 26.1.2 vanilla generated/leather models and client code before changing assets. GUI transforms match vanilla; PNGs and model references resolve. The available client log predates the latest reconnect, so it cannot rule out a later pack conflict.

Confirmed defect: robe dye defaults were RGB-only integers (initially 10511680, then 16777215). In this client, Dye.calculate calls DyedItemColor.getOrDefault, which returns an undyed default unchanged; only explicit DYED_COLOR values are forced opaque. ExtraCodecs.RGB_COLOR_CODEC preserves integer input. Thus 16777215 becomes 0x00FFFFFF, with zero alpha. Vanilla leather uses -6265536 (0xFFA06540). The correct untinted opaque default is -1 (0xFFFFFFFF). Removing untinted overlays exposed the entirely transparent base. GUI item rendering retains alpha; held visibility therefore does not establish a valid GUI icon.

Correction changes only four inventory tint defaults to -1. Models, PNGs, native dye behavior and worn armor are unchanged. The pack builder now rejects nonopaque integer dye defaults, with regression tests for both previous invalid values and valid vanilla/white values. Explicit RGB-vector defaults and equipment color_when_undyed remain separate formats. Client hotbar confirmation is still required.

References: https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1 documents the separate item shader and UI lightmap; exact tint behavior was verified in the installed 26.1.2 client classes rather than inferred from older pack guides.

## Keep original inventory icons (2026-11-08)
The user accepted the replacement worn/entity robes but requested the original inventory icons. All four inventory PNGs are restored byte-for-byte from `4b41b45`. The accepted worn textures, equipment layers, opaque inventory dye defaults and item models are unchanged. Replacement artwork/crop metadata now covers worn armor only.
