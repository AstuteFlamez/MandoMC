# Feature: Maintenance access and admin audit
Status: deployed; private playtest access controls
Branch: bilal/maintenance-admin-protection

## Player experience
Maintenance is enabled initially. Only the established owner can join; other players receive a branded maintenance message before world entry. Staff can manage a persistent independent maintenance list. Turning maintenance on does not kick existing players.

## Rules
MandoAccess is a small independent operational module, built with the reactor as access/target/MandoAccess.jar. It loads at STARTUP and remains restrictive if the gameplay plugin fails. It checks authenticated UUIDs at async prelogin and again at login; it never clears a ban, whitelist, capacity or other login denial. Online mode is required. Initial state is enabled and contains only the configured initial-owner, resolved from the server's existing operator records. No UUID is generated from a name. Account additions use Paper's asynchronous profile resolution with a twenty-second timeout and four-lookup bound. Completion rechecks sender permission before saving.

The original owner's UUID remains listed and cannot be removed through commands, including by console. An allowed account gains no operator/admin rights. The owner can recover through console; a damaged/missing owner state is never silently reset. Invalid existing state blocks joins until the operator repairs it and restarts. State changes use immutable snapshots, force the temporary file to disk and atomically replace maintenance.yml before publishing the new snapshot. A failed save leaves the previous live state active.

Maintenance and vanilla whitelist are independent. For private playtests keep white-list=true and enforce-whitelist=true, with the owner already listed. A tester needs vanilla whitelist access; while maintenance is enabled the same account also needs maintenance access. Turning maintenance off admits ordinary whitelisted testers subject to normal bans/capacity.

## Ownership and integrations
MandoAccess owns maintenance.yml and /maintenance (/maint), permission mandomc.admin.maintenance (operator by default), and the permission-filtered /adminhelp, permission mandomc.admin.help. Console can manage maintenance. All allowlist data and completions are private. Existing gameplay admin commands are reused, including /gm, /fly, /speed, /heal, /feed, /god, /vanish, /tp, /tphere, /invsee, /enderchest and /event; no second implementation is added.

CoreProtect CE 24.1 owns standard block place/break, container/hopper transactions, dropped/picked-up items, explosions, entity changes and audit lookup/rollback. Its official release supports Paper 1.16.5–26.2, including this pinned Paper 26.1.2 / Java 25 runtime. The sanitized config selects embedded SQLite. MandoAccess makes CoreProtect's otherwise-public root command permission operator-only after CoreProtect enables; lookup/inspect/rollback/restore retain CoreProtect's staff-only defaults. Separate explicit grants remain possible. Maintenance permission does not grant CoreProtect access.

The existing gameplay JAR, claims/raids, temporary explosion restoration, custom mobs, entities and vehicles remain byte-identical. Standard explosion events remain audited. Automatic terrain restoration uses direct native block updates and does not emit new player placement events; no global logging blacklist or player-audit suppression is introduced. CoreProtect is incident recovery, not the authoritative claim/raid policy. Avoid rolling back custom event entities or temporary blast holes while their owning subsystem is active.

## Parameters
access/src/main/resources/config.yml supplies schema-version=1, initial-owner, denied and unavailable messages. The initial owner applies only when first creating state. Message/config changes require a restart; mode/list commands persist immediately. Runtime maintenance.yml contains schema-version, enabled, owner UUID and players UUID/name map. Never commit runtime state, databases or JARs.

## Admin playbook
- `/maintenance status`, `/maintenance list` — inspect access.
- `/maintenance add PlayerName`, `/maintenance remove PlayerName` — edit maintenance access; adding does not whitelist or grant OP.
- `/maintenance on` — restrict new joins; existing online players stay connected.
- `/maintenance off` — reopen to the ordinary whitelist.
- `/whitelist add PlayerName`, `/whitelist list`, `/whitelist on` — manage ordinary playtest admission.
- `/adminhelp` — list available commands filtered by the sender's permissions.
- `/co status`, `/co i` — inspect audit health or toggle block/container inspector.
- `/co l u:PlayerName t:1h r:10 a:block` — inspect nearby block changes.
- `/co l u:PlayerName t:1h r:10 a:container` — inspect nearby container transfers.
- `/co rollback u:PlayerName t:1h r:10 a:block #preview` — preview a tightly scoped repair while standing at the incident.
- `/co apply` commits the preview; `/co cancel` cancels it. `/co restore u:PlayerName t:1h r:10 a:block` reverses that same scoped rollback.

Back up worlds and the CoreProtect data folder before significant repairs. Use the same user/time/radius/action filters for lookup, preview and repair. Production rollback is never a deployment test. Audit history starts at installation and does not reconstruct earlier incidents.

## Verification
Full reactor verification passes 107 existing tests and four access unit tests. Forty isolated Paper assertions cover real named-account resolution, permission and whitelist separation, restart persistence, container item removal, real explosion audit, lookup and scoped test-block rollback. Four unit tests cover access rules, owner lockout prevention, restart serialization, invalid state rejection and failed-write preservation. The isolated AccessProbe only runs with a disposable world named access_probe, checks Paper login handlers/permission defaults and invokes logging/rollback against isolated test blocks. It is never installed in production. These handler tests do not claim an authenticated second-account Minecraft client test.

## Pause note
Installed with a stopped-runtime backup and normal restart. Maintenance starts enabled with only the verified owner; normal whitelist is enabled with the owner already included. Gameplay JAR, resource pack, launcher and javaagent are preserved. Next action: owner reconnects, checks /maintenance status and /co i, then adds chosen testers to the normal whitelist and turns maintenance off when ready.

Sources: [official CE 24.1 release](https://github.com/PlayPro/CoreProtect/releases/tag/v24.1), [official free download](https://www.patreon.com/coreprotect/posts/coreprotect-v24-170398122), [CoreProtect commands](https://docs.coreprotect.net/commands/).
