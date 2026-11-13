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
commit over SFTP. The production environment accepts `main` only. Branch work
and pull requests cannot access its secret. The workflow sends no power or
reload commands. Click **Restart** in PebbleHost to activate an uploaded build.

## Work on your computer

Use the `server-workflow` checkout containing this document as the new clean
workspace. Other worktrees and their uncommitted work are retained. Start work:

```bash
git switch dev
git pull --ff-only origin dev
git switch -c bilal/my-feature
./scripts/build.sh
```

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

`pull` creates a new private folder under `.local/pebblehost/downloads/` and
never writes to the server or overwrites a prior download. It skips symlinks.
Stop Paper or use a host-created backup before relying on a world snapshot;
a live file download can mix saves from different ticks. Downloads remain
separate from tracked source and are not automatically committed or uploaded.

## What uploads

`deployment/files.json` is the explicit allowlist. It contains the built
MandoMC JAR and reviewed MandoMC YAML configuration. Worlds, player inventories,
claims, balances, vendor JARs, credentials, operator lists, machine settings,
and arbitrary runtime files are excluded from routine deployment.

Paper's `plugins/update/MandoMC.jar` receives the next JAR, leaving the running
plugin intact until restart. The bootstrap must retain the configured
`settings.update-folder: update` in `bukkit.yml`. YAML files are uploaded
atomically to their configured paths; do not run a config reload between an
upload and its restart. An already-running system may independently reread a
file, so a bundle-wide atomic activation is not claimed.

Before uploading, the tool checks the hashes from `.mandomc/deployed.json`.
Unexpected remote edits stop the upload. A remote mkdir lock serializes
deployments, including local CLI use. Previous bytes and the deployment receipt
are retained in `.mandomc/backups/<timestamp>-<commit>/`. Each uploaded file is
read back and verified before atomic rename. A mid-upload connection failure
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
Build the intended main commit and reconcile any wanted local feature changes
before treating it as the production baseline; the old local live plugin was
assembled from multiple branches and is not reproduced by a main build yet.

The migration must use a stopped, consistent copy of the rebuilt local worlds
and configuration, the hash-pinned Paper and vendor binaries, the built main
JAR, and the intended hosted resource pack. Preserve a complete copy of the old
PebbleHost files before moving its obsolete plugins/worlds aside. Keep the
old copy outside the active plugin/world paths. Copy only the desired rebuilt
runtime, preserving PebbleHost's host-managed files. Set the panel startup JAR
to the new Paper filename and its Java runtime to 25. The server allocation is
port 25570 and the bind address must not be laptop loopback. Do not copy laptop
RCON/proxy credentials or probe plugins.

Verify startup, world loading, dependency enablement, non-op player join and
the resource-pack download on PebbleHost. Seed the managed file hashes in
`.mandomc/deployed.json` and a schemaVersion 1 `.mandomc/bootstrap.json` receipt
only after the installed layout matches the upload manifest. Enable the GitHub
variable only after verification. The user performs the first start/restart;
subsequent uploads also leave restart timing to the user.

## Rollback

Stop the server manually before restoring a previous JAR/config set. Inspect
`previousBackup` in the last receipt, download that backup, verify its bytes,
and restore its corresponding files and receipt together. Remove or replace a
queued update JAR deliberately so it cannot supersede the restored JAR on the
next startup. Start manually and verify the expected build. These backups
cover managed files only; world/player migrations require a matching full
runtime backup. Never use Git to roll back world or player state.
