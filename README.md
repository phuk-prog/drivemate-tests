# drivemate-tests

The testing for DriveMate. Every test runs here, in this public repository.

Authoritative register: https://github.com/phuk-prog/drivemate-app/blob/test/continuous-drive-20261030/REGISTER.md

**Current execution scope (31 October 2026):** The app's authoritative TEST-branch register contains **24 categories and 124 subcategories** in dependency/driver-priority order. Some historical test-programme files still use 23 legacy slots; those are test-harness groupings, **not** the current development category count or execution cursor. Read `AGENTS.md` and the live register before selecting work. Keep all new source/tests on matching TEST integration or isolated candidate branches, never on `main`. Preserve mandatory attribution and road-safety checks; do not mistake a passing emulator test for UK-road or real-phone acceptance.

## Layout

- `categories/NN-name/law/`: law tests. They check the app against the law and official sources
  (GOV.UK, TSRGD, National Highways) only, never the register's decisions.
- `categories/NN-name/decisions/`: decision checks. They check the app does what is decided
  in that category's file in drivemate-app's `register/`. Each test names its question (e.g. `q1_6`).
- `categories/NN-name/screen/`: screen checks, run on an Android emulator: the app draws its own
  screens and the test checks what is on them, with a screenshot for the picture page.
- `app-unit/`: the app's own unit tests (moved here from drivemate-app).
- `ci/`: turns the results into `summary.json` and the picture page (`run-report.html`).
- `.github/workflows/test-app.yml`: the test run.

## How they run

"Test the app" runs on every pull request here, on merges to main, and by hand (Actions → Test the
app → Run workflow, optionally naming a drivemate-app branch or commit). It fetches drivemate-app
(the branch with the same name as this one if there is one, else main), puts these tests in place of
the app's unit tests, runs them, and publishes one artifact, `drivemate-tests-<app commit>`, with
`summary.json` and `run-report.html`. App code is only built on the runner; it is never published.

drivemate-app's APK build only goes ahead when that artifact shows every test passed for the exact
app commit being built.

## Signed APK (for now)

"Build signed APK" (Actions → Build signed APK → Run workflow) builds the signed release APK of a
drivemate-app commit (main by default), only if "Test the app" passed on that exact commit (it starts
that test run itself and waits for it when there is none yet). It then
waits for the owner's approval: the signing key is only in the `release` environment, which needs
the owner as reviewer and only serves main. The APK is saved only inside an AES-256 password-
protected zip (password: `APK_ZIP_PASSWORD` in the same environment), kept 7 days. Open it with
7-Zip (Windows), Keka (Mac) or ZArchiver (Android); the built-in Windows and Mac zip tools can't open
AES zips. No debug APK is built here.

## Setup (once)

drivemate-app is private, so this repository needs a read-only token for it:

1. GitHub → Settings → Developer settings → Fine-grained tokens → Generate new token.
   Repository access: only `phuk-prog/drivemate-app`. Permissions: Contents → Read-only.
2. In this repository: Settings → Secrets and variables → Actions → New repository secret,
   name `DRIVEMATE_APP_TOKEN`, value the token.

## Not covered yet

- Physical tests (in a real car) are not run here.
- 1.9 and 1.18 mini-roundabouts: checked from the route and road data; the road data itself comes from drivemate-map's weekly build.
- Category 2 sound itself is not heard by any test: the tests check what the app decides to say or play,
  and when. Physical tests, in a real car: Car-call mode over Bluetooth, directions spoken over a real
  phone call (2.10, 2.19), the guidance volume levels (2.17), the speeding chime and camera beeps (2.11),
  and "Hey DriveMate" heard over road noise and music (2.15, 2.20). "Hey DriveMate" needs Android 12+
  with on-device speech recognition, and listens only while DriveMate is on screen.
