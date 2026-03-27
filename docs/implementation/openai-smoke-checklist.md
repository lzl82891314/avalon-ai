# OpenAI Smoke Checklist

Use this checklist when you want to verify the real OpenAI provider path locally. This is intentionally manual and is not part of the automated `mvn -q test` suite.

## Preconditions

- a valid credential source exists for the selected profile, for example `providerOptions.apiKey`, `providerOptions.apiKeyEnv`, or `OPENAI_API_KEY`
- an enabled OpenAI-backed catalog model profile exists, for example static `openai-gpt-5.4`
- the default static `openai-gpt-5.4` profile targets `https://openrouter.ai/api/v1`
- the default static `openai-gpt-5.4` profile expects `OPENROUTER_API_KEY`
- The shared OpenAI-compatible gateway currently accepts `provider=openai|minimax|glm|claude|qwen`; this checklist focuses on the `openai` profile path
- JDK 21 and Maven are installed
- `mvn -q test` already passes locally
- Use console mode with `mvn -f avalon-app/pom.xml spring-boot:run` if you want to verify the OpenAI path interactively in the terminal
- Use server mode with `mvn -f avalon-app/pom.xml spring-boot:run -Dspring-boot.run.arguments=--avalon.mode=server` if you want to run the HTTP scenarios below

## Scenario 1: Console Smoke

Start the app in default console mode:

```bash
mvn -f avalon-app/pom.xml spring-boot:run
```

Then in the terminal:

```text
new
<enter>
<enter>
99
role
openai-gpt-5.4
openai-gpt-5.4
openai-gpt-5.4
openai-gpt-5.4
openai-gpt-5.4
run
```

Expected outcome:

- The console wizard lists enabled catalog model profiles and binds roles to `modelId`
- The terminal prints every public speech, proposal, vote, mission result, pause, and winner directly during `run`
- If credentials are missing or the model output is invalid, the game moves to `PAUSED` and the terminal can inspect the failure through `audit`
- Run `probe-model openai-gpt-5.4 structured` before `new/run` if you want a quick structured-output compatibility check

## Scenario 2: One OpenAI Seat Over HTTP

Create one LLM seat and keep the other four scripted:

```bash
curl -X POST http://localhost:8080/games \
  -H "Content-Type: application/json" \
  -d '{
    "ruleSetId": "avalon-classic-5p-v1",
    "setupTemplateId": "classic-5p-v1",
    "seed": 99,
    "players": [
      {
        "seatNo": 1,
        "displayName": "P1",
        "controllerType": "LLM",
        "agentConfig": {"outputSchemaVersion": "v1"}
      },
      {"seatNo": 2, "displayName": "P2", "controllerType": "SCRIPTED"},
      {"seatNo": 3, "displayName": "P3", "controllerType": "SCRIPTED"},
      {"seatNo": 4, "displayName": "P4", "controllerType": "SCRIPTED"},
      {"seatNo": 5, "displayName": "P5", "controllerType": "SCRIPTED"}
    ],
    "llmSelection": {
      "mode": "ROLE_BINDING",
      "roleBindings": {
        "MERLIN": "openai-gpt-5.4",
        "PERCIVAL": "openai-gpt-5.4",
        "LOYAL_SERVANT": "openai-gpt-5.4",
        "MORGANA": "openai-gpt-5.4",
        "ASSASSIN": "openai-gpt-5.4"
      }
    }
  }'
```

Capture `gameId`, then run:

```bash
curl -X POST http://localhost:8080/games/{gameId}/run
curl http://localhost:8080/games/{gameId}/state
curl http://localhost:8080/games/{gameId}/replay
```

Expected outcome:

- `POST /run` returns `status=ENDED`
- `GET /state` returns terminal state for the same `gameId`
- `GET /replay` contains a full event timeline for the finished game

## Scenario 3: Five OpenAI Seats Over HTTP

Repeat the create request, but set all five seats to `controllerType=LLM` and keep the same role binding, or switch to `RANDOM_POOL` with five distinct OpenAI-backed `modelId`s.

Expected outcome:

- The game still reaches `status=ENDED`
- No seat silently falls back to noop when `provider=openai` is explicitly set
- Failures, if any, are explicit and attributable to schema/output/provider issues

## Failure Signals To Watch

- `OpenAI-compatible provider 'openai' requires apiKey...`
  This means the selected catalog model profile opted into OpenAI but no credential source was available.
- `OpenAI-compatible request failed with status ...`
  This usually means invalid credentials, unsupported parameters, or a provider-side rejection.
- `Agent turn validation failed after 2 attempts`
  The model responded, but the structured action did not pass runtime validation.
- `OpenAI-compatible assistant content was not valid JSON`
  The model ignored the requested response contract and the parser rejected the output.
- `OpenAI-compatible response body was not valid JSON ...`
  This usually means `baseUrl` points at a landing page or non-API endpoint. A common example is forgetting the `/v1` prefix on an OpenAI-compatible gateway.

## Notes

- Leaving `provider` empty or omitting `modelProfile.provider` still uses deterministic noop fallback on the legacy inline path.
- The static `openai-gpt-5.4` profile intentionally uses `https://openrouter.ai/api/v1`. Keep `baseUrl` at the API root; do not point it directly at `/chat/completions`.
- `GET /games/{gameId}/audit` is now a useful inspection surface for both successful and failed LLM turns, but the real pass/fail criterion for this checklist is still whether the configured OpenAI seats can complete or explicitly pause with attributable diagnostics.

