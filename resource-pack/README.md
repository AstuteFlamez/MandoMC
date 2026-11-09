# MandoMC resource pack
The new pack is source-controlled here in the MandoMC repository. The old MandoPack repo is recovery material, not an independently deployed dependency.

## Build
Requires Python 3, with no extra packages:
```bash
./scripts/build-resource-pack.py
```
Output: `resource-pack/build/MandoMC.zip` and a JSON report containing SHA-1 and SHA-256. Builds use stable ordering/timestamps and validate JSON, local model/texture/sound references and the pinned Minecraft target. Build output is ignored by Git. The build does not download assets, install the pack in Minecraft or start a hosting service.

Target: Minecraft Java 26.1.2, resource format 84.0, verified from the server's version.json and [Minecraft's release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1). The min/max metadata uses the [current pack format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9). Change the server and pack target together after validation.

## Hosting and automatic delivery
GitHub hosts the built ZIP as a public release asset. The [publishing workflow](../.github/workflows/resource-pack.yml) runs on main when pack sources, pack tooling or the server target change; it can also be dispatched manually from Actions. It builds/tests the pack and publishes `MandoMC.zip` plus `MandoMC.json` under a `resource-pack-<minecraft-version>-<commit>` prerelease. Published files are preserved on workflow reruns. The separate source-code archive is not a usable resource pack.

Server startup runs `scripts/sync-resource-pack.py` before Paper. It selects the newest published pack for the pinned Minecraft version, downloads it without authentication, verifies SHA-1, SHA-256, size and pack metadata, and updates only the pack properties in the local server.properties. The URL identifies a specific release: a newly published pack cannot change the bytes behind an already-running server's hash. A verified copy and manifest are cached in ignored `server/runtime/resource-packs/`; temporary network failures reuse that copy. An incompatible or unverified first download stops startup without altering server settings.

`hosting.json` owns the repository, stable pack UUID, required flag and player prompt. The pack is required. Minecraft prompts players on first join; when accepted, Minecraft downloads and applies it. Set the server-list entry's **Server Resource Packs** option to **Enabled** for subsequent joins without approval prompts. Client-side download/application still needs to be confirmed in Minecraft.

Normal update loop: edit assets → push/merge to main → wait for the **Publish resource pack** workflow → stop/start Paper → reconnect. No manual URL/hash copying or local HTTP service is needed. A local build alone is a preview; it does not publish or change the pack served to players. If you restart before CI finishes, the previous release remains selected.

To sync while Paper is stopped without launching it:
```bash
./scripts/sync-resource-pack.py
```
Use `--offline` to explicitly apply the cached release. Startup selects the newest compatible hosted pack independently of the local branch. Check `server/runtime/resource-packs/current.json` for the exact URL/hash/source commit in use. A cache lets the server start during a hosting outage, but new clients still need to reach GitHub to download the ZIP.

References: [GitHub release downloads](https://docs.github.com/en/repositories/releasing-projects-on-github/linking-to-releases), [GitHub release asset API](https://docs.github.com/en/rest/releases/assets), [Paper resource-pack properties](https://docs.papermc.io/paper/reference/server-properties/).

## First recovered content
E-11 base, aiming and sprinting models plus the original texture are under `src/assets/mandomc/`. Aiming and sprinting models inherit the base geometry. The item model keys are:
- `mandomc:blasters/e11`
- `mandomc:blasters/e11_scope`
- `mandomc:blasters/e11_sprint`

These are wired to the E-11 WeaponMechanics definition, including aim/sprint switching. Standard Plasma Cells, the red bolt and four original sounds are included. See [E-11 setup and verification](../docs/features/e11.md). Starter kits remain deferred. Existing vanilla items are not overridden.

To inspect the base model, join the server and accept the hosted pack, then use this command as an operator. For unpublished local previews, enable the built ZIP in your client's Resource Packs menu instead:
```mcfunction
/minecraft:give @s minecraft:paper[minecraft:item_model="mandomc:blasters/e11"]
```
Use the other keys to inspect the alternate poses. Check inventory, ground, item frame and both hands in a matching client. Client rendering has not yet been tested.

## Recovering more assets
`sources.json` pins the old GitHub commit, original archive SHA-256 and per-file hashes. Source assets now build offline from this repository. Preserve existing credits; record each additional recovery and any conversion in the manifest.

Recover one feature's assets with its configuration. Models, textures, sounds and item definitions belong under the `mandomc` namespace where possible. Do not restore broad vanilla overrides, shaders, generated HUD assets or generated ModelEngine files just because they were bundled in the old ZIP. ModelEngine blueprints must be recovered separately before its models can be regenerated and merged deliberately.

## Credits and saber dependency
Thank you to **JustTimm** for the lightsaber models and textures, including Darth Vader's saber. Visit [Lightsaber Workshop](https://modrinth.com/resourcepack/justtimms-lightsaber-workshop), [his creator profile](https://modrinth.com/user/JustTimm) and [his links](https://linktr.ee/JustTimm). The distributed MandoMC ZIP includes `credits.txt`.

His terms allow server use but prohibit rehosting extracted artwork without permission. MandoMC sends the pinned, unchanged official pack from its Modrinth CDN using Paper's additional-pack API. Our item JSON references its `workshop` models; the original artwork is not bundled or committed here. The exact version/hash/model allowlist is in dependencies.json, and runtime delivery is configured in sabers.yml. Both packs are required for the full visuals.

## Sandy-biome mob assets
Five recovered ModelEngine blueprints are tracked under server/config/common/modelengine with provenance. Generate with pinned ModelEngine R4.1.1 and import its output with `scripts/import-mob-models.py PATH_TO_GENERATED_PACK`. The required MandoMC ZIP contains these namespaced model assets plus their texture atlas directory; vanilla shader files are preserved. Cycler rifle item geometry is adapted from the ranged Tusken's existing rifle because the legacy item dispatch referenced absent cycler models. Its item poses require a client check. Original mob/cycler sounds are recovered from the pinned legacy pack.

Gaffi Stick: the player item reuses the existing generated melee Tusken stick geometry and its embedded-model texture, with item display poses under `mandomc:melee/gaffi_stick`. No new artwork or shader overrides; client item poses await playtesting.

## Legacy transport models
The vehicle slice adds the original speederbike, X-wing, TIE fighter and U-wing fixed models, inventory icons and engine sounds. Provenance is in `vehicle-sources.json` and `server/config/common/modelengine/vehicle-sources.json`. The current ModelEngine generator's output is imported alongside creature models by `scripts/import-mob-models.py`; alternate skins and fuel assets are excluded. The speeder's embedded texture is explicitly namespaced to `modelengine`, matching the generated model reference.
