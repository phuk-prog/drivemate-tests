# How to run the DriveMate checks

Applies to branch `ccr-c4f1c707-a4lhew` in `drivemate-tests`, which tests the app branch of the same name.

## 1. Run one check

1. Push all code and test changes to the branch first.
2. Open **Actions → Test the app** (`test-app.yml`) → **Run workflow**.
3. Set:
   - `app_ref` = `ccr-c4f1c707-a4lhew`
   - `batch` = `all` for the full set, or a batch letter (`A`–`F`) for part of it.
4. Run **once per commit**. Do not dispatch again while a run on the same commit is still going.

## 2. What runs

- **JVM unit tests** (`app-unit/`) run in every batch. They need no emulator.
- **Screen tests** (`categories/`) run on an emulator, and only when `batch` is `all` or includes their category.
- The batch letters and their categories are defined in the manifest in `ci/process_contract.py` (`batches`).

## 3. Read the result

- The **Run the tests** step runs `:app:testDebugUnitTest`. A compile error there means no test ran.
- A compile error shows as `1/N passed`, where N is the whole suite. That is not a test failure.
- Read the **Run the tests** log for lines starting with `e:`. Those are the compile errors.
- A green run has every test passing and no diagnostics. Only that counts as evidence.

## 4. Pin every test change

- After editing any file under `app-unit/` or `categories/`, update its Git blob SHA-1 in `ci/approved_suite_inventory.json`.
- Check the pins with: `python3 -m unittest discover -s ci -p test_suite_inventory.py`
- A new test file that is not pinned fails the workflow before any test runs.

## 5. Stalled runs

- A run that shows no update for about 30 minutes has stalled.
- Cancel it, then dispatch once more on the same commit.

## 6. Owner phone checks

- On-device behaviour is checked by the owner on a phone. Those items are listed as "owner phone check pending" in the register.
- A passing run does not replace them.

## 7. Suggested order for a full pass

1. Finish all code and JVM tests for every open subcategory, and push them with pins.
2. Run `all` once on that head.
3. For any failing area, fix it and run only that batch letter.
4. Record each subcategory in the register only from a green run, citing the run ID and commit.
