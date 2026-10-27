# J27-01 — published real Greater Manchester lane-source audit (Category 3)
Date: 27 October 2026 UK. **Pretest, not an acceptance pass.**

## Motivation
The owner saw no lane strip anywhere on a real M60/Stockport journey. Earlier
passing screen tests used controlled route steps containing arrows; they did not
prove that published UK lane squares were available, current or able to supply
arrows for the actual road.

## Reproducible public source check
The public `ci/audit_published_manchester_lanes.py` reads the **live published**
`map-data-ukjoin/uk.json` pointer, verifies the manifest length and SHA-256,
then verifies the actual published `lanes-106_-5.json` file (Greater Manchester)
against the manifest's exact byte count and SHA-256. It counts actual OSM-derived
road features in a bounded Stockport/M60 area and source-tagged multi-lane
turn-arrow candidates. It records a small sample of public OSM way IDs and
immutable hashes to permit future frozen comparison. No private driver GPS
trace, phone data, APK, or image is uploaded.

- The audit **does not** establish the user's specific junction, current
  British road markings, lane legality, GPS matching, lane recommendation,
  Android screen rendering or installed APK version.
- A missing pointer, malformed digest, absent square or failed publication
  is a **blocked source audit**, not a claim that DriveMate's lane logic failed.
- Zero tagged candidates means the source-data coverage may be inadequate:
  do not fabricate arrows or mark category accepted.
- The public [GitHub Actions source audit](../../.github/workflows/cat3-published-lanes.yml)
  uploads a small machine-readable results JSON. Run it on this PR, and
  record the run ID, pointer/manifest/square hashes, counts and blockers.

## Next gate after source audit
Select a road with an actual sourced complete layout and **independently
corroborate** legal/painted lane arrows by dated UK official records or other
lawful dated evidence. Then run the exact same route from start to finish in
Android emulator using production `NavigationSession`, data loader and
driving UI: trace source tile -> `LaneDb.lanesAt` -> policy gates ->
`NavUi.lanes` -> visible strip before decision -> clear after passage and
reroute. Record correct/eligible, failure, unknown and provisional separately,
including version/snapshot and online/offline status. Keep incident J27-01
and Category 3 reopened until that full acceptance passes.
