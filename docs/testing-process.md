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

## TEST-FW-AUTO-PROBE-01 — automatic live emulator network observation (28 October 2026)

A new `ci/emulator_network_probe.py` runs **inside the existing public Android emulator workflow**, immediately after its screen tests while ADB is available. It opens TCP from **the Android device**, not from the GitHub host, using `adb shell toybox nc -z -w 3 1.1.1.1 443`. It observes a successful online baseline, disables airplane-mode/mobile/Wi-Fi transport via ADB controls, checks TCP twice while disconnected, attempts unconditional restoration of connectivity and verifies TCP is reachable again. Control-command failure, missing device/tool, pre-existing offline state, remaining connectivity, and failed recovery all result in `inconclusive`, **not a green isolation claim**. TCP reachability rather than toggle state is the evidence.

A bounded on-run `results/emulator-network-probe.json` records method, status/reason, observations and no personal data; the process preflight independently validates its structure, hashes the record, and records only the summary in `process-summary.json`. The raw probe and private map files are **not** added to published artifacts. Existing app/emulator pass counts remain independent; an inconclusive probe is explicitly shown as incomplete technical coverage rather than as a navigation failure or a proven offline result.

**Important limitation:** even `network_isolation_observed` means *transport only*, not that an actual released UK map was installed, loaded by production app navigation or displayed correctly while isolated. This does not create a `mode=offline` acceptance record or upgrade `controlled_cache` evidence. Genuinely downloaded source and navigation output verification remain separate tasks, with legal road truth and J27-03 unresolved.

Frozen public RED: [PR #39](https://github.com/phuk-prog/drivemate-tests/pull/39), test commit `12996b2d33ee5c11f2fab54753637232bbe8a15d`, [run 40646195554](https://github.com/phuk-prog/drivemate-tests/actions/runs/40646195554) **8 expected failures in 89 framework regressions**. Identical frozen suite must pass at the final PR head before integration.

### Actual emulator network-probe compatibility regression

The first genuine public emulator run [40646449135](https://github.com/phuk-prog/drivemate-tests/actions/runs/40646449135) completed **89/89 Python framework and 290/290 Android/emulator tests**, but its actual device TCP probe returned **inconclusive / NO_ONLINE_BASELINE**. This is *not* evidence of network isolation. Investigation identified that API 30/Android 11 toybox netcat compatibility cannot assume `-z` (zero-I/O) mode; an open stdin can also make an otherwise connected probe hang. Two new frozen public regressions [run 40647585056](https://github.com/phuk-prog/drivemate-tests/actions/runs/40647585056) exposed **2 expected failures among 91 Python framework tests**.

The revised device probe uses the supported `-w 3 -q 1` TCP handshake options and `stdin=DEVNULL`; unsupported command output is treated as `PROBE_UNAVAILABLE` rather than a proven disconnected network. The original 8 frozen regression methods remain unchanged. Even when this suite passes, any actual live `inconclusive` observation must remain inconclusive; no offline UK navigation or map installation is inferred from an ADB transport probe.

## Emulator-origin fixture file transport, alongside the existing network probe

The public network probe (merged PR #39) runs once in the existing emulator session. The separate `ci/emulator_fixture_readback.py` runs immediately afterwards, **without repeating airplane-mode or network commands**. It copies frozen archived, OSM-derived UK fixture bytes into a dedicated Android test-only file through ADB, obtains Android's own `sha256sum`, reads the same device file with `adb exec-out cat`, compares the actual readback SHA-256 and removes the file even after error. The runner records `results/emulator-fixture-readback.json` as a bounded receipt; `process_preflight.observed_emulator_fixture_readback` independently validates the report's scope and expected hash from repository-frozen fixture bytes. Real device mismatch, command error or failed cleanup results in `inconclusive`, never a false pass.

The observed file transport **is not** an Android regional downloader or production cache install, an independently published GitHub map release, an offline navigation journey, or legal UK road truth. The record explicitly contains `published_release_downloaded=false`, `production_map_cache_loaded=false` and `offline_acceptance=false`. It is independent of the network probe's own `network_isolation_observed` / `inconclusive` state. Category 1 real offline route acceptance still requires the future combined production-path test.

Frozen file-readback regressions: [public PR #41](https://github.com/phuk-prog/drivemate-tests/pull/41), first test commit `4524c4a820ff39eba5c9b2186b5bc1a63d0992c3`. [RED run 40649624446](https://github.com/phuk-prog/drivemate-tests/actions/runs/40649624446): **91/97 framework tests passed, six expected failures**. New six-test file remains frozen for the corrected GREEN retest. Superseded overlapping network+file [PR #40](https://github.com/phuk-prog/drivemate-tests/pull/40) was closed without merging, preserving its green evidence but preventing a conflict with the independently merged network probe. Do not count this improvement complete until full CI at the final PR head reports success.
