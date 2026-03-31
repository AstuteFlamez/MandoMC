# Architecture Guide

## High-level model

`MandoMC` is a modular Paper plugin. The main class builds an ordered module list and controls lifecycle:

- Enable order: forward (`buildModules()` order)
- Disable order: reverse
- Reload (`/mmcreload`): disable all -> rebuild module graph -> enable all

This means dependency order between modules is intentional and must be preserved.

## Core patterns

- Modules implement `Module` with `enable(ServiceRegistry)` and `disable()`.
- Shared dependencies are passed through `ServiceRegistry`.
- Prefer registry injection over new static mutable singletons.

## Lifecycle safety

Anything created in `enable()` should be cleaned in `disable()`:

- listeners
- scheduled tasks
- temporary registries/caches
- external handles (DB/repository/service objects)

If this symmetry is broken, `/mmcreload` will eventually leak state.

## Package boundaries

- `net.mandomc.core.*`: infra/framework concerns
- `net.mandomc.gameplay.*`: gameplay systems
- `net.mandomc.server.*`: server/admin systems
- `net.mandomc.world.*`: world-specific systems

When adding a feature, decide package ownership first to avoid architecture drift.
