# Feature: Consistent chat presentation
Status: implementation
Branch: bilal/chat-message-polish

## Player experience
Known server notices use one MandoMC » prefix, category color, gray bodies and meaningful highlights. Administrative feedback is entirely red and italic; errors are red without italics. Categories distinguish combat, events, success, information, economy, social messages and routine status. Normal player chat uses the existing native rank badge plus display name » message, omitting the badge for default players. Literal player markup stays literal.

## Rules
Presentation does not change permissions, recipient sets, transaction outcomes or persisted currency. Signed player chat stays in Paper AsyncChatEvent and its existing native RankService owner; only the renderer changes. Suppressed/cancelled chat remains suppressed. Unknown third-party/system messages remain unchanged. Only audited owned system templates are styled. Click/hover actions, translation arguments and display names remain components. GUI/title/actionbar/item lore and territory palette are excluded. Three owned resource packs yield one terminal success/failure notice per join; pack IDs, URLs, hashes and required flags remain identical. Ammo warnings are coalesced for two seconds per player.

## Smallest playable version
Cover the current combat, administration, events, travel, recipes, clans, Jawa, auction, credits, crate, first-join, death and resource-pack messages. No gameplay changes are needed.

## Ownership and integrations
MessageService owns formatting and lifecycle notices. MessageCatalog loads explicit template sources and classifies known messages; unknown packets pass through. PacketEvents handles audited system-chat and owned pack prompts only. It never intercepts signed player-chat bodies. Explicit CommandMessages/CreditService send boundaries preserve context when templates are reused by interfaces. RankService retains its permission and membership authority. The deployment overlay injects two verified lifecycle calls into the actual integrated CommandBindings rather than replacing its existing clan/Ilum/decor ownership.

## Parameters
`language/chat-style.yml` enables the feature and supplies audited template sources, categories, private administration paths and lifecycle notice wording. Changes require restart.

## Verification
190 Java tests pass. The isolated native server passes 131 assertions including serialized pack fields, unknown/overlay exclusions, rich events, exact admin/private economy audiences, unchanged dollar amounts, actual crate full-inventory denial without spending either key, crate reward, in-place death and first-join handlers, coalesced pack results and actual AsyncChatEvent renderer preserving viewers, signed-message identity, nickname components, cancellation and literal markup. Native raw system messages require capture after a server task boundary; the initial fixture read was corrected without modifying crate gameplay. Production smoke uses protocol-only mode without synthetic actors.

## Pause note
Source and native checks are ready; no live chat update yet. Priority gray-Jawa repair owns the next rollout. Next action: coordinate a scoped chat rollout against the latest deployed baseline after that repair.
