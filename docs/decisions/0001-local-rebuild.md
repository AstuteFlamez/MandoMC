# 0001: Local rebuild and gameplay ownership
Date: 2026-11-05
Status: accepted direction and ownership; exact dependency compatibility requires validation.

## Decision
Create a clean laptop development foundation in the existing Git history. Preserve previous main and isolate source/config/tests in recovery branches. Build playable features from main and merge when satisfactory.

Use one modular custom plugin and the public components in architecture.md. Design player behavior first, then order, parameterized implementation and configured content.

## Consequences
Old active modules and documentation leave main but remain in history and the complete snapshot. Recovery files require adaptation. Keep one state owner and isolate vendor integrations for future replacement.

## Revisit when
A public component cannot support required behavior, updates make it unsustainable, or independent deployment becomes necessary.
