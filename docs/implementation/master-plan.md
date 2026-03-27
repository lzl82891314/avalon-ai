# Avalon AI Master Plan

## Scope
- Active milestone: M5
- Pending milestones: none for V1

## Work Items
| ID | Milestone | Status | Owner | Write Scope | Depends On | Acceptance |
| --- | --- | --- | --- | --- | --- | --- |
| M1-WI-001 | M1 | DONE | coordinator | root pom, module pom, docs | none | multi-module skeleton exists and root build is coherent |
| M1-WI-002 | M1 | DONE | worker-core | avalon-core/** | M1-WI-001 | domain enums, models, actions, interfaces compile |
| M1-WI-003 | M1 | DONE | worker-config | avalon-config/**, resources rules/roles/setups | M1-WI-001 | YAML config loads and invalid config fails fast |
| M1-WI-004 | M1 | DONE | worker-runtime | avalon-runtime/**, avalon-testkit/** | M1-WI-001, M1-WI-002 | runtime skeleton and scripted fixtures compile |
| M1-WI-005 | M1 | DONE | worker-shell | avalon-agent/**, avalon-persistence/**, avalon-api/**, avalon-app/** | M1-WI-001 | non-M1 modules compile with safe placeholders |
| M1-WI-006 | M1 | DONE | worker-core | avalon-core/** | M1-WI-002, M1-WI-003 | role assignment and visibility rules implemented |
| M1-WI-007 | M1 | DONE | worker-runtime | avalon-runtime/**, avalon-testkit/** | M1-WI-002, M1-WI-003, M1-WI-004, M1-WI-006 | rule engine, scripted controller, run-to-end work |
| M1-WI-008 | M1 | DONE | coordinator | integration fixes, tests, progress docs | M1-WI-005, M1-WI-007 | compile and milestone tests pass |
| M1-FU-001 | M1 | DONE | coordinator | avalon-runtime/**, avalon-config/** | M1-WI-008 | runtime/config contract models converge onto core before M2 starts |
| M2-WI-001 | M2 | DONE | coordinator | avalon-persistence/**, avalon-app resources, avalon-runtime/** | M1 complete | event store, snapshot baseline, runtime persistence, forced snapshots, and recovery replay land |
| M2-WI-002 | M2 | DONE | coordinator | avalon-runtime/**, avalon-persistence/**, avalon-api/** | M2-WI-001 | recovery and replay queries work end-to-end through API and can resume execution after recovery |
| M3-WI-001 | M3 | DONE | coordinator | avalon-runtime/**, avalon-api/**, avalon-app/**, avalon-testkit/** | M2-WI-001 | REST control surface uses core/config-backed player-view and phase-correct allowed actions, with Spring integration tests covering role-differentiated visibility and V2 API boundaries |
| M3-WI-002 | M3 | DONE | coordinator | avalon-agent/**, avalon-runtime/**, avalon-app/** | M2 complete | prompt builder, parser, retry policy, deterministic fallback gateway, and LLM controller wiring work end-to-end with at least one LLM-controlled seat able to run to terminal state |
| M4-WI-001 | M4 | DONE | coordinator | avalon-agent/**, avalon-app/**, docs/** | M3-WI-002 | provider-routed gateway, OpenAI transport adapter, request/response validation tests, and full-reactor regression land without breaking the default noop LLM path |
| M5-WI-001 | M5 | IN_PROGRESS | coordinator | avalon-app/**, README.md, docs/** | M4-WI-001 | V1 defaults to a usable console operator flow, provider guidance and manual verification checklists are in place, and only explicit real-key smoke remains for close-out |

## Locked Defaults
- Code root: `E:\Rubbish\avalon\avalon-ai`
- Group ID: `com.example.avalon`
- V1 ruleset: classic 5-player only
- Human wait state will be modeled as status, not phase
- Event stream will record state-changing actions and phase transitions; validation failures go to audit later
- Snapshot cadence default: every 10 events, plus first persisted state and terminal state
- LLM retry fallback for later milestones: pause rather than fabricate an action

## Current Outcome
- M1 is complete: multi-module skeleton exists, classic 5-player scripted game runs to end, YAML config loading works, and the full reactor test suite passes.
- `M1-FU-001` is complete: `avalon-config` emits core models and `avalon-runtime` compiles cleanly against the converged contracts.
- `M2-WI-001` is complete: Flyway migration, JPA stores, runtime snapshots, forced snapshots, recovery replay, and runtime recovery tests are in place.
- `M2-WI-002` is complete: `RecoveryService` can rebuild live state from snapshot plus tail events, API-level session eviction now triggers recovery successfully, and `/replay` is projected into a higher-level read model.
- `M3-WI-001` is complete: runtime role assignment, player visibility, and allowed-action derivation now flow from `avalon-core` plus setup-backed role definitions, `player-view` has role-differentiated integration coverage, and `submitPlayerAction` is explicitly closed with HTTP 501 for V2 scope.
- `M3-WI-002` is complete: `avalon-agent` now has prompt assembly, structured action parsing, retry policy, `LlmPlayerController`, runtime resolver wiring, and deterministic gateway-backed Spring integration coverage for an LLM-controlled seat.
- `M4-WI-001` is complete: `AgentTurnRequest` now carries provider/model settings, `RoutingAgentGateway` routes explicit noop separately from the shared OpenAI-compatible Chat Completions adapter, provider routing/request parsing tests landed, and the full reactor `mvn -q test` remains green.
- `M5-WI-001` is in progress: root-level operator documentation and a real-provider smoke checklist now exist, successful LLM turns now persist `audit_record` rows through runtime persistence, invalid LLM output now pauses the game safely instead of crashing the request, paused runs are projected in `/replay` and recover cleanly after session eviction, console mode is now the default startup path with an interactive in-terminal operator loop, and the remaining V1 close-out work is manual real-key verification when credentials are available.
