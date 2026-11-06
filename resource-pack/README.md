# MandoMC resource pack
The new pack is source-controlled here in the MandoMC repository. The old MandoPack repo is recovery material, not an independently deployed dependency.

## Build
Requires Python 3, with no extra packages:
```bash
./scripts/build-resource-pack.py
```
Output: `resource-pack/build/MandoMC.zip` and a JSON report containing SHA-1 and SHA-256. Builds use stable ordering/timestamps and validate JSON, local model/texture references and the pinned Minecraft target. Build output is ignored by Git. The build does not download assets, install the pack in Minecraft or start a hosting service.

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

These are cosmetic previews only. They are not yet configured as firing WeaponMechanics weapons, and no pose switching or starter kit has been implemented. Existing vanilla items are not overridden.

To inspect the base model, join the server and accept the hosted pack, then use this command as an operator. For unpublished local previews, enable the built ZIP in your client's Resource Packs menu instead:
```mcfunction
/minecraft:give @s minecraft:paper[minecraft:item_model="mandomc:blasters/e11"]
```
Use the other keys to inspect the alternate poses. Check inventory, ground, item frame and both hands in a matching client. Client rendering has not yet been tested.

## Recovering more assets
`sources.json` pins the old GitHub commit, original archive SHA-256 and per-file hashes. Source assets now build offline from this repository. Preserve existing credits; record each additional recovery and any conversion in the manifest.

Recover one feature's assets with its configuration. Models, textures, sounds and item definitions belong under the `mandomc` namespace where possible. Do not restore broad vanilla overrides, shaders, generated HUD assets or generated ModelEngine files just because they were bundled in the old ZIP. ModelEngine blueprints must be recovered separately before its models can be regenerated and merged deliberately.
