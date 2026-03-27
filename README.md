# Avalon AI

Avalon AI is a multi-module engine for running classic 5-player Avalon with scripted or LLM-controlled seats. The current V1 scope is now console-first: you can create a game, run it, inspect state, inspect player views, review replay/audit output, and watch the full transcript directly in the terminal without manually driving HTTP requests. The REST surface still exists, but it is now an explicit server mode instead of the default operator path.

## Modules

- `avalon-core`: domain model, actions, rule contracts, role visibility truth
- `avalon-config`: YAML-backed rules, roles, and setup loading
- `avalon-runtime`: orchestration, player resolution, turn context building, run-to-end flow
- `avalon-agent`: prompt building, structured parsing, retry policy, noop/OpenAI-compatible gateways
- `avalon-persistence`: JPA entities, repositories, stores
- `avalon-api`: REST DTOs and application service boundary
- `avalon-app`: Spring Boot entrypoint, Flyway, H2-backed runtime wiring
- `avalon-testkit`: end-to-end scripted tests

## Requirements

- JDK 21
- Maven 3.9+

## Build And Test

```bash
mvn -q test
```

## Run The App

```bash
mvn -f avalon-app/pom.xml spring-boot:run
```

This default launch path starts the interactive console mode. The terminal now supports:

- guided game creation through `new`
- `scripted`, deterministic noop-LLM, or model-pool-backed LLM seats
- full transcript output for public speeches, team proposals, votes, mission actions, paused states, and final winner
- `state`, `player`, `players`, `events`, `replay`, and `audit` inspection commands
- `step` and `run` flows without leaving the console

Typical default model-pool flow:

```text
new
<enter>
<enter>
<enter>
<enter>
<enter>
<enter>
<enter>
<enter>
<enter>
run
exit
```

This accepts the default `role` preset, creates five model-pool LLM seats, and accepts the default role-to-model bindings shown by the console.

If you want the HTTP operator flow, start the app in explicit server mode:

```bash
mvn -f avalon-app/pom.xml spring-boot:run -Dspring-boot.run.arguments=--avalon.mode=server
```

Server mode exposes the local endpoints below:

- API base: `http://localhost:8080`
- H2 console: `http://localhost:8080/h2-console`
- JDBC URL: `jdbc:h2:mem:avalon;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- Username: `sa`
- Password: empty

## Optional REST Quickstart

Create a classic 5-player game with scripted seats:

```bash
curl -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{
    "ruleSetId": "avalon-classic-5p-v1",
    "setupTemplateId": "classic-5p-v1",
    "seed": 42,
    "players": [
      {"seatNo": 1, "displayName": "P1", "controllerType": "SCRIPTED"},
      {"seatNo": 2, "displayName": "P2", "controllerType": "SCRIPTED"},
      {"seatNo": 3, "displayName": "P3", "controllerType": "SCRIPTED"},
      {"seatNo": 4, "displayName": "P4", "controllerType": "SCRIPTED"},
      {"seatNo": 5, "displayName": "P5", "controllerType": "SCRIPTED"}
    ]
  }'
```

Then drive the game:

```bash
curl -X POST http://localhost:8080/games/{gameId}/start
curl http://localhost:8080/games/{gameId}/state
curl http://localhost:8080/games/{gameId}/players/P1/view
curl -X POST http://localhost:8080/games/{gameId}/run
curl http://localhost:8080/games/{gameId}/replay
curl http://localhost:8080/games/{gameId}/audit
```

Human action submission remains out of V1 scope. `POST /games/{gameId}/players/{playerId}/actions` currently returns `501 Not Implemented`.

Successful LLM turns are now persisted into `audit_record` and exposed through `GET /games/{gameId}/audit`.
If an LLM seat keeps returning invalid output after retries, the game now moves to `PAUSED` instead of failing the whole request with HTTP 500, and the failure details are also written to `audit_record`.
That paused transition is also visible in `GET /games/{gameId}/replay`, and the paused status survives recovery after session eviction.

## LLM Seats

LLM model configuration now lives in a unified model catalog instead of being entered inline for each game request.

Catalog sources:

- static YAML files under `avalon-app/src/main/resources/model-profiles`
- managed records exposed through `GET/POST/PUT/DELETE /model-profiles`

The console wizard now references this catalog by `modelId`. For HTTP game creation, the recommended path is:

- keep each LLM player as `controllerType=LLM`
- provide `llmSelection.mode=ROLE_BINDING` to bind roles to catalog `modelId`s
- or provide `llmSelection.mode=RANDOM_POOL` with a candidate `modelId` pool

Example: role-bound model selection

```json
{
  "ruleSetId": "avalon-classic-5p-v1",
  "setupTemplateId": "classic-5p-v1",
  "seed": 42,
  "players": [
    {"seatNo": 1, "displayName": "P1", "controllerType": "LLM", "agentConfig": {"outputSchemaVersion": "v1"}},
    {"seatNo": 2, "displayName": "P2", "controllerType": "SCRIPTED"},
    {"seatNo": 3, "displayName": "P3", "controllerType": "SCRIPTED"},
    {"seatNo": 4, "displayName": "P4", "controllerType": "SCRIPTED"},
    {"seatNo": 5, "displayName": "P5", "controllerType": "SCRIPTED"}
  ],
  "llmSelection": {
    "mode": "ROLE_BINDING",
    "roleBindings": {
      "MERLIN": "openai-gpt-5.4",
      "PERCIVAL": "claude-compatible-template",
      "LOYAL_SERVANT": "glm-compatible-template",
      "MORGANA": "qwen-compatible-template",
      "ASSASSIN": "minimax-m2.7"
    }
  }
}
```

Example: random model pool

```json
{
  "llmSelection": {
    "mode": "RANDOM_POOL",
    "candidateModelIds": [
      "openai-gpt-5.4",
      "claude-compatible-template",
      "glm-compatible-template",
      "qwen-compatible-template",
      "minimax-m2.7"
    ]
  }
}
```

When a game starts, role assignment happens first and the runtime then resolves the effective model profile for each LLM seat once. That resolved configuration is frozen into the game snapshot so recovery does not drift if the catalog changes later.

The compatible template profiles use their real provider ids (`minimax`, `glm`, `claude`, `qwen`) and are currently routed through the shared OpenAI-compatible gateway using `baseUrl`, `modelName`, and provider credentials.
Enable or adjust each template only after confirming the upstream model name, base URL, and credential source you want to use.

Legacy inline `agentConfig.modelProfile` requests are still accepted for backward compatibility, but they are no longer the default operator path.

For a manual real-provider verification flow, first run `probe-model openai-gpt-5.4 structured` in console mode, then see [docs/implementation/openai-smoke-checklist.md](docs/implementation/openai-smoke-checklist.md).

## Current Limits

- V1 interaction is now console-first, but live human action submission is still deferred to V2.
- The default console path now uses catalog-backed model selection instead of entering raw provider settings inline.
- Automated tests cover provider routing and OpenAI-compatible request/response mapping without making network calls.
- Manual smoke tests against the real OpenAI API still require valid credentials and are not part of `mvn -q test`.
- V2 items such as human wait states and live action submission are still deferred.

