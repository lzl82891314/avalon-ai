# Compound Learnings 2026-04-01 OpenAI-Compatible Transport Reliability

- Retry ownership should stay in the transport layer for OpenAI-compatible providers. If transport retries and validation retries both perform additional network calls, one intermittent outage turns into duplicated prompts, longer stalls, and noisier audit records.
- Failure auditing must preserve transport root cause fields all the way into persisted audit JSON. `failureKind`, `transportAttempts`, `timeoutMs`, `statusCode`, `rootExceptionClass`, and `rootExceptionMessage` are the minimum useful diagnostics when a game pauses on provider failures.
- Shared timeout policy must be centralized and provider-aware. Probe flows and runtime turn execution need to use the same timeout resolver, otherwise a successful probe can misrepresent real in-game behavior.
- Integration tests that depend on model-profile secret lookup must explicitly isolate the secrets file path. A real root-level `avalon-model-profile-secrets.yml` can silently contaminate test results unless the test overrides that location.
