# Testing and development-process improvements

Scope: the 14 approved FW/EN improvements only. Driving-feature implementation and category completion are separate. Preserve historical tests and truthful gaps.

| ID | Enforced process | Executable implementation |
|---|---|---|
| FW-01 | Current exact-suite/app evidence; expired or unrelated artifacts cannot qualify | private `check_drivemate_tests.py`, public `test_release_gate.py` |
| FW-02 | Missing, misnamed, unmapped and duplicate tests cannot silently pass | `collect_results.py`, `test_collect_results.py` |
| FW-03 | Frozen fixture changes trigger source comparison; live and frozen audits remain distinct | source audit workflow, `verify_frozen_lanes.py`, archived source digests |
| FW-04 | All 23 current categories and related batches, preserving historical IDs | `programme.json`, `run_summary.py`, `run_report.py` |
| FW-05 | Actual published fixture -> production loader/parser -> free-driving session -> visible lane strip; stage and screenshot fingerprints required | `PublishedLanePipelineScreenTest.kt`, collector sidecar gate |
| EN-01 | Prerequisites backed by observed test imports and production source digests; unobserved/transitive relationships remain explicit | `dependency_graph.py` |
| EN-02 | Separate law/decision/screen tests and stable failure/incomplete reason codes | collector and `process_contract.py` |
| EN-03 | Six related batches; partial artifacts never authorize a full release | `test_selection.py`, workflow `batch` input |
| EN-04 | Correct/total, failed, unknown, provisional and skipped remain separate | structured and HTML programme reports |
| EN-05 | Identical frozen test/input/map IDs across pretest/retest; hashed logs consumed by CI | `validate_retest`, `process_preflight.retest_records`, `evidence/retests` |
| EN-06 | Dated independent official-source URL and document digest required for legal claims; otherwise unknown | `independent_truth`, acceptance contract |
| EN-07 | Identical route/case input, mode and denominator; provider map versions disclosed; incomplete coverage gets no score/rank | `validate_benchmark`, process report |
| EN-08 | Safety blockers separate from quality findings and component CI; unresolved P0 blocks signing in both release paths | `release-findings.json`, exact-suite release gate |
| EN-09 | Reuse frozen inputs/results and Gradle cache; related batches, no emulator when no screens, no duplicate equivalent dispatch | selection and workflows; bounded summary artifacts |

## Working rules

Reproduce the specific fault with a public regression, freeze the test and data, change the smallest relevant code, run the identical retest, then run wider checks only where affected. Retain concise hashed RED/GREEN evidence. Reuse previously verified unaffected checks. Always inspect the exact commit's terminal CI result before integration.

Source availability, component correctness, source-to-screen replay, independent legal truth and physical driving are different evidence levels. The new source pipeline replay uses genuine Great Ancoats Street geometry and tags, with no route-supplied arrows. It deliberately uses the production free-driving path rather than manufacturing a junction. It does not claim a real junction, online download acceptance, current legal markings or a complete drive. Archived samples are original source observations; the complete original square is not archived here.

The pipeline verdict uses `controlled_cache`: original fixture bytes enter the production loader read boundary. Network is not isolated; this does not establish offline downloader availability or online/offline parity, and it cannot be ranked as an online/offline engine benchmark.

Only complete comparable benchmark records may rank. Unknown/provisional/skipped coverage remains incomplete. The framework does not invent competitors' results, legal truth or absent acceptance records. The process report records absent truth/benchmark inputs as missing evidence. An import graph is not an exhaustive runtime architecture graph.

An open safety issue blocks release without falsifying passing component tests. Signed builds remain manual and require the established signing approval. This change does not request a signed APK.

## Follow-on: benchmark ranking integrity regression

A benchmark observation may have a valid 0–10 score without qualifying as a **ranking**. Rank 1 is not awarded just because a single engine has a complete result: at least two **distinct, complete, comparable** engine records are required. Engine names must be nonblank and unique after trimming/case-folding (so `DMN` and ` dmn ` cannot masquerade as opponents). Incomplete engines remain unranked, with unknown/provisional/skipped kept visible. This guard does not constitute an engine performance comparison.

