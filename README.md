# MandoMC

Paper plugin for the MandoMC server.

## Agent and contributor docs

- Start here: [`src/main/docs/README.md`](src/main/docs/README.md)
- Architecture: [`src/main/docs/architecture.md`](src/main/docs/architecture.md)
- Workflow: [`src/main/docs/development-workflow.md`](src/main/docs/development-workflow.md)
- Testing: [`src/main/docs/testing-and-validation.md`](src/main/docs/testing-and-validation.md)
- Bug hotspots: [`src/main/docs/bug-radar.md`](src/main/docs/bug-radar.md)

## Build

- `mvn -q test`
- `mvn package` -> `target/MandoMC.jar`
- `mvn -q -DskipTests compile` (compile-only fallback when local package copy step is unavailable)

## Refactor status notes

- Command and permission consistency checks are now covered by automated tests.
- Core reload safety has been improved for lottery and parkour repeating tasks.
- Link/economy command flow now uses injectable service seams for better unit testing.
