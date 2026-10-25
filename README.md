# drivemate-tests

The testing for DriveMate.

Register: https://github.com/phuk-prog/drivemate-app/blob/main/REGISTER.md

## Layout

- `categories/NN-name/law/`: law tests. They check the app against the law and official sources
  (GOV.UK, TSRGD, National Highways) only, never the owner's choices.
- `categories/NN-name/decisions/`: decision checks. They check the app does what the owner decided
  in that category's file in drivemate-app's `register/`. Each test names its question (e.g. `q1_6`).
- `ci/add_unit_results.py`: adds these results to the app run's `summary.json` and picture page.

## How they run

drivemate-app's "Full-app gates" workflow checks out the newest `main` of this repository, copies
`categories/*/{law,decisions}/*.kt` into the app's unit tests (package `com.drivemate.tests.*`),
runs them with Gradle, and adds the results next to the emulator tests. Fast tests only for now;
the emulator tests still come from drivemate-test-map-display until they move here.

## Not covered by a fast test yet

- 1.1 card layout and 1.11 "Then" chip timing: need the emulator (screen checks).
- 1.9 mini-roundabouts: not built; the app has no mini-roundabout map data yet (1.16 fallback is checked).
