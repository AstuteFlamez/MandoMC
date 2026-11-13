# Local development and PebbleHost

`main` is the production source. `dev` is the integration branch. `hotfix` is the
short path for urgent production fixes. Normal feature branches use
`bilal/<feature>` and merge into `dev`; promote a tested `dev` into `main` with a
pull request. Start hotfix work from the latest `main`, merge through `hotfix`
into `main`, then merge `main` back into `dev` and `hotfix`. Never force-push.

The GitHub `Server checks` job builds Java 25, runs the Java and Python tests,
and validates the resource pack. The public ModelEngine free API is downloaded
and hash-verified for compilation only. The licensed premium JAR is never
committed or attached to a public Actions artifact.

After the initial migration is verified and repository variable
`PEBBLEHOST_BOOTSTRAPPED` is `true`, a successful push to `main` uploads the exact
commit over SFTP. The upload job uses the exact binaries retained by the passing checks. The matching
resource pack publishes before upload. The production environment accepts `main` only. Branch work
and pull requests cannot access its secret. The workflow sends no power or
reload commands. Click **Restart** in PebbleHost to activate an uploaded build.

## Work on your computer

Use the `production-integration` checkout containing this document as the new clean
workspace. Other worktrees and their uncommitted work are retained. Start work:

```bash
git switch dev
git pull --ff-only origin dev
git switch -c bilal/my-feature
./scripts/build.sh
```

Use the integrated production checkout for new work; historical worktrees remain independent.

The source you edit is in `plugin/`, reviewed production config in
`server/config/common/mandomc/`, and artwork in `resource-pack/src/`. JARs are
built from source. Downloading a production JAR does not recover its source.
The legacy remote branches were preserved under `archive/bilal/legacy-*` tags
before removal; local feature worktrees were not removed.

## Private SFTP connection and downloads

Run `bash scripts/setup-pebblehost.command` once. It shows the host fingerprint
before sending a password, verifies login, and saves an owner-only ignored
`.local/pebblehost/sftp.json`. It can save the same connection into the encrypted
GitHub `production` environment secret `PEBBLEHOST_SFTP_JSON`. Do not paste the
password in chat, commit it, or use it as a command argument.

```bash
.local/tools/bin/python scripts/pebblehost.py check
.local/tools/bin/python scripts/pebblehost.py pull
```

`pull` copies worlds, plugins and configuration, excluding generated host caches/logs. It creates a new private folder under `.local/pebblehost/downloads/` and
never writes to the server or overwrites a prior download. It skips symlinks.
Use `pull --resume PATH` to finish an interrupted download; large existing files are reused by size.
Stop Paper or use a host-created backup before relying on a world snapshot;
a live file download can mix saves from different ticks. Downloads remain
separate from tracked source and are not automatically committed or uploaded.

## What uploads

`deployment/files.json` is the explicit allowlist. Regenerate it with `python scripts/build-deployment-manifest.py` after adding managed source files.
It includes the gameplay/access JARs, reviewed plugin configuration and model blueprints,
the jetpack datapack, and an immutable version of the redstone startup agent. Worlds, player inventories,
claims, balances, vendor JARs, credentials, and operator lists,
and arbitrary runtime files are excluded from routine deployment.

Paper's `plugins/update/` receives the next gameplay and access JARs, leaving their running
files intact until restart. Each agent is uploaded to a new content-addressed filename;
`pebblehost.yml` selects that agent and Java 25 for the next manual restart. The current
JVM's agent file is retained. The bootstrap must retain the configured
`settings.update-folder: update` in `bukkit.yml`. YAML files are uploaded
to verified temporary files and renamed into place; do not run a config reload between an
upload and its restart. An already-running system may independently reread a
file, so a bundle-wide atomic activation is not claimed.

