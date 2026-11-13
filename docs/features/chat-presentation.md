# Feature: Consistent chat presentation
Status: playable
Branch: bilal/chat-message-polish

## Player experience
Known server notices use one bold scoreboard-lettered ᴍᴀɴᴅᴏᴍᴄ » prefix, with the special » symbol independently dark gray, category color, gray bodies and meaningful highlights. Administrative brand/body are red and italic; the separator remains dark gray and nonitalic. Errors are red without italics. Categories distinguish combat, events, success, information, economy, social messages and routine status. Normal player chat uses the existing native rank badge plus display name » message, omitting the badge for default players. Literal player markup stays literal. All join and leave announcements reuse the same rank badge/color and Adventure display-name component as normal chat, with no badge for default players. Returning join/leave messages keep their native translations and renamed-player previous-name argument. System styling runs before inserting that component so rich nickname data and rank colors survive unchanged.

## Rules
Presentation does not change permissions, recipient sets, transaction outcomes or persisted currency. Signed player chat stays in Paper AsyncChatEvent and its existing native RankService owner; only the renderer changes. Suppressed/cancelled chat remains suppressed. Unknown third-party/system messages remain unchanged. Only audited owned system templates are styled. Click/hover actions, translation arguments and display names remain components. GUI/title/actionbar/item lore and territory palette are excluded. Three owned resource packs yield one terminal success/failure notice per join; pack IDs, URLs, hashes and required flags remain identical. Ammo warnings are coalesced for two seconds per player.

## Smallest playable version
Cover the current combat, administration, events, travel, recipes, clans, Jawa, auction, credits, crate, first-join, death and resource-pack messages. No gameplay changes are needed.

## Ownership and integrations
MessageService owns formatting and lifecycle notices. MessageCatalog loads explicit template sources and classifies known messages; unknown packets pass through. PacketEvents handles audited system-chat and owned pack prompts only. It never intercepts signed player-chat bodies. Explicit CommandMessages/CreditService send boundaries preserve context when templates are reused by interfaces. RankService retains its permission and membership authority. The deployment overlay injects two verified lifecycle calls into the actual integrated CommandBindings rather than replacing its existing clan/Ilum/decor ownership.

## Parameters
`language/chat-style.yml` enables the feature and supplies audited template sources, categories, private administration paths and lifecycle notice wording. Changes require restart.

## Verification
All-join/leave follow-up: 194 Java tests and 41 native Paper assertions pass for ranked/default first/rejoin/quit, rich name identity, suppressed notices, single icons, no direct extra sends, and unchanged signed-chat identity/recipients. Unit checks also retain native name hover data, prior names, surrounding text and literal markup. Only MessageService.class and PlayerChat.class change; first-join rewards remain separate and untouched. Live after one normal coordinated restart with no probe installed; exact two-class diff, current world/config/grant owners preserved, startup/read-only checks passed, Jabba restored and window released.

First-join name follow-up: 192 Java tests and 29 isolated native Paper assertions pass, including ranked/default components, exact rich-name data, literal placeholder-like nickname text, suppressed joins, returning-player detection without another welcome, no direct extra sends, and unchanged signed chat/recipients. The scoped follow-up replaces only MessageService.class and PlayerChat.class; deployed in its own coordinated window over the latest Jabba fix with an exact two-class diff. One clean normal restart, no installed probe, current chat owner startup and read-only preservation checks passed; Jabba restored and window released.

190 Java tests pass. The isolated native server passes 131 assertions including serialized pack fields, unknown/overlay exclusions, rich events, exact admin/private economy audiences, unchanged dollar amounts, actual crate full-inventory denial without spending either key, crate reward, in-place death and first-join handlers, coalesced pack results and actual AsyncChatEvent renderer preserving viewers, signed-message identity, nickname components, cancellation and literal markup. Native raw system messages require capture after a server task boundary; the initial fixture read was corrected without modifying crate gameplay. Production smoke uses protocol-only mode without synthetic actors.

## Pause note
Chat polish and adjusted scenery are live after the priority gray-Jawa repair. 57 live protocol checks and 109 live scenery checks passed; normal server restarted cleanly without probes, all seven gray rigs and pack/configs preserved, Jabba restored. Next action: inspect actual client chat and NPC looking.
