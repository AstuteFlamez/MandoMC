# Resource planet ores, encounters and U-wing seats

Agrinium, Electrum and Durasteel share a 75/15/10 distribution in the surveyed mineable wilderness on Tatooine, Hoth, Mandalore and Naboo. This describes the share of natural metal ore cells. The maps retain their own geography and ore slot counts. The former Mandalore wilderness coal slots become the three regular metal ores, and the prior coal deposits in the protected Mandalore caves revert to their exact host rock. Coal remains available on Tatooine, Hoth and Naboo.

Beskar ore remains exclusive to the ten registered nodes in Mandalore's Beskar Rush. That event now has rotation weight 10, alongside KOTH at 30 and Jabba at 20. The selection menu shows a base chance of 16.7% before the existing cooldown rules are applied. Old manual-only defaults migrate once. Later configuration edits can set an explicit rotation preference.

The U-wing has six passenger mounts in its cargo cabin, plus the existing pilot and gunner. Passengers may choose an open seat through the vehicle menu. Only the deploying owner can pilot. The gunner is the only occupant who can use a held blaster while aboard.

On death, Boba Fett drops an EE-3, jetpack or flamethrower. The existing independent equipment rolls run first, and an EE-3 fills in when all three miss. Bossk retains his existing guaranteed rare Dioxis Grenade drop and Relby chance. The grenade stack ranges from 12 to 20 and averages 16. The Rancor continues to drop its legendary skull.

## Terrain cutover

Run `scripts/rebalance-planet-ores.py --runtime PATH --output PATH` with the map-tool requirements and PyYAML against a stopped snapshot. The plan reports the target ores, exact current-world changes and the staged file hashes. Apply the checked staging transaction with the same arguments and `--apply` after a normal stop, using the current runtime as the target. Use a fresh output directory if the live runtime changed since the snapshot. The tool acquires native world locks and backs up each affected region. Reuse the same output directory to resume an interrupted apply.

The new `data/planet-ore-terrain.yml` incorporates the remaining pre-existing coal overlay and the new metal terrain changes. Land restoration reads it over the original immutable snapshots. Claimed or staff-marked wilderness and player-modified blocks are preserved. Batuu, Ilum, protected builds, the warped terrain geometry and Beskar event journals are outside the patch. Deploy the new land-restoration code alongside the offline region changes.

Install the updated U-wing blueprint with the managed seat configuration before starting Paper and allow ModelEngine to rebuild it.
