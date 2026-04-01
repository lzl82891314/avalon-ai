# Compound Learnings 2026-03-31 Model Profile Secret Management

- Source-controlled static `model-profiles/` must not contain live `providerOptions.apiKey` values. Enforce that at config validation time so accidental secret commits fail fast during startup and tests.
- Secret lookup for catalog-backed model profiles should be centralized in one resolver that understands file-based secrets, startup properties, dedicated environment variables, and legacy fallbacks. Duplicating that lookup inside each gateway or probe path guarantees drift.
- For operator ergonomics, static catalog profiles should keep stable non-secret metadata only, while secret material lives in a root-level gitignored file keyed by `modelId`. That keeps Git history clean without forcing every operator flow to use bespoke environment variable names.
- If runtime behavior depends on a gitignored root-level file, the repo must ship both a checked-in example file and explicit tests/docs that distinguish the example from the real local file. Verifying only startup-property fallback is not enough; add an integration path that proves file-backed secrets work for a real static profile.
