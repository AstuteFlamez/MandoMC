# Feature: Discord account linking and ranks
Status: C0-M5 implementation; live activation on both servers in progress
Branch: bilal/discord-bot-sync

## Player experience
C0-M5 is the silver protocol droid near M4-ND on Tatooine and the existing Discord bot. Right-click C0-M5 or run `/discord` in Minecraft and use
the private code with `/link code:<code>` in the MandoMC Discord server. Successful
linking associates one Minecraft account with one Discord account. `/link` checks
whether the account is already linked. Join at https://discord.gg/bruuZCG5Pk. A verified link rewards 5,000 credits once per Minecraft UUID on production after that account has joined there. CreditLedger saves the balance and reward receipt atomically; retries, relinking checks and restarts cannot pay twice. Development does not pay the production reward.

The first successful link sets the member's Discord server nickname to their verified Minecraft name. Discord `/players` shows the latest online count. Use `/leaderboard board:<board>` to check the native top ten for Credits, Player Kills or Ilum Time. Both commands default to production, offer a development server option, and require a fresh server snapshot. Players
open support tickets with `/ticket` or a ticket panel, then talk to staff in a
private ticket channel. Channel names use short durable numbers, such as `support-001` or `bug-report-002`, instead of long account identifiers. The production Minecraft-hosted Gateway companion relays ordinary ticket DMs, assigns the default Mando Citizen role on joins and displays Online while production runs. Cloudflare interactions can keep working while the Gateway appears Offline.

## Rules
New codes use eight easy-to-read characters. Click the code in Minecraft chat to copy it, or click Copy command for the complete Discord `/link` command. The Discord-blue invite opens https://discord.gg/bruuZCG5Pk. Codes expire after ten minutes and work once. Requesting another code invalidates
the previous code; requests are limited to one per thirty seconds. A Minecraft UUID
and Discord account can each have only one link. Account names alone cannot link
accounts. Staff handle deliberate unlinking or migration of existing verified links.

Staff explicitly select which native ranks correspond to which Discord roles. A
change on either side grants or removes only its mapped membership. Unmapped ranks,
other Discord roles and operator status are preserved. Rank inheritance continues
to work normally without creating extra Discord roles. Production Minecraft is the initial baseline. Both Minecraft servers share the same durable target, with separate versioned acknowledgments so delayed replies do not undo changes from the other server. Rank sync includes offline Minecraft
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
intent. Its separate optional Paper Gateway plugin adds member-join, DM and presence events without a separate hosting bill; only production runs it.
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

## NPC presentation
C0-M5 uses a pinned signed silver TC-14 skin and an avatar rendered from the same head pixels. It chooses clear supported ground five to ten blocks from M4-ND, preferring a six-block offset and never carving terrain. Both droid titles have transparent backgrounds and retain text shadows. M4-ND provides exact local answers about joining, linking, rewards, rank sync and tickets.

## Activation
The existing Discord application, token, ticket panel controls and webhook destinations are retained. Cloudflare Worker/D1 is provisioned on the free plan. Both server bridges use distinct private credentials; only production runs the Gateway and pays link rewards. A real player must prove account ownership through the normal private code flow.
