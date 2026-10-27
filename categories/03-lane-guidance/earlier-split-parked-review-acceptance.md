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
