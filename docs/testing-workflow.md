# Testing workflow

## Authorised implement-and-upload tasks

- Include primary public GitHub testing in the initial plan.
- Complete local proof, upload, exact-commit CI and concise evidence reporting in one pass.
- Local checks alone do not complete this workflow. Report actual blockers explicitly.

## Deliberately kept duplicates

These duplicates are intentional. Keep them in sync when changing the related code.

- The verify gate in `build-apk.yml` and `release-apk.yml` (checked by text in drivemate-tests).
- The routing steps in `map-data.yml` and `routing-uk.yml` (checked for equality by `test_independent_routing_workflow`).
- The checkout and emulator steps in drivemate-tests.
- The licence and notice files.
- `ci/m02-dmn-rebuild-request.txt` (DMN workflow trigger).
- The Manchester JSON data in `docs/validation`.
