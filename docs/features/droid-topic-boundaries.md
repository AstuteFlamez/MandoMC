# Feature: M4-ND subject boundaries
Status: implementation
Branch: bilal/droid-topic-boundaries

## Player experience
M4-ND remains a helpful MandoMC/Star Wars/Minecraft specialist with friendly droid banter. He redirects unrelated requests such as Walmart or UW-Madison definitions, without giving those facts. A player mentioning their university while asking where to build a MandoMC base still gets base advice. A Star Wars name absent from local keyword hints can still receive a lore answer. Ambiguous names request context. Both redirects and clarifications invite in-domain context, so an unfamiliar name never ends at a bare refusal. Follow-ups to supported topics keep working.

## Rules
All generated replies classify actual intent, rather than accepting a claimed domain or a matching keyword. MandoMC mechanics require verified facts. Lore and vanilla Minecraft knowledge never establish server-specific behavior. A mixed question gets only its supported portion. Real-world brands, universities, geography, current affairs, homework, programming and unrelated factual/advice subjects are outside scope. Normal greetings, thanks, farewells, encouragement and in-character jokes remain available. An unrelated premise disguised as Star Wars is still outside.

Redirects and clarifications use maintained player-facing messages. Their generated payload is discarded. Missing/unclassified/malformed scoped replies use the existing safe fallback. A refusal clears topic context so an unrelated subject cannot continue via pronouns. Refusals/clarifications refund the personal answer and cooldown, but actual shared tokens and global request spacing still apply. No new network requests or budgets are introduced.

## Ownership and integrations
GroqBrain owns semantic scope selection, requests the pinned model's strict JSON schema output, and independently validates the reply before returning display text. The model must support strict structured outputs; the current GPT-OSS 20B configuration does. See the [provider schema contract](https://console.groq.com/docs/structured-outputs). ProtocolDroid owns per-player delivery/context and quota accounting. GuideConversation selects retrieval hints only. Gameplay authorities and the exact-release knowledge workflow remain unchanged.

## Parameters
The paired protocol-droid configuration contains `conversation.redirect` and `conversation.clarify`, plus the specialist identity/menu wording. Normal restart activation applies. No topic keywords are added to a user-facing whitelist.

## Verification
Unit checks cover scope output validation, discarded outside payloads, missing server facts, maintained messages, refund/accounting persistence and topic context. The isolated native fixture exercises real Walmart/UW-Madison requests, fabricated-domain/instruction attempts, mixed requests, lore without keyword hints, lore follow-ups, Minecraft controls, humor, ambiguity and verified server statistics. A small maintained canon primer anchors Vader/Anakin training and Chewbacca identity without restricting the rest of Star Wars to enumerated names. References: [Obi-Wan Kenobi](https://www.starwars.com/databank/obi-wan-kenobi), [Anakin Skywalker](https://www.starwars.com/databank/anakin-skywalker), [Chewbacca](https://www.starwars.com/databank/chewbacca). Native results and development activation are pending.
