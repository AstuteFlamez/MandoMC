# Feature: Discord account linking and ranks
Status: implementation; optional integration disabled until configured
Branch: bilal/discord-bot-sync

## Player experience
When Discord linking is enabled, a player runs `/discord` in Minecraft and uses
the private code with `/link code:<code>` in the MandoMC Discord server. Successful
linking associates one Minecraft account with one Discord account. `/link` checks
whether the account is already linked. There is no credit cost or item reward.

Discord `/players` shows the latest online count; `/baltop` shows the native top
ten balances. Both become unavailable when the server's snapshot is stale. Players
open support tickets with `/ticket` or a ticket panel, then talk to staff in a
private ticket channel. An optional companion also relays ordinary ticket DMs.

## Rules
Codes expire after ten minutes and work once. Requesting another code invalidates
the previous code; requests are limited to one per thirty seconds. A Minecraft UUID
and Discord account can each have only one link. Account names alone cannot link
accounts. Staff handle deliberate unlinking or migration of existing verified links.

Staff explicitly select which native ranks correspond to which Discord roles. A
change on either side grants or removes only its mapped membership. Unmapped ranks,
other Discord roles and operator status are preserved. Rank inheritance continues
to work normally without creating extra Discord roles. Minecraft is the initial
baseline and resolves concurrent changes. Rank sync includes offline Minecraft
accounts. A Discord member leaving the guild does not revoke Minecraft ranks.

Links, ticket ownership and pending rank updates survive restarts. Network failures
retry without replacing existing Minecraft ranks. Synchronization is periodic rather
than instantaneous, and can take longer for larger numbers of linked accounts.

## Smallest playable version
Link an account, grant and remove one mapped rank from Minecraft, then grant and
remove its role in Discord. Both sides converge while an unrelated role and native
rank remain intact. Open a ticket, have staff claim and close it, and verify its
transcript is saved before the channel is deleted.

## Ownership and integrations
MandoMC's RankService remains the only writer of native rank memberships and
permissions; CreditLedger remains the only balance authority. Discord stores guild
roles and messages. The Cloudflare service stores verified links and reconciliation
intent. Its optional Gateway companion adds member-join, DM and presence events.
Existing ticket controls and recognized legacy ticket channels can be reused.

## Parameters
Integration enablement defaults to off. The server update interval defaults to
thirty seconds, with a supported range of thirty seconds to one hour. Up to ten
linked accounts rotate per cycle. Staff configure the same explicit mapping at
both ends and restart the server to apply configuration changes.

## Verification
Automated tests cover proof expiry/uniqueness, authenticated requests, both rank
directions, failed and interrupted updates, permission boundaries, legacy ticket
controls and transcript retention. Live permissions and client behavior require
verification with the configured Discord application before activation.

## Pause note
Source implementation is available. Live enablement awaits Cloudflare and Discord
configuration and a real linked-account check. Next action: configure the Worker
and test one non-staff rank mapping before production activation.
