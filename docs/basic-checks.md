# Basic routine checks

Routine push and pull-request CI runs ten explicitly selected existing Python checks plus
Python syntax and approved Kotlin source-inventory checks. It does not use app credentials,
an Android SDK/emulator, live services or downloaded map data.

Run `python3 ci/run_basic_checks.py --output results/basic.json` locally. The report always
records `profile: BASIC`, `release_allowed: false`, and category/legal acceptance as
`not_assessed`. Missing selected tests, failures, errors, skipped tests or failed source
checks make the command fail. The inventory counts source methods, not executed Android tests.

All existing test definitions remain available. Full `test-app.yml`, the app watcher and the
seven source/live-data audit workflows are manual-only. Signed APK delivery still requires
the full current-suite run for the exact app commit; its workflow and signing controls are
unchanged. Basic artifacts use a distinct `drivemate-basic-*` name.

## Category 1 Batch A focused evidence validation

The separate `test_legal_truth_binding.py` regression cohort checks that a legal-evidence
record cannot use an unrelated official document for a different case, action, source,
app/test revision or date. Legal records require schema 2 document identity/version, exact
acceptance bindings and an independently established applicability interval containing the
evaluation time. Run it explicitly with
`python3 -m unittest discover -s ci -p test_legal_truth_binding.py`.

This is record validation, not authentication of a legal document, verification of a real
turn or legal acceptance. Synthetic adversarial records in those tests are never real UK
road evidence. Missing applicability, amendments, exact movement or original document
identity remains unknown. Category 1 and the J27-03 safety blocker remain open.
