# Voting and the Voting Crate

Players use `/vote` for the server listings. Every accepted vote awards 500 native credits and one virtual Voting Crate key. VotingPlugin queues offline votes until the player joins; unjoined names and unknown services are rejected. Rewards use MandoMC commands, with no second currency store. The configured services are Minecraft-MP, Minecraft Server List, MinecraftServers.org, Planet Minecraft and Minecraft.Buzz.

An operator places a chest or barrel, looks directly at it, and runs `/crates setblock vote`. Left-click previews the crate; right-click opens it. `/crates preview vote`, `/crates open vote` and `/crates keys` also work. Location writes use the private server-owned `plugins/MandoMC/data/crate-blocks.yml`, so later source deployments preserve placement. Free inventory space before opening. Crate keys and pending rewards remain in the native durable player-data system.

The initial reward table is configurable: 40% eight agrinium scraps, 30% eight electrum scraps, 20% four durasteel scraps, 8% one beskar scrap, 2% one white kyber. Each opening consumes one key.

NuVotifier listens on the allocated TCP port 19132. Private RSA keys and the V2 token belong only in the ignored runtime or encrypted secret store. Public voting sites use the public key from `plugins/Votifier/rsa/public.key`; never upload `private.key`. The migration preserves the existing RSA identity so registered public keys remain valid. Production host is 142.4.206.185. Website endpoint changes and their test-vote acknowledgements must be verified separately from listener startup.

Plugin artifacts are hash-pinned in `server/plugins.lock.json`. Only reviewed `VoteSites.yml` is deployed from Git; listener configuration, credentials, vote databases and crate placement are excluded. The secret scanner checks Git history before CI builds. Four exact historical false positives (asset hashes and a public Figma ID) are documented in `.gitleaksignore`.

For a disposable full-runtime check, compile `VotingProbe.java` alongside `NativeProbePlayers.java` using the candidate and Paper library classpath, install only into a loopback fixture, then run `scripts/test-vote-listener.py --runtime <fixture>`. The probe requires one encrypted TCP vote to produce exactly 500 credits and one key. Probe plugins must never enter a production image.

## Development parity (2026-11-13)
Development uses the same pinned voting plugins, five site links, 500-credit reward and native
Voting Crate definition as production. Its NuVotifier listener binds to loopback with its own
private identity. Public websites retain the production endpoint. Only the vote crate's saved
block binding is mirrored; player balances, vote history and existing Kyber/KOTH placements
remain owned by each server. New feature deployments and restarts happen on development first.
