# MandoMC Agent Docs

This folder is the docs-first entrypoint for AI/code agents working in this repo.

## Read order

1. `src/main/docs/architecture.md`
2. `src/main/docs/development-workflow.md`
3. `src/main/docs/testing-and-validation.md`
4. `src/main/docs/bug-radar.md`

## Purpose

- Reduce onboarding time for refactors, bugfixes, and feature work.
- Keep lifecycle and reload behavior safe in a modular Paper plugin.
- Provide concrete checks that prevent common regressions.

## Source of truth

- Runtime/module lifecycle: `src/main/java/net/mandomc/MandoMC.java`
- Module contract: `src/main/java/net/mandomc/core/module/Module.java`
- Service wiring: `src/main/java/net/mandomc/core/services/ServiceRegistry.java`
- Commands and permissions: `src/main/resources/plugin.yml` and `src/main/java/net/mandomc/core/modules/core/CommandModule.java`

## Recent refactor anchors

- Service seams: `src/main/java/net/mandomc/server/discord/service/` and `src/main/java/net/mandomc/core/services/EconomyService.java`
- Lifecycle stop semantics: lottery and parkour scheduler helpers under `src/main/java/net/mandomc/gameplay/lottery/task/` and `src/main/java/net/mandomc/world/ilum/`
- Guardrail tests: `src/test/java/net/mandomc/core/config/PluginYmlConsistencyTest.java`, `src/test/java/net/mandomc/core/lifecycle/SchedulerLifecycleStopTest.java`, `src/test/java/net/mandomc/core/commands/LinkCommandTest.java`
