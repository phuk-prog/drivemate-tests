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
