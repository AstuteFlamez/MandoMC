# MandoMC resource pack
The new pack is source-controlled here in the MandoMC repository. The old MandoPack repo is recovery material, not an independently deployed dependency.

## Build
Requires Python 3, with no extra packages:
```bash
./scripts/build-resource-pack.py
```
Output: `resource-pack/build/MandoMC.zip` and a JSON report containing SHA-1 and SHA-256. Builds use stable ordering/timestamps and validate JSON, local model/texture references and the pinned Minecraft target. Build output is ignored by Git. The build does not download assets, install the pack in Minecraft or start a hosting service.

Target: Minecraft Java 26.1.2, resource format 84.0, verified from the server's version.json and [Minecraft's release notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-1). The min/max metadata uses the [current pack format](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-9). Change the server and pack target together after validation.

## First recovered content
E-11 base, aiming and sprinting models plus the original texture are under `src/assets/mandomc/`. Aiming and sprinting models inherit the base geometry. The item model keys are:
- `mandomc:blasters/e11`
- `mandomc:blasters/e11_scope`
- `mandomc:blasters/e11_sprint`

These are cosmetic previews only. They are not yet configured as firing WeaponMechanics weapons, and no pose switching or starter kit has been implemented. Existing vanilla items are not overridden.

To inspect the base model, put the built ZIP in your client's resourcepacks folder, enable it in Resource Packs, then use this command as an operator:
```mcfunction
/minecraft:give @s minecraft:paper[minecraft:item_model="mandomc:blasters/e11"]
```
Use the other keys to inspect the alternate poses. Check inventory, ground, item frame and both hands in a matching client. Client rendering has not yet been tested.

## Recovering more assets
`sources.json` pins the old GitHub commit, original archive SHA-256 and per-file hashes. Source assets now build offline from this repository. Preserve existing credits; record each additional recovery and any conversion in the manifest.

Recover one feature's assets with its configuration. Models, textures, sounds and item definitions belong under the `mandomc` namespace where possible. Do not restore broad vanilla overrides, shaders, generated HUD assets or generated ModelEngine files just because they were bundled in the old ZIP. ModelEngine blueprints must be recovered separately before its models can be regenerated and merged deliberately.
