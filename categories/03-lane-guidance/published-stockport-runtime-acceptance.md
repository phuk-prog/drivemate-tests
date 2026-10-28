# J27-01 — actual Stockport published lane square through production OwnMap

The unchanged public Category 3 tests in PR #21 verify the compiled map-release publisher and, in Android emulator, that production `MapData` discovers the actual published UK-joined Stockport `lanes-106_-5.json` file, `OwnMap.loadAlong` downloads and indexes it, and real nearby source road segments are present. These checks do not assert legal lane arrows, a valid route, `NavigationSession.nav.lanes` or the on-screen strip.

Original [run #80](https://github.com/phuk-prog/drivemate-tests/actions/runs/40566484226), app `93a3de56e13ce8621f71d901beffc8f4ad94e00b`, tests merge-ref `1b5dff15e58a1b3ffbad5d24ca31175515132880`, artifact `12762869485`: **268/270**, including both new publication checks passing. The only failures were unrelated existing Category 3 settings readiness timeout and duplicate active DataStore. The later independent settings fix passed 268/268 in run #76 and merged to app main.

To keep the two source tests unchanged and isolate that known startup defect, fast-forward the matching app test branch `test/cat3-map-release-publisher-20261027` to main `7672b237cbbff3b78c6b42aa2c00f679b368c703`, then rerun the same 270-test cohort. This document is a test-run trigger and evidence log, not a claim of passing retest or legal truth. Merge only after all gates pass.
