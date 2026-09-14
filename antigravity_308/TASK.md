# Antigravity #308 synthetic coding benchmark

This directory is intentionally synthetic/public and is isolated from the private Job Search Automation repository.

## Defect

`ExpiryCache.get()` currently returns a stale value at the exact expiry boundary.

Required behavior:

- before `expires_at`: return the stored value;
- at `expires_at`: return `None` and remove the key;
- after `expires_at`: return `None` and remove the key;
- a zero TTL is therefore expired immediately.

## Required agent work

1. Fix the defect in `expiry_cache.py`.
2. Add a focused standard-library `unittest` regression suite under `tests/`.
3. Cover at minimum: before-boundary, exact-boundary, after-boundary, and zero-TTL behavior.
4. Do not modify files outside this `antigravity_308` directory.
5. Do not execute shell/terminal commands, network/browser actions, URLs, or MCP. A trusted outer harness performs deterministic validation and GitHub handoff after the model stops.

Acceptance requires a non-empty source/test diff and green trusted validation.
