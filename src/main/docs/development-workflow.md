# Development Workflow (Agent-Focused)

## 1) Refactors

- Preserve behavior first; separate structural cleanup from behavior changes.
- Keep diffs scoped to one subsystem when possible.
- Validate module order assumptions before moving module wiring.

## 2) Bug fixes

- Capture reproducible symptom and trigger path.
- Identify root cause before patching.
- Prefer smallest safe fix that addresses cause, not only symptom.

## 3) New features

- Choose correct module/package boundary before implementation.
- Wire dependencies through `ServiceRegistry`.
- Ensure command/config/resource changes are reflected consistently.

## Mandatory consistency checks

- `plugin.yml` command key == Java registration key (exact case match)
- permission key in `plugin.yml` == `hasPermission(...)` checks
- resources defaults exist for new configs
- reload safety maintained for listeners/tasks/services

## Deliverable checklist

- Explain what changed and why.
- Note risks/assumptions.
- Include test results (`mvn -q test`) and manual smoke-test suggestions.
