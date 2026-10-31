# DriveMate tests

All public DriveMate app tests run in this repository. The single current order, descriptions, prerequisites, acceptance status and subcategories are in [DriveMate REGISTER.md](https://github.com/phuk-prog/drivemate-app/blob/main/REGISTER.md): **1–24**, with subcategories **1.1, 1.2, 2.1…** and **Pending / In progress / Part done / Complete**. There is no second category programme.

## Test layout and compatibility

- `categories/.../law/` verifies applicable road laws and official sources.
- `categories/.../decisions/` verifies owner-approved decisions.
- `categories/.../screen/` exercises displayed Android emulator screens.
- `app-unit/` contains app unit tests and `ci/` collects results and evidence.
- Existing numbered **test source directory names and class names are technical compatibility identifiers, not the category order**. Do not infer the current category number from a folder's historical name; the current register owns the mapping.

## Running tests

The **Test the app** workflow runs on pull requests, `main` and manual dispatch. It checks out the exact app ref, installs the public test suite and emits the `drivemate-tests-<app commit>` report artifact with `summary.json` and `run-report.html`. Partial batches never authorize releases. The signed APK workflow requires exact-source passing tests and explicit owner approval to use the private signing key. Offline acceptance requires independent evidence and must not be inferred from a cache-only or mocked test.

Preserve existing legal-road safety checks, licence notices and provenance controls. No separate licence-review category is added. Do not equate a test pass with proof of on-road accuracy.
