# Recovering old features
Original source commit: `03daf999caf6d8cec6773a6924d2a7b66257a934`.

The complete previous main, including documentation, is preserved at [bilal/legacy-main-2026-11-05](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-main-2026-11-05) and tag `legacy/pre-rebuild-2026-11-05`.

Each feature branch starts from the new foundation and adds its original source, configuration and tests under `recovery/<feature>/`. Payload files retain their original paths and exact bytes. They are outside the active Maven module and are **unported reference material**, not independently runnable features.

| Feature | Original files | Recovery branch |
| --- | ---: | --- |
| abilities | 30 | [`bilal/legacy-abilities`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-abilities) |
| beskar-rush | 5 | [`bilal/legacy-beskar-rush`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-beskar-rush) |
| bounties | 15 | [`bilal/legacy-bounties`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-bounties) |
| discord-linking | 8 | [`bilal/legacy-discord-linking`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-discord-linking) |
| economy-shops | 23 | [`bilal/legacy-economy-shops`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-economy-shops) |
| event-framework | 15 | [`bilal/legacy-event-framework`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-event-framework) |
| ilum-parkour | 23 | [`bilal/legacy-ilum-parkour`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-ilum-parkour) |
| items-crafting | 25 | [`bilal/legacy-items-crafting`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-items-crafting) |
| jabba-dungeon | 8 | [`bilal/legacy-jabba-dungeon`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-jabba-dungeon) |
| koth | 5 | [`bilal/legacy-koth`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-koth) |
| lightsabers | 17 | [`bilal/legacy-lightsabers`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-lightsabers) |
| lottery | 17 | [`bilal/legacy-lottery`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-lottery) |
| shared-foundation | 36 | [`bilal/legacy-shared-foundation`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-shared-foundation) |
| tatooine-resources | 2 | [`bilal/legacy-tatooine-resources`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-tatooine-resources) |
| travel | 5 | [`bilal/legacy-travel`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-travel) |
| vehicles-fuel | 60 | [`bilal/legacy-vehicles-fuel`](https://github.com/AstuteFlamez/MandoMC/tree/bilal/legacy-vehicles-fuel) |

All 294 non-documentation files are assigned once. Thirteen old documentation/rule files remain only in the complete snapshot. The exact mapping is in [the recovery manifest](../planning/legacy-features.json).

## Bring back a feature
Start a new `bilal/<feature>` branch from current main. Inspect the relevant recovery branch and shared foundation. Adapt only the behavior needed for the new player experience into `plugin/`; place configuration with its owner, update current integrations, and test. Merge the adapted implementation when satisfactory. Do not blindly merge the raw recovery payload as active code.

Vehicles and fuel remain together because their lifecycle is tightly coupled. Event scheduling is separate from KOTH, Jabba and Beskar. Shared item definitions remain in items-crafting; feature-specific definitions travel with the feature. The complete old assembly is always recoverable from the snapshot.
