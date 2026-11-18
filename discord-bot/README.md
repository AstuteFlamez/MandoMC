# MandoMC Discord bot

Recreates the behavior of [AstuteFlamez/MandoBot](https://github.com/AstuteFlamez/MandoBot)
at commit `1bb68267e3dbca77e45b3cf303530e2ae5167bb2`, inside MandoMC. The source is rewritten
for Cloudflare Workers/D1 with an optional Python Gateway companion.

## What runs where

The Worker serves `/link`, `/baltop`, `/players`, `/ticket`, `/ticket-panel`, ticket
select/claim/close controls and the authenticated Minecraft bridge. Tickets are private
Discord channels that their owner and configured staff can use directly. Links,
single-use code hashes, sync intent and ticket ownership survive restarts in D1.
Closing a ticket exports actual channel history to the log channel before deleting it.
The bot never writes balances: `/baltop` reads the native server's top-ten snapshot.

The optional `gateway/companion.py` restores ordinary DM-to-staff ticket relay,
Mando Citizen assignment when members join, and the live Watching presence from the
old bot. It requires an always-running Python process. It uses the same Discord
application, does not register commands, and uses the Worker for durable ticket identity.
Without it, all Worker commands, channel tickets and both directions of rank sync work.

## Free hosting

Cloudflare Workers Free currently includes 100,000 requests/day and 10 ms CPU/request.
D1 Free includes 5 million rows read/day, 100,000 rows written/day and 5 GB storage.
These are usage limits, not a promise of unlimited service or a paid uptime SLA.
HTTP interactions are an officially supported Discord-on-Workers deployment.
The server pushes status every 30 seconds and polls linked members through Discord REST;
no public Minecraft HTTP port, Discord Gateway connection or cron job is required.

For a small guild this is designed to fit the free allowance. A normal bridge cycle uses
two baseline Worker requests plus one request per two of up to ten linked accounts
(at most five sync requests). Link-list pagination adds one request per 100 accounts.
An ordinary 30-second cycle therefore uses 5,760–17,280 Worker requests/day for up to
100 links, plus player interactions and optional companion requests. D1 state writes
grow with actual polling; ten accounts polled every cycle consume about 57,600
sync-state writes/day before other operations/index writes. Monitor actual usage.
Requests fail when a free allowance is exhausted; synchronization retains game ranks
and retries later. This repository does not enable a paid plan.

Workers do not directly run the original `discord.py` process. The companion can run
on an existing Linux host without an additional hosting subscription if that host
allows it. Oracle Always Free is another option, but capacity can be unavailable and
idle instances can be reclaimed. Google Cloud's free VM allowance requires billing
setup and careful handling of networking and other chargeable resources. Neither is
assumed or provisioned here. A full Cloudflare Gateway implementation using Durable
Objects is a separate design, not the deployed behavior of this bot.

Sources checked 2026-11-18:
[Workers limits](https://developers.cloudflare.com/workers/platform/limits/),
[D1 pricing](https://developers.cloudflare.com/d1/platform/pricing/),
[Discord Workers tutorial](https://docs.discord.com/developers/tutorials/hosting-on-cloudflare-workers),
[Discord Gateway](https://docs.discord.com/developers/events/gateway),
[Oracle Always Free](https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm).

## Configure Cloudflare and Discord

1. Use Node 22.13+ (Node 24 recommended), `npm ci`, then `npx wrangler login`.
2. Run `npx wrangler d1 create mandomc-discord`. Put the returned database ID in
   `wrangler.jsonc`; keep the `DB` binding name. Apply the schema:
   `npx wrangler d1 migrations apply mandomc-discord --remote`.
3. Set `DISCORD_APPLICATION_ID`, `DISCORD_GUILD_ID` and role/channel IDs in
   `wrangler.jsonc`. `STAFF_ROLE_IDS` is a JSON array of Discord role ID strings.
   Set `TICKET_CATEGORY_ID` to the existing Tickets category and
   `TICKET_LOG_CHANNEL_ID` to the existing ticket-logs channel to reuse them.
   `LINKED_ROLE_ID` and `CITIZEN_ROLE_ID` identify the existing Linked and Mando
   Citizen roles. Empty IDs disable those optional roles.
4. In the Discord Developer Portal, use the existing application if keeping the old
   identity and ticket panels. Copy its Public Key and current bot token privately.
   `DISCORD_TOKEN` retains the original bot's environment variable name.
5. Set secrets with `npx wrangler secret put NAME`: `DISCORD_TOKEN`,
   `DISCORD_PUBLIC_KEY`, `BRIDGE_SECRET`, and optionally `GATEWAY_SECRET`.
   Generate different bridge/gateway secrets with `openssl rand -hex 32`.
   Never paste tokens into tracked config or chat. Local `.dev.vars` is ignored.
6. Deploy with `npm run deploy`. Set the application's Interactions Endpoint URL
   to `https://<your-worker>.workers.dev/interactions` (the alias
   `/api/interactions` works too). Discord's signed PING should validate.
7. Install the app in the guild with `bot` and `applications.commands` scopes.
   Give it Manage Roles, Manage Channels, View Channels, Send Messages,
   Embed Links, Attach Files and Read Message History, in the ticket category
   and log channel as appropriate. Place its role above every role it manages.
   It does not need Administrator. With the companion, enable Server Members
   and Message Content privileged intents too.
8. Copy `.dev.vars.example` to `.dev.vars` and privately fill the token,
   application ID and guild ID. `npm run register` replaces this application's
   guild command definitions with the five commands here. Existing global
   commands are deliberately left untouched. Remove obsolete global definitions
   in the Developer Portal only after checking the new guild commands.
9. Post `/ticket-panel` or retain the old panel. Members can run `/ticket` even
   if no panel exists. Test with a non-staff member.

## Connect Minecraft and enable both-way ranks

In `plugins/MandoMC/integrations/discord.yml`, set `endpoint` to the Worker HTTPS
origin and `enabled: true`. Store the same `BRIDGE_SECRET` privately in
`plugins/MandoMC/private/discord-bridge-secret`, or the server process environment
variable `MANDOMC_DISCORD_BRIDGE_SECRET`. Use owner-only file permissions. Keep
reviewed endpoint/mapping config aligned with your managed deployment source;
routine releases overwrite managed YAML files. Secrets are outside that allowlist.

Set `RANK_ROLES` in `wrangler.jsonc` and `rank-roles` in the Minecraft config to
exactly the same rank-to-role-ID mapping, for example:

```json
{"warrior":"123456789012345678","marshal":"234567890123456789"}
```

Both sides refuse to sync when their mappings differ. Use the existing native IDs
`warrior`, `marshal`, `mandalore`, `creator`, `builder`, `mod`, `mandostudios`.
Only add ranks that should be controlled by the mapped Discord roles. A mapping
can grant and revoke that native rank, including its permissions. In particular,
`mandostudios` includes wildcard permissions and staff ranks confer staff access.
There are no automatic staff mappings. Operator status is never synchronized.

After a normal server restart, a player runs `/discord` in Minecraft and uses
`/link code:<code>` in the guild. Codes have 80 bits of random entropy, expire in
ten minutes, are stored as hashes and can be redeemed once. Each Minecraft UUID
and Discord account has at most one link. Five attempts/minute limit guessing.
There is deliberately no user-facing unlink/relink shortcut that could leave old
privileged roles behind. Staff must remove mapped/Linked roles and retire the
verified D1 link together before a deliberate relink.

First link and mapping changes use Minecraft's current explicit memberships as
the baseline. After that, a changed Minecraft membership changes its mapped
Discord role; a changed Discord role changes the native rank. Per-rank concurrent
changes use the Minecraft value. Explicit higher ranks do not manufacture extra
lower-rank roles from inheritance. Unmapped ranks and unrelated roles are preserved.
Minecraft validates the request snapshot before applying changes through the native
atomic RankService. Pending intent is saved before Discord writes, so retries are
idempotent after partial Discord updates, timeouts or dropped Minecraft replies.
During an unfinished update, its saved target is replayed until the server acknowledges
it; a fresh Minecraft membership set supersedes an incomplete Discord write.

Up to ten linked accounts rotate each cycle, including offline UUIDs. Ten accounts
normally converge within a few cycles; larger guilds take up to
`ceil(linkedAccounts / 10) * interval` seconds plus request/acknowledgment time.
A departed Discord member is skipped, never treated as a rank removal. Missing
permissions, rate limiting and network failures retain existing game ranks and retry.
The server logs UUIDs for applied rank changes and rate-limited generic failures;
tokens and link codes are never logged. `/players` and `/baltop` reject snapshots
older than two minutes.

## Existing bot compatibility and cutover

The inspected upstream repository contains no custom webhook routes or webhook URLs.
It authenticates using `DISCORD_TOKEN` and reads MySQL `links` and `balances`.
Reusing the Discord application preserves its identity, guild membership, roles,
channels and old component IDs: `ticket_select`, `claim_ticket`, `close_ticket`.
Old ticket channels can be adopted only inside the configured guild/category when
their topic is the numeric owner ID written by the original bot. Old messages are
included in channel-history transcripts, including after restarts.

Existing incoming Discord webhook URLs are unaffected: no script deletes, rotates
or replaces them. HTTP interaction routing is configured separately in the Developer
Portal. Do not rotate the existing token unless it is compromised.

The old MySQL connection is not a webhook and D1 is not a drop-in MySQL server.
This implementation does not silently change old DB settings, import unverifiable
ownership records, or introduce a second economy writer. Old links require relinking
unless staff explicitly migrates a trusted export. The original old `/baltop` still
needs its old MySQL balance feed; the replacement reads the native credit snapshot.
Back up the database and keep the old process available for rollback. Stop it when
switching live ownership to avoid duplicate Gateway DMs, auto-role handlers and
command registration. Run only the new optional companion afterward.

No live account, role, webhook, database, Minecraft server or Cloudflare deployment
is changed by cloning/building/testing this source.

## Optional Gateway companion

Create a Python 3.12+ venv in `gateway/`, install `requirements.txt`, copy
`.env.example` to `.env`, and fill private values. Use the same bot token and guild
IDs as the Worker, but the separate `GATEWAY_SECRET`. Run `python companion.py`.
`mandomc-discord.service` is a Linux systemd example with automatic restarts; adjust
its paths and service account for the chosen host. Keep the environment file readable
only by the service account. The companion never runs command-tree synchronization.

## Verification and recovery

`npm test` exercises a real SQLite schema, real Ed25519 signatures, account uniqueness,
expiry/attempt limits, both sync directions, replay/partial failure, staff controls,
legacy tickets and transcript-before-delete. Discord HTTP calls are mocked.
`npx wrangler deploy --dry-run` verifies Cloudflare bundling without publishing.
Run `./scripts/build.sh` from the repository root for Java tests and packaged M4-ND
knowledge checks. The plugin integration is disabled by default.

The optional companion has six unit tests: from `gateway/`, run
`python -m unittest discover -p 'test_*.py' -v` after installing its requirements.
`scripts/test-discord-bridge.py` runs the native bridge in disposable Paper against
a local HTTPS stub (see its `--help` for the pinned runtime/JDK inputs). It verifies
native offline persistence, an unrelated rank, stale-response rejection and recovery
after an HTTP outage. It copies caches only; no production worlds or player files
are used. The fixture's trust store is private and applies only to its child JVM.

Before production activation, test a nonstaff link, each direction's grant/removal,
offline-player sync, a network outage/restart, role hierarchy denial and a real
ticket transcript with the chosen Discord application. Test DM relay separately
if using the companion. Local tests do not establish live Discord permissions.

If channel creation or closure is interrupted, inspect D1's `tickets` row and Discord
channel before retrying. A `creating` reservation deliberately remains after an
ambiguous timeout to avoid duplicate private channels. Attach the verified channel
ID and set its status `open`, or retire the reservation if no channel exists.
A `closing` row is reopened only after verifying whether Discord deleted its channel
and whether the log transcript exists. A ticket with 1,000+ messages is retained
for manual export instead of being deleted with a truncated transcript.
Cloudflare D1/SQLite transactions protect local identity and reservations; Discord
and D1 are separate systems, so their mutations are not a distributed transaction.