Before uploading, the tool checks the hashes from `.mandomc/deployed.json`.
Unexpected remote edits stop the upload. A remote mkdir lock serializes
deployments, including local CLI use. Previous bytes and the deployment receipt
are retained in `.mandomc/backups/<timestamp>-<commit>/`. Each uploaded file is
read back and verified before replacement. PebbleHost rejects overwrite renames, so the
old name is moved to a retained sibling first, then the verified new file is renamed
into place. A failed second rename restores the old name. There is a short interval
where the replaced path is absent; this is not a bundle-wide atomic transaction. A mid-upload connection failure
can leave a partial config set; inspect the receipt, backup and lock before
recovery. Do not restart blindly after a failed deployment. Managed file
deletions require a reviewed migration; the upload job never deletes them.

For a local preview, on the exact clean production commit after building:

```bash
.local/tools/bin/python scripts/pebblehost.py deploy --commit "$(git rev-parse HEAD)"
```

Add `--apply` only when intentionally performing an upload. Routine releases
should use GitHub so that checks and the production branch policy apply.

## One-time migration

The old PebbleHost server is a different version and layout from this rebuild.
Routine uploads are blocked until a full one-time migration is completed.
The completed local feature histories have been reconciled in the production integration.
Build the intended main commit and use its checked CI artifact as the binary baseline.

The migration must use a stopped, consistent copy of the rebuilt local worlds
and configuration, the hash-pinned Paper and vendor binaries, the built main
JAR, and the intended hosted resource pack. Preserve a complete copy of the old
PebbleHost files before moving its obsolete plugins/worlds aside. The cutover retains all old active
entries under `.mandomc/legacy/`; it never permanently deletes them. Keep the
old copy outside the active plugin/world paths. Copy only the desired rebuilt
runtime, preserving PebbleHost's host-managed files. Set the panel startup JAR
to the new Paper filename and its Java runtime to 25. The current dedicated server allocation is
142.4.206.185:25565 and the bind address must not be laptop loopback. The preparer disables RCON/query and excludes probe plugins. The rebuilt server's
existing owner/maintenance/whitelist state is retained privately.

Verify startup, world loading, dependency enablement, non-op player join and
the resource-pack download on PebbleHost. Seed the managed file hashes in
`.mandomc/deployed.json` and a schemaVersion 1 `.mandomc/bootstrap.json` receipt
only after the installed layout matches the upload manifest. Enable the GitHub
variable only after verification. Start/restart is an explicit operator action. Routine uploads leave restart timing to the operator.

## Rollback

Stop the server manually before restoring a previous JAR/config set. Inspect
`previousBackup` in the last receipt, download that backup, verify its bytes,
and restore its corresponding files and receipt together. Remove or replace a
queued update JAR deliberately so it cannot supersede the restored JAR on the
next startup. Start manually and verify the expected build. These backups
cover managed files only; world/player migrations require a matching full
runtime backup. Never use Git to roll back world or player state.

Prepare from a stopped rebuilt runtime, stage the resulting private image, then activate
only after PebbleHost is visibly stopped:

```bash
python scripts/prepare-production.py --runtime PATH --output .local/pebblehost/production --port 25565
.local/tools/bin/python scripts/migrate-pebblehost.py stage --runtime .local/pebblehost/production
.local/tools/bin/python scripts/migrate-pebblehost.py activate --runtime .local/pebblehost/production --server-stopped
```

Select `server.jar` in PebbleHost settings; the host config chooses Java 25 and the
matching immutable agent. After the explicit migration start, run
`.local/tools/bin/python scripts/pebblehost.py verify-startup`. This checks startup
markers and managed hashes before enabling routine uploads. Authenticated client
playtesting remains a separate human check. The launcher never sends a restart.

Each upload preserves the server's existing host-specific properties while updating
only the main resource-pack URL/hash/identity/prompt and the source MOTD. Previous
server.properties and PebbleHost startup settings are backed up with the managed files.
The host's jar auto-update is disabled so its pinned Paper version remains deliberate.
