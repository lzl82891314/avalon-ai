# Avalon AI Progress

## Current Focus
- Active milestone: M5
- Active work items: M5-WI-001

## Status Board
| ID | Status | Notes |
| --- | --- | --- |
| M1-WI-001 | COMPLETED | root skeleton, module pom files, and execution docs created |
| M1-WI-002 | COMPLETED | avalon-core domain models and tests landed |
| M1-WI-003 | COMPLETED | YAML config loading and validation landed |
| M1-WI-004 | COMPLETED | runtime skeleton and scripted fixture support landed |
| M1-WI-005 | COMPLETED | compile-safe skeleton added for agent/api/persistence/app |
| M1-WI-006 | COMPLETED | deterministic role assignment and visibility logic landed in core |
| M1-WI-007 | COMPLETED | classic 5-player scripted run-to-end flow landed in runtime/testkit |
| M1-WI-008 | COMPLETED | full reactor `mvn -q test` passed |
| M1-FU-001 | COMPLETED | runtime/config contract copies were reduced and core is again the primary contract source |
| M2-WI-001 | COMPLETED | persistence stores, migration, snapshot codec, runtime persistence, forced snapshots, and recovery replay baseline landed |
| M2-WI-002 | COMPLETED | replay projection landed, API-level session-eviction recovery smoke landed, and recovered games can continue to terminal state |
| M3-WI-001 | COMPLETED | runtime role assignment, visibility, and allowed actions now come from core/config truth, role-differentiated `player-view` coverage landed, and V2 action submission returns 501 |
| M3-WI-002 | COMPLETED | prompt builder, parser, retry policy, deterministic fallback gateway, LLM controller, resolver wiring, and run-to-terminal integration coverage landed |
| M4-WI-001 | COMPLETED | provider/model request settings, routing gateway, OpenAI transport adapter, request/response mapping tests, and full-reactor regression landed |
| M5-WI-001 | IN_PROGRESS | console-first startup is now the default launch path, `AvalonConsoleRunner` prints the full game transcript and inspection views in-terminal, docs were rewritten around console usage, and the remaining close-out item is manual real-key smoke for explicit OpenAI seats |

## Completion Log
- 2026-03-23: session started, plan accepted, code root initialized, implementation docs created.
- 2026-03-23: M1-WI-005 completed by worker-shell; agent/api/persistence/app compile-safe skeletons added and `mvn -q test` passed.
- 2026-03-23: M1-WI-002 and M1-WI-006 completed by worker-core; classic 5-player domain model, rule engine, role assignment, visibility, and core tests landed.
- 2026-03-23: M1-WI-003 completed by worker-config; YAML loading, registry, config validation, and classic 5-player sample resources landed.
- 2026-03-23: M1-WI-004 and M1-WI-007 completed by worker-runtime; scripted controller, runtime orchestration, test fixtures, and end-to-end scripted game landed.
- 2026-03-23: M1-WI-008 completed by coordinator; full reactor `mvn -q test` passed.
- 2026-03-23: M1-FU-001 completed by coordinator plus worker-config; runtime/config contracts converged further onto core and the full reactor remained green.
- 2026-03-23: M2-WI-001 completed by coordinator plus worker-runtime; Flyway migration, JPA stores, runtime snapshots, forced snapshot policy, recovery replay, and runtime recovery tests landed.
- 2026-03-23: M3-WI-001 entered in-progress state; persistent `GameApplicationService`, `GameController` query surface, Spring Boot wiring fixes, and `GameApiIntegrationTest` landed.
- 2026-03-23: full reactor `mvn -q test` passed again after M2/M3 integration.
- 2026-03-24: M2-WI-002 advanced by worker-runtime; recovery reducer now preserves discussion-phase advancement and `RecoveryServiceTest` proves recover-then-run-to-end against persisted state.
- 2026-03-24: M3-WI-001 advanced by worker-api; `player-view` Spring integration coverage landed in `GameApiIntegrationTest`.
- 2026-03-24: full reactor `mvn -q test` passed again after the new recovery and player-view coverage.
- 2026-03-24: M2-WI-002 completed by coordinator plus workers; API session-eviction recovery smoke test landed, `/replay` was promoted to a projection view with `replayKind/summary`, and full reactor `mvn -q test` remained green.
- 2026-03-24: M3-WI-001 completed by coordinator; `avalon-runtime` now maps into `avalon-core` for role assignment, private visibility, and allowed actions, `GameApiIntegrationTest` now asserts role-differentiated player-view plus `submitPlayerAction` 501 semantics, and the full reactor `mvn -q test` passed again.
- 2026-03-24: M3-WI-002 completed by coordinator; `avalon-agent` gained `PromptBuilder`, `AgentTurnRequestFactory`, `ResponseParser`, `ValidationRetryPolicy`, `LlmPlayerController`, deterministic `NoopAgentGateway` action generation, runtime resolver wiring for per-player LLM config, and a Spring integration test proving one LLM-controlled seat can `run` to terminal state.
- 2026-03-24: M4-WI-001 completed by coordinator; `AgentTurnRequest` now carries provider/model settings, `RoutingAgentGateway` and an OpenAI-compatible Chat Completions adapter landed, supported provider routing/request parsing tests were added, and full-reactor `mvn -q test` passed.
- 2026-03-24: M5-WI-001 started by coordinator; root `README.md` and an OpenAI smoke checklist now document build/run flow, REST quickstart, opt-in provider configuration, and the manual verification path for real-key testing.
- 2026-03-24: M5-WI-001 advanced by coordinator; successful LLM turns now emit internal runtime audit entries that persist into `audit_record`, `GameApiIntegrationTest` now asserts `/audit` is populated by a deterministic LLM seat, and full-reactor `mvn -q test` passed again.
- 2026-03-24: M5-WI-001 advanced again by coordinator; repeated invalid LLM output now becomes a safe `PAUSED` state instead of a 500, failure-path audit records are persisted, `GameApiLlmFailureIntegrationTest` landed, and full-reactor `mvn -q test` passed again.
- 2026-03-24: M5-WI-001 advanced again by coordinator; `/replay` now projects `GAME_PAUSED` into a human-readable paused frame, paused games recover as `PAUSED` after session eviction, rerunning a paused game remains idempotent without duplicating failure audits, and full-reactor `mvn -q test` passed again.
- 2026-03-24: M5-WI-001 advanced again by coordinator; `AvalonApplication` now defaults to console mode, `AvalonConsoleRunner` and `ConsoleTranscriptPrinter` landed, the terminal can create/run/inspect games without manual HTTP, app-level console tests landed, a scripted console smoke was executed through `spring-boot:run`, and `mvn -q -pl avalon-app -am test` passed again.

## Blockers
- No current compile or test blocker.
- No current M3 blocker; the API/control milestone is closed for V1 scope.
- No current M4 blocker; the provider-backed `AgentGateway` path is implemented and covered by unit tests.
- Remaining V1 work is now limited to manual real-provider smoke once credentials are available; the default operator path is already console-first.

## Worker Completion Format
- Work item ID
- Summary
- Files touched
- Tests run
- Remaining gaps
- Recommended next step

## Next Spawn Set
- coordinator for manual real-provider smoke when credentials are available
- optional worker-test for additional console transcript polish if operator feedback changes
