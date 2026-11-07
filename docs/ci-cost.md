# CI cost

## Runner minutes

- Cancel superseded push and pull-request check runs where the workflow is read-only and its tests do not require serialisation.
- Keep publish and serialised acceptance runs uncancelled.
- Use manual or scheduled runs only when they are needed.

## Transfer

- Avoid re-downloading unchanged sources, toolchains and emulator images.
- Cache only inputs keyed on their exact revision or hash, and keep caches out of publication artifacts.

## Storage

- Set an explicit short `retention-days` on workflow artifacts.
- Do not keep intermediate build outputs as artifacts.
- Keep only sanitised results needed as evidence.
