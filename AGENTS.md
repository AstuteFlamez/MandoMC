# MandoMC — agent notes

Paper plugin (Java 17, Maven). Persistent AI guidance lives in **[`.cursor/rules/`](.cursor/rules/)** (`.mdc` files). Read those first.

## Docs-first onboarding

- Agent docs hub: [`src/main/docs/README.md`](src/main/docs/README.md)
- Architecture guide: [`src/main/docs/architecture.md`](src/main/docs/architecture.md)
- Workflow guide: [`src/main/docs/development-workflow.md`](src/main/docs/development-workflow.md)
- Validation guide: [`src/main/docs/testing-and-validation.md`](src/main/docs/testing-and-validation.md)
- Known issue hotspots: [`src/main/docs/bug-radar.md`](src/main/docs/bug-radar.md)

## Start here

- Main lifecycle + module order: [`src/main/java/net/mandomc/MandoMC.java`](src/main/java/net/mandomc/MandoMC.java)
- Module contract: [`src/main/java/net/mandomc/core/module/Module.java`](src/main/java/net/mandomc/core/module/Module.java)
- Service wiring: [`src/main/java/net/mandomc/core/services/ServiceRegistry.java`](src/main/java/net/mandomc/core/services/ServiceRegistry.java)
- Economy seam: [`src/main/java/net/mandomc/core/services/EconomyService.java`](src/main/java/net/mandomc/core/services/EconomyService.java)
- Link seam: [`src/main/java/net/mandomc/server/discord/service/LinkService.java`](src/main/java/net/mandomc/server/discord/service/LinkService.java)
- Command registration hub: [`src/main/java/net/mandomc/core/modules/core/CommandModule.java`](src/main/java/net/mandomc/core/modules/core/CommandModule.java)
- Config loading hub: [`src/main/java/net/mandomc/core/modules/core/ConfigModule.java`](src/main/java/net/mandomc/core/modules/core/ConfigModule.java)
- Plugin metadata: [`src/main/resources/plugin.yml`](src/main/resources/plugin.yml)

## Change goals in this repo

- Refactor safely (improve structure without behavior regressions).
- Find and fix bugs with reproducible evidence.
- Build features that fit existing module/package boundaries.

## Agent workflow

1. Read `src/main/docs/README.md` and the linked docs for task context.
2. Identify affected module(s) and dependency order in `MandoMC.buildModules()`.
3. Verify lifecycle safety: anything created in `enable()` is cleaned in `disable()`.
4. For commands, keep `plugin.yml`, Java registration, and permission checks aligned.
5. For config changes, add defaults in resources and wire typed config in `ConfigModule`.
6. Validate with tests (`mvn -q test`) and include manual smoke-test notes for in-server behavior.
7. Keep guardrail tests updated when touching command wiring or lifecycle (`PluginYmlConsistencyTest`, `SchedulerLifecycleStopTest`, `LinkCommandTest`).

## Bugfix protocol

- Capture symptom -> root cause -> fix path.
- Prefer the smallest fix that addresses cause, not only symptom.
- Add/adjust tests when practical; if not feasible, state why and provide manual verification steps.

## Refactor protocol

- Avoid mixing broad refactors with unrelated bugfixes/features in one change.
- Preserve external behavior (commands, permissions, config keys) unless change is explicit and documented.
- Remove dead code only when references are confirmed absent.

## Feature protocol

- Place code in the correct package (`core`, `gameplay`, `server`, `world`).
- Keep optional plugin integrations guarded (`softdepend`-aware).
- Ensure reload behavior is sane (`/mmcreload` should not leak listeners/tasks/services).

## Build and verify

- `mvn -q test`
- `mvn package` -> `target/MandoMC.jar`
- `mvn -q -DskipTests compile` (compile-only fallback when package copy target is environment-specific)
