# Cross-repo Bridge V1 — FlorisBoard ↔ RafGitTools ↔ RafPolimata

## Role
FlorisBoard is the Android/IME surface of the bridge. RafGitTools owns transport/provenance; RafPolimata owns derived analysis/ontology.

## Privacy invariant
No typed text, clipboard contents, credentials, or personal payload crosses the bridge by default.

## Evidence contract
A bridge artifact SHOULD contain only technical metadata required for reproducibility:

- repository
- path
- commit_sha
- content_hash
- media_type
- build_variant
- abi
- android_api
- test_result
- provenance_state
- claim_allowed

Unknown or unobserved fields MUST remain `TOKEN_VAZIO`. Claims without a reproducible receipt MUST use `claim_allowed=false`.

## Initial scope
1. Build metadata and deterministic receipts.
2. Android/ABI compatibility evidence.
3. Test and runtime result references.
4. Manifest ingestion by RafGitTools.
5. Derived graph/view ingestion by RafPolimata.

## Out of scope by default
- typed user content
- clipboard contents
- contacts
- credentials
- hidden telemetry

## Expected first artifact
A local JSON/JSONL manifest containing build/runtime metadata only, plus a test proving that no user text payload is emitted.

## Related work
- `rafaelmeloreisnovo/RafGitTools#357`
- `rafaelmeloreisnovo/RafPolimata#298`

State: DESIGN_V1
Runtime evidence: TOKEN_VAZIO
claim_allowed: false
