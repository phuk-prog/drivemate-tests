# Test coverage map, categories A–L

Starting point, built from test file and method names only. Each row needs checking against the app code before it is relied on. "Approximate" marks a mapping where the test may belong to more than one category.

## Existing tests by file

| File | Methods | Category | Confidence |
|---|---|---|---|
| PositionCategoryTest.kt | 3 | A (permission, quality loss and recovery; boundaries; 60 s estimate) | Clear |
| PositionTimingTest.kt | 3 | A (timing) | Clear |
| LocationStatusTest.kt | 6 | A (GPS status, thresholds, revoked permission) | Clear |
| DeadReckoningAndDotTest.kt | 2 | A (marker shown above 50 m) | Clear |
| CategoryAEmulatorTest.kt (emulator) | 1 | A (basic permission, first fix, native paint) | Clear |
| NavigationSafetyTest.kt | 35 | C, F (arrival, destination, route budgets, trip safety) | Approximate |
| TripRecoveryDismissalTest.kt | 37 | I (trip recovery), K (history) | Approximate |
| TrafficRestartTest.kt | 2 | K (settings restart) | Clear |
| BackupRestoreInterruptionTest.kt | 17 | K (backup and restore) | Clear |
| StartupMirrorFailureTest.kt | 3 | K (settings mirror) | Clear |
| RetentionAuthorityGatedTest.kt | 2 | K (retention) | Clear |
| NavigationSessionStartupTest.kt | 8 | I (startup), K | Approximate |

## Coverage by category

| Category | Tests found | Status |
|---|---|---|
| A Position and GPS | 14 unit, 1 emulator | Covered for the basic behaviours |
| B Road matching | none | **No coverage** |
| C Turn guidance | part of NavigationSafetyTest | Partial, approximate |
| D Voice | none | **No coverage** |
| E Lanes and signs | none | **No coverage** |
| F Route and destination | part of NavigationSafetyTest | Partial, approximate |
| G Driving view | none | **No coverage** |
| H Android Auto | none | **No coverage** |
| I Background and trip recovery | TripRecoveryDismissalTest, NavigationSessionStartupTest | Partial, approximate |
| J Map data | none | **No coverage** |
| K Settings and saved data | five files, about 32 methods | Covered for the basic behaviours |
| L Privacy, accounts, general | none | **No coverage** |

## Next steps
1. Read the app code for B, D, E, G, H, J and L to confirm which behaviours exist.
2. Confirm the approximate mappings for C, F and I by reading the test bodies.
3. Add one basic check per uncovered behaviour, at the cheapest layer that proves it.
