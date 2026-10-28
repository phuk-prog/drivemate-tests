# TF-20 coverage and efficiency — preparation

Status **OPEN**, 28 October 2026. This audit inventories public source declarations; it does not execute Android tests or accept app categories. App inspected: `a52ddb6c2df4dabb36d0de26c68a41636cd9bc2d`. Suite source cohort: `bf35f68a3b92945ffadaebb69c961994991002b3`; TF-02 additions affect Python audits only. Strict Category 1–23 execution and J27-03 release block remain.

## Frozen source cohort

Using the same Kotlin `@Test fun` declaration/package/class rules as `ci/collect_results.py`, the public `app-unit/java` and `categories` trees contain **291 distinct methods, 0 duplicate identities**: 89 app-unit, 146 decisions, 20 law and 36 screen declarations. Sorted `class#method` identity-list SHA-256: `6200afad5969b55ac220d240c3ed4a9939614886ceb38b52bae2b91ef256732f` (UTF-8, newline joined, no trailing newline).

The programme maps **215** declarations to 11 current categories. **76 declarations across 17 app-unit classes remain unassigned**. Keep them in the denominator; do not duplicate counts or infer a mapping from an old folder number. Original private-app tests discovered during an actual build can add observed methods beyond this public-declaration cohort; reconcile those identities separately. No correct/failed/unknown/provisional/skipped execution score is inferred from these declaration counts.

| Current category | Mapped public declarations | Acceptance still needed / blocker |
|---|---:|---|
| 1 Map data and processing | 13 | Components only; real UK graph, rights, scope, installed source→guidance and independent truth: TF-06–11/15 |
| 2 Reliability | 0 | Collection/release artifact reconciliation and safety gate: TF-05/17/18 |
| 3 Settings/account/backup | 0 | Applicable actual account/backup consumer and recovery checks; do not infer from link inventory |
| 4 Downloads/storage | 0 | Verified actual graph/supporting data install/cache/recovery: TF-06–08 |
| 5 Map display | 0 | Actual installed map consumer/rendering and real coverage: TF-07–09 |
| 6 Position/GPS | 12 | Authentic UK replay/stress cases and reacquisition: TF-12 |
| 7 Turn-by-turn | 23 | J27-03 P0, actual source/session/screen, lawful progression: TF-11–15 |
| 8 Lanes | 70 | Real lane denominators, contradictory tags, legal approach and visible session: TF-09/11/13/15 |
| 9 Voice/sound | 27 | Same-session spoken guidance and real Bluetooth/call/timing observation: TF-11/19 |
| 10 Rerouting | 8 | Authentic wrong-turn, legal route and recovery cohort: TF-10/12–15 |
| 11 Driving camera | 0 | Real session/device rendering acceptance; component presence is insufficient |
| 12 Junction view | 24 | True junction geometry, lane applicability, roundabout lifecycle and actual display: TF-11/13/15 |
| 13 3D immersive | 9 | Real-data scene and actual device/performance acceptance |
| 14 Speed limits | 10 | Vehicle/time/road-specific dated legal truth and actual consumer: TF-15 |
| 15 Road alerts | 10 | Applicable authentic provider/source and delivered output: TF-04/11/15 |
| 16 Traffic | 9 | Actual provider services, time-dependent lawful restrictions and consumer: TF-04/15 |
| 17 Search/places | 0 | Real source/services, address/entrance coverage and installed consumers: TF-04/08/09 |
| 18 Route planning | 0 | Published real graph, legal route, parity and arrival: TF-06/07/10/14/15 |
| 19 Arrival/parking | 0 | Real correct destination/entrance, arrival and critical-phase display: TF-14/19 |
| 20 Start/layout | 0 | Real startup/layout/release candidate checks; TF-17/19 |
| 21 History/review | 0 | Applicable real stored-trip/review behavior and lifecycle checks |
| 22 Corrections/OSM | 0 | Actual authorized service and consumer behavior; TF-04; configured sandbox is not live acceptance |
| 23 Android Auto | 0 | Actual applicable car/device acceptance; TF-19 |

## Reconciliation and efficiency gate

- Reuse terminal exact-head CI receipts and all existing passing component evidence; acquire the final TF-01 run's programme/summary before reporting executed per-category scores. Run `manifest()`'s validation to retain the 23-category order and the exact one-batch-per-category A–F partition.
- Reconcile collected XML identities against the frozen declarations, app-built-in identities and selected batch. Missing/renamed/duplicate tests must fail closed; unselected categories remain not tested. Do not accept an unrelated or PR-only release artifact.
- Report **correct/total, failed, unknown, provisional and skipped** independently, plus unassigned identities, test failures versus unavailable evidence, and app/test/map/source hashes. Passing law-policy tests are not authenticated legal-road cases.
- Reuse the prior **52/52 map + 52/52 manifest actual-byte** receipts and bounded Stockport install proof. Do not repeat the ~1.6 GB fetch for unchanged release bytes. Map-display PMTiles do not substitute for the missing whole-UK routing graph.
- Batch only the affected prerequisites while Category 1 remains active. Do not run later feature-category acceptance merely because its test row exists. Re-run broader affected gates once on the integrated final head, not independently for every agent patch.
- TF-19 installed phone/car/signature/GPS/audio observations remain a physical/human boundary. No emulator receipt or prepared artifact can clear it; signing/release needs its existing explicit authorization.

Preparation adds **0 executable tests** and claims **0 real-device observations**. TF-20 cannot close before applicable TF-01–19 evidence and safety blockers are resolved.
