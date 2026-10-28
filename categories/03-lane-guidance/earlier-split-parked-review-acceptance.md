# Category 3: earlier-split reporting and parked-approval acceptance

Checked 27 October 2026.

## Baseline risks discovered by code inspection

- When the driver says lane guidance is wrong at an **earlier split**, production
  `flagLaneCorrectionForReview` captures the displayed earlier split's location,
  approach direction, outgoing direction and source signature, but saves an empty
  lane list even though the displayed, verified candidate lanes are available.
  In the after-drive dialog the existing layout is therefore not pre-filled.
- `approveLaneReport` checks a fresh stopped GPS fix, but does not check whether
  the report object it receives is still in the private pending-report store.
  A stale dialog callback may therefore approve a deleted or superseded report.
- Both pathways are private; speaking a report does not automatically approve
  edits or publish maps. The user-selected safety contract remains unchanged.

## New integration checks

`LaneSplitParkedReviewAcceptanceScreenTest.kt` exercises the actual production
`NavigationSession` and stores, with a controlled earlier split before a later
junction:

1. Report location is the **displayed earlier split**, not the later turn.
2. The private report contains the displayed lanes and valid/invalid markings
   so a parked reviewer can inspect and edit the layout.
3. Voice reports do not immediately approve corrections or publish any map.
4. Duplicate reports for the same split do not accumulate.
5. Moving/stale GPS fixes cannot approve; a fresh stopped fix can.
6. Review records remain specific to source/layout, manoeuvre, direction.
7. A report already approved and deleted must not be approved again by a
   stale screen callback.

Run against the existing app before any app patch to collect actual pre-fix
failure evidence. Then patch only the production code needed and rerun the
identical test inputs, keeping both results.

## Limits

The route is a controlled Greater Manchester-coordinate fixture, **not**
independently verified UK lane markings. A speed=0 GPS fix with a user
confirmation is a product-level parked gate, not independent confirmation
the vehicle is securely parked. No physical road or car tests are performed.
Do not mark Category 3 complete from this check alone.

## Frozen fix-candidate rerun — 27 October 2026
- Original unfixed-app public [run #68](https://github.com/phuk-prog/drivemate-tests/actions/runs/40485703060): **265/268** passing. Both new report/approval screen checks failed, plus the separate settings-readiness lane journey test. Original app SHA `05ef576af5bab22add8323f07a86ae4a7ad08957`.
- Proposed correction remains [app PR #40](https://github.com/phuk-prog/drivemate-app/pull/40). The independent DataStore startup fix was merged via app PR #42, public tests PR #20, [run #76](https://github.com/phuk-prog/drivemate-tests/actions/runs/40560370610): **268/268**, but did not include these earlier-split tests.
- Rerun the **unchanged two earlier-split tests** against the matching app fix branch `test/cat3-earlier-split-parked-review-20261027`; the standard public PR runner selects that matching app branch automatically. Keep expected pre-fix failures as history. **Do not merge PR #40/#18** unless both new acceptance checks and the overall public regression gate pass. This edit only requests retest; it is not evidence of a result.

## Fixed-base rerun after independent settings fix — 27 October 2026

- The first paired candidate [run #82](https://github.com/phuk-prog/drivemate-tests/actions/runs/40567384662) used app SHA `48730a4425e26726510d133f5431bc9d18ffa1fc`, before the settings DataStore fix was merged into app main. Result: **267/270**. Failures: `q3_16_17_20_parkedApprovalMustUseExistingPrivateReportAndFreshStoppedPosition` (settings-ready timeout), `q3_3_7_14_laneSpeechIsOncePerShownDecisionAndResetsOnlyForNewDecisionOrRoute` (settings-ready timeout), and `q3_overlappingSessionsCanReadTheSameSettingsWithoutDataStoreConflict` (duplicate active DataStore). The new earlier-split report preservation test **passed**. This is not evidence that the parked-approval code failed after initialization.
- To preserve all 270 frozen tests and avoid masking the independent startup bug, app PR #40 candidate branch has been advanced with a genuine merge of latest main `63277ad77969eb0ef9c3e39f639f93318f9b761f` (which includes settings fix app PR #42), retaining its original six-addition/one-deletion source patch. Combined candidate app commit: `2abdf573a1569e1c44232da161b5f0d5e908367d`. Rerun this unchanged public cohort against the **matching branch**. No new legal lane truth, APK or real-road acceptance is asserted.