Frozen public regressions: `ci/test_benchmark_ranking_integrity.py`, added before the production-validator change as commit `16ef28bf62577706e679366f5406b06d364a01bf`. The red baseline is [run 40595840271](https://github.com/phuk-prog/drivemate-tests/actions/runs/40595840271): 60 Python checks, 6 expected failed assertions (including three missing-name subcases). Corrections are in PR [#28](https://github.com/phuk-prog/drivemate-tests/pull/28). Green verification and full app/emulator results must be checked at the final PR SHA before merge or claiming completion. These synthetic *validator* fixtures are not simulated UK roads and provide no legal truth or route-quality scores.

## Category 1 component-reporting evidence (28 October 2026)

The previously unassigned production `UkJoinTest` and `PublishedRegionCatalogTest` are now attributed to **current Category 1** via an explicit, reviewed `programme.json.component_category_map`. This is a **partition**, not an extra test run or double count; unlisted app-unit classes remain unassigned. Legacy `cat01` / `1 Turn-by-turn guidance` results continue to map to **current Category 7**, not Category 1. A failed, unknown, provisional or skipped mapped test retains its actual status.

These checks cover UK-join manifest/pointer/asset selection and published-region parsing/integrity on production classes, using bounded synthetic component fixtures; they do **not** prove live UK coverage, publisher delivery, download/offline availability, road-law truth, full source-to-screen navigation or safe release. Programme Category 1 retains `acceptance: open` and `evidence_scope: component_checks_only` even when its component checks pass. A future standalone source-backed acceptance ledger and actual independent evidence must be verified before closing Category 1.

Frozen RED tests: `ci/test_category1_component_reporting.py` introduced at `73a728a0267a2069f4b17bf78856d355b75f7b85`, [run 40640959581](https://github.com/phuk-prog/drivemate-tests/actions/runs/40640959581): **61/64 passing**, 3 expected failures in map-component attribution; historical category protection passed. Follow-on implementation is [public PR #35](https://github.com/phuk-prog/drivemate-tests/pull/35). The frozen test file is unchanged for GREEN retest; complete public CI evidence must be recorded before any merge.

## Category 1 offline-evidence contract (28 October 2026)

The existing real-source Great Ancoats Street emulator replay is explicitly `mode=controlled_cache`. Its source→loader→session→LaneRow proof **never validates download, live publication, network isolation or offline parity**. Previously, `validate_acceptance` accepted a record with `mode=offline` and the four successful pipeline stages even if there was **no network- or installed-asset evidence**.

The corrected `validate_offline_proof` requires an offline record to name independently collectable network-isolation probe evidence (blocked before **and** after, controlled method and SHA-256), pinned published catalogue and manifest digests, a successfully verified installed release asset and a post-isolation readback byte digest matching the frozen source. Missing or inconsistent claims fail closed. The validator's `offline_evidence_record_valid` return means **only that the supplied metadata fields are internally consistent**. It **does not independently authenticate** the referenced probe or publication. A future emulator runner must collect signed/hashed probe files, inspect actual release assets, and cross-check their bytes against this ledger before anyone claims real offline navigation acceptance. Legal layout, real junction decision, geographical denominator and full online/offline parity remain independently OPEN.

Frozen public RED regressions: `ci/test_offline_proof_contract.py` first committed `cd6c177da65e8e6e32bba1a839af2ed24e76949d`; [run 40642714611](https://github.com/phuk-prog/drivemate-tests/actions/runs/40642714611) reports **12 expected failed assertions among 74 framework tests**. Fix is isolated in [public PR #36](https://github.com/phuk-prog/drivemate-tests/pull/36), preserving frozen tests unchanged. No navigation code, map publication, signed APK, real download or real-device verification is modified by this contract change.

## Offline receipt cross-check in the public CI process gate

A second independent step now checks actual files saved by a future offline emulator run. A self-described `offline_proof` ledger item is not enough: `ci/process_preflight.py` requires `offline_proof.artifacts` to name five **single-filename** receipts under the ephemeral run's `results/offline-evidence/` folder: `network_probe`, `catalog`, `manifest`, `installed`, `readback`. It hashes the **actual bytes** of each file and compares them to the SHA-256 fields already validated by `validate_offline_proof`. It rejects missing/mutated files, symlink/path traversal, overlarge metadata receipts, mismatched case identity and network-probe logs that report reachability before or after the supposed isolation. The network probe must be schema 1 JSON, identify the same `case_id` and method, and contain `before.network_reachable=false` and `after.network_reachable=false`.

This additional check is exercised by **seven focused public regressions**: a complete five-file fixture, missing file, changed bytes, claimed isolation while still reachable, changed case, symlink/traversal, and **direct integration into the CI process preflight**. They use synthetic metadata-only artifacts to test the verifier; they are not fabricated roads or a claimed offline drive. The files are examined in-run; neither source map assets nor APKs are added to the public artifact upload. Only bounded summary information (`offline_receipts_verified`, `offline_acceptance=not_established_by_receipt_validation`) is reported.

**Limitation:** Byte integrity and internal trace consistency cannot alone authenticate the source of a network log. A real instrumented offline emulator harness must generate those files and establish that the isolated app truly consumed the installed data before physical/offline acceptance can be claimed. Missing real offline records are not converted into green acceptance. Safety release blockers remain separate.

Frozen RED: [public PR #37](https://github.com/phuk-prog/drivemate-tests/pull/37), new regressions at `8d58ef594015f1019f30a4f1d8a8c8d5310229d0`; [run 40644182987](https://github.com/phuk-prog/drivemate-tests/actions/runs/40644182987) **10 expected assertion failures among 81 framework tests**. Final GREEN evidence must be checked at the PR head before merging.

## Automatic emulator-origin environment evidence — Category 1 test process

The existing public Android emulator workflow now runs `ci/emulator_evidence.py` **inside the already-running emulator session, after all screen tests**, avoiding interference with navigation regression tests or extra emulator boots. It reads a previously archived genuine Manchester OSM-derived fixture (not the live published map release), pushes the exact fixture bytes to a dedicated temporary Android test path, reads the **device-computed SHA-256**, disables emulator Wi-Fi/mobile network through ADB airplane mode and `svc`, probes IP/TCP reachability, reads the file back through ADB, probes again, and attempts to restore network connectivity and delete the fixture in a `finally` block.

The bounded `results/emulator-evidence-smoke.json` is uploaded with the existing public reports and checked against the frozen fixture digest by `process_preflight.emulator_smoke_report`. It distinguishes `observed` (baseline actually reachable, both isolated probes unreachable, device installation and readback digests match) from `inconclusive` (unsupported ping/TCP tests, failure to isolate, mismatched bytes, command/restore problems). Missing observations are `not_run`; no fake green result is substituted. ADB probe reachability is **not** exhaustive network-isolation proof.

This is strictly an **emulator environment smoke**, not `mode=offline` acceptance. It neither downloads a real published GitHub asset, loads a production app cache, replays a real-UK offline route nor proves legal lane truth. The output explicitly states `published_release_downloaded=false`, `navigation_tested=false`, `offline_acceptance=false`; `process_preflight` retains these limitations. Authentic released-map and offline route acceptance remain open in later testing improvements.

Frozen RED regressions were added first in [public PR #40](https://github.com/phuk-prog/drivemate-tests/pull/40) at `a85b5e66471800903e6e30c304b804e22df5cab0`. [RED run 40647008187](https://github.com/phuk-prog/drivemate-tests/actions/runs/40647008187): **81/88 Python checks passed, seven expected failures** for absent generator and workflow integration. The seven new tests remain unchanged for the GREEN retest. Do not merge or claim CI verification until the exact final PR SHA passes the existing full test workflow.

**Emulator-runner integration regression (28 October 2026):** the first integrated [run 40647295669](https://github.com/phuk-prog/drivemate-tests/actions/runs/40647295669) executed **88/88 Python framework checks and 290/290 app/emulator tests**, but the overall workflow **FAILED** because the emulator action executes each `script` line separately; its backslash-continued Python command lost `--source` and `--out`. This was a test-harness invocation failure, **not** a navigation app failure. The command is now one physical line and `ci/test_emulator_workflow_command.py` guards against future split arguments. No complete automatic-emulator-evidence success or green full-suite result is claimed until the corrected final PR head finishes.
