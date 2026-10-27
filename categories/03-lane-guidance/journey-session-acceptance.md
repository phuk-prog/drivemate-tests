# Category 3 — NavigationSession route-passage and reroute acceptance

27 October 2026. Target: `drivemate-app/main` at the commit recorded by each test run.

## Coverage added

`categories/03-lane-guidance/screen/LaneJourneySessionScreenTest.kt` uses the
real Android `NavigationSession`, its `setRouteInternal` and
`updateNavigationInternal` implementations, production `NavigationEngine`,
and actual `NavUi.lanes` output to exercise:

1. Lane display approaching the first of two successive junctions.
2. Removal of first-junction advice after passing that junction.
3. Display of the next junction's *different* mapped lanes at its own onset.
4. Removal of second-junction advice after passing.
5. A route replacement that changes usable-lane markings, without reusing the old
   route's lane-display latch, as well as an approach outside the new timing window.
6. No invented preferred lane where the ordinary route contains no verified
   `followingLaneEvidence`.

The test has production Android view/session integration rather than directly
testing `LanePolicy` in isolation. Route geometry, arrows and GPS locations are
controlled Greater Manchester-area fixtures: **no independent current UK road
markings or legal lane connectivity are verified by this replay.**

## Still outstanding

- Compare full route replays with dated, independently established UK road-layout
  and lane permissions, especially complex roundabouts, motorway exits, roadworks
  and successive lane splits.
- Confirm recovery under weak GPS, loss/reacquisition and real provider map-data
  loading, not just injected good fixes.
- End-to-end voice, interruption, reporting of earlier splits, and parked review
  are separate Category 3 acceptance checks.

Physical road/car testing is excluded by the owner's direction. Do **not** mark
Category 3 complete solely because these emulator route fixtures pass.
