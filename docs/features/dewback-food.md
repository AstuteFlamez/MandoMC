# Dewback Leg and mob food balance

User requested the legacy roasted_nuna_leg texture renamed to Dewback Leg and dropped by Dewbacks. The custom item keeps a native cooked-beef base (steak consumption/saturation), restores eight food points (four hunger icons), and uses the recovered unchanged texture under mandomc:consumables/dewback_leg. A Dewback death drops one leg with default chance 1.0 in dewback-drops.yml; despawn cleanup does not call Mythic death loot.

Massiff Ribs keep their current identity, cooked-mutton base and drop chance. Their nutrition is explicitly four food points (two hunger icons). The optional food-nutrition item field is validated as an integer 1–20 and applied to the native FOOD component; native consumption and other food properties remain. Catalog build rejects missing native food components. The existing owned-item refresh also updates food metadata on old ribs without replacing stack quantities or other metadata.

Side conversation scope: source/assets prepared locally. No server restart, runtime config replacement, pack publication, commit or push was performed. Build passes 52 Java tests, including nutrition parsing/bounds; pack validation passes (211 files). Native consumption, drop-event and client appearance checks remain for coordinated deployment in the main conversation.

Next action: deploy the new plugin, item/drop configs and pack in the main conversation, then verify hunger restoration and a Dewback death in-game.

Main integration: rebuilt the combined workspace after the robe/icon/gray-pane fixes. All 52 Java tests and 1,921 focused live assertions pass: actual native consumption restores 8/4 hunger points and removes one item, old rib nutrition updates without losing quantity, 400 vendor death events per species retain the intended distribution, and one actual Dewback death creates exactly one collectible leg. The current 211-file pack already contains the verified Dewback texture alongside restored robe icons (SHA-1 47e92a5e8b5642a0dca25e6d234fb6d202e4c2a2); no replacement pack is needed.

Deployment completed at 17:37:52 on 2026-11-08. The normal server starts without errors, its JAR hash matches the verified combined build, and FoodProbe is removed. Robe inventory icons, worn textures and gray one-space menu panes are retained.
