# OpenAI Smoke Checklist

Use this checklist when you want to verify the real OpenAI provider path locally. This is intentionally manual and is not part of the automated `mvn -q test` suite.

## Preconditions

- a valid credential source exists for the selected profile, for example root-level `avalon-model-profile-secrets.yml`, `--avalon.model-profile-api-keys.gpt-5.4=...`, `AVALON_MODEL_PROFILE_API_KEY_GPT_5_4`, `providerOptions.apiKey`, `providerOptions.apiKeyEnv`, or `OPENAI_API_KEY`
- an enabled OpenAI-backed catalog model profile exists, for example static `gpt-5.4`
- the default static `gpt-5.4` profile targets `https://gcapi.cn/v1`
- the default static catalog profiles no longer store `providerOptions.apiKey` in source control
- The shared OpenAI-compatible gateway currently accepts `provider=openai|minimax|glm|claude|qwen`; this checklist focuses on the `openai` profile path
- The shared transport now retries retryable provider failures up to 3 total attempts: `429/500/502/503/504`, plus timeout/connect/io exceptions, with `500ms` and `1500ms` backoff
- Default timeout is `30s` for most providers and `60s` for `minimax|glm|claude|qwen`; explicit `providerOptions.timeoutMillis` still wins
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
gpt-5.4
gpt-5.4
gpt-5.4
gpt-5.4
gpt-5.4
run
```

Expected outcome:

- The console wizard lists enabled catalog model profiles and binds roles to `modelId`
- The terminal prints every public speech, proposal, vote, mission result, pause, and winner directly during `run`
- If credentials are missing, transport fails after bounded retries, or the model output is invalid, the game moves to `PAUSED` and the terminal can inspect the failure through `audit`
- Run `probe-model <modelId> structured` before `new/run` if you want a quick connectivity and structured-output compatibility check on the same timeout policy used at runtime

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
        "MERLIN": "gpt-5.4",
        "PERCIVAL": "gpt-5.4",
        "LOYAL_SERVANT": "gpt-5.4",
        "MORGANA": "gpt-5.4",
        "ASSASSIN": "gpt-5.4"
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

- `OpenAI-compatible provider 'openai' requires an API key ...`
  This means the selected catalog model profile opted into OpenAI but no credential source was available.
- `OpenAI-compatible request failed with status ...`
  This usually means invalid credentials, unsupported parameters, or a provider-side rejection.
- `OpenAI-compatible HTTP transport failed after ... attempts`
  This means the request never reached a usable JSON response after bounded retries. Inspect `audit` for `failureKind`, `transportAttempts`, `timeoutMs`, `statusCode`, `rootExceptionClass`, and `rootExceptionMessage`.
- `Agent turn validation failed after 2 attempts`
  The model responded, but the structured action did not pass runtime validation. Transport failures no longer trigger this outer corrective retry path.
- `OpenAI-compatible assistant content was not valid JSON`
  The model ignored the requested response contract and the parser rejected the output.
- `OpenAI-compatible response body was not valid JSON ...`
  This usually means `baseUrl` points at a landing page or non-API endpoint. A common example is forgetting the `/v1` prefix on an OpenAI-compatible gateway.

## Notes

- Leaving `provider` empty or omitting `modelProfile.provider` still uses deterministic noop fallback on the legacy inline path.
- The static `gpt-5.4` profile intentionally uses `https://gcapi.cn/v1`. Keep `baseUrl` at the API root; do not point it directly at `/chat/completions`.
- `GET /games/{gameId}/audit` is now a useful inspection surface for both successful and failed LLM turns, including transport diagnostics, but the real pass/fail criterion for this checklist is still whether the configured OpenAI seats can complete or explicitly pause with attributable diagnostics.

