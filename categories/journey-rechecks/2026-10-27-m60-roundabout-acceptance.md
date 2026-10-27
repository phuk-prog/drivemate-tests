# DriveMate cross-category real-UK journey recheck — 27 October 2026

**Status: OPEN — requirements and observed evidence only; no new test run or
correctness result represented by this document.** The driver supplied three
screenshots and reported no lane strip or immersive junction view throughout
a journey; a wrong following-turn banner appeared while circulating on a
roundabout. Screenshot binaries were supplied in a private ChatGPT
conversation, **not** committed to this public repository. Do not publish
a private route log, GPS trace, phone identifier or user's home/work.

Source of truth: app `REGISTER.md` and
`register/journey-2026-10-27-issues.md`. Scope and priority must remain
separate from existing passing isolated suites.

## Required pretest setup before any implementation

- Pin **exact app Git SHA**, public-test SHA, UK PMTiles/catalogue/manifests/
  lane-square revision, latest release APK version, chosen routing engine and
  on-device online/offline mode. Record what is **unknown** when the owner's
  installed version is unavailable.
- Derive public reproducible routes from **actual UK road graph** around
  relevant M60 / A560 / Stockport/Portwood complex junctions, plus other
  independently verified two-plus-lane roads. A screenshot label is not a
  route trace; never guess the specific entrance/exit used.
- Determine legal/geometry truth independently from verified dated official
  highways/local authority data, lawful dated lane-marking imagery and
  map-source provenance; if insufficient, mark `unknown`/`provisional`.
  **Do not use the app's own expected code output or a competitor as the
  independent legal road truth.** Competitor navigation is a comparison
  benchmark, not authority on legality.
- Freeze real road inputs for pretest and rerun them unchanged after each
  code correction. Distinguish tests of actual loaded UK data from controlled
  lifecycle fixtures. Physical-road testing is excluded from the automation
  requirement, but the driver's live report remains a genuine incident.

## Acceptance cases (all NOT RUN on this incident)

| ID | Category/priority | Test / source and UI assertion | Report metrics |
|---|---|---|---|
| J27-03-A | 1/14 P0 | Actual verified roundabout **entry → circulating → last approach to exit → truly off**: active banner stays "take exit" during circulation, never changes early to Yew Street right turn; then updates only after real exit. | Correct progress frames / all ring frames; premature transitions, unknown ground truth |
| J27-03-B | 1/4 P0 | Repeat with short ordinary-tagged sections on the actual ring, nearby parallel roads, GPS fuzz and reroute; previous #36 untagged-ring fix stays effective. Verify any illegal/unsafe instruction stops release. | Correct manoeuvres/total; illegal, provisional |
| J27-04 | 1/14 P2 | Independently verify `ref,name,towards,exit,exitNumber,thenStep` for "Take the exit on the left / M60 / Then Exit 4" and duplicated Yew text, distinguish motorway exit from roundabout exit. | Correct text/total; ambiguous wording and unknown |
| J27-01-A | 3 P1 | Actual UK lane tiles and route steps available: test known multi-lane eligible motorway exit, roundabout and urban split from entry to decision; inspect source completeness, permissions, timing, scope, GPS confidence and final `NavUi.lanes`. | Correct displayed eligible events/total, missing, incorrect, unknown-unmapped |
| J27-01-B | 3/10/18 P1 | Live online map square arrives late, county/region edges, offline fallback, no lane evidence and changed route. Preserve all source-verified usable lanes; do not invent lane arrows. | Correct/total, invalid lane claims, unknown |
| J27-02-A | 4 P1 | **Motorway off-ramp and fork** while lanes are unknown: the dedicated junction-view plan and rendered view still appear at defined onset when a reliable actual route/manoeuvre exists. | Eligible/visible frames and junctions; explicit suppression reasons |
| J27-02-B | 4/5 P1 | Actual MapLibre emulator **full junction view → connected roundabout → following turn** with supported geometry; verify renderer acknowledgement, stable 3D blend and camera, enabled/disabled switches and no premature chain clearance. Ordinary tilted base map is not equivalent to immersive view. | Actual rendered views/eligible; premature/absent and provenance |
| J27-05 | 12/18 P1 | Real currently **published** online/offline UK PMTiles at Scotland overview and Manchester detailed zoom: coverage/region/zoom boundaries, joined regions, malformed/late files, Retry; distinguish absent publication from failed requests; no false UK completeness. | Correct map tiles/expected tiles; failure vs unknown publication |
| J27-06/07 | 11/12 P2 | Follow navigation, user pan to Scotland, manual zoom and re-centre; arrow at high/low zoom; no unsolicited moving camera or marker obscuring road/overview. | Follow/recentre correct/total; marker obstruction frames |
| J27-08 | 15/19 P2 | "Parking near here" during imminent motorway exit/roundabout must not obscure route, marker, lane or instruction; available at safe later approach or by explicit user action. | Safe offers/total and blocked critical frames |
| J27-09 | 12/19 P2 | Map coverage notice and "Map detail unavailable" warning on small portrait screen, larger accessibility text, landscape and non-navigation UK overview: Retry/re-centre tappable and visible, no critical overlay obstruction. | Non-overlapping accessible screens/total; visual failures |

## Results schema (populate only after real runs)

For **each** case: frozen map-input hash, exact SHA/build, test category,
law/owner-decision distinction, `correct / total`, **failures**,
**unknown**, **provisional**, precise actual vs expected outputs and links
to public GitHub Actions run `summary.json` and device screenshot
artifact. `N/T` only for truly not testable; absence of grounded lanes
is **unknown**, never silently a pass or fail. Report category rankings
against benchmark navigation engines only where comparable evidence and
identical UK inputs exist; no invented comparisons.

### Recommended efficient implementation sequence

1. **P0** Roundabout promotion / label sequence: diagnose pretest on real
   graph; change smallest supported navigation/step rule; retest frozen
   inputs. Safety-critical illegal instructions block release.
2. **P1** Instrument the single `NavigationSession` path and MapLibre
   for lanes + junction/immersive activation, so one replay explains both
   total absence failures with tagged reason codes (rather than generic
   "missing map").
3. **P1** Map data and publication version/zoom/edge audit.
4. **P2** Camera/marker and nonessential overlay timing and placement.
5. After all new supported checks pass on the **same app commit**,
   update categories 1, 3, 4, 5 and dependencies in the private register
   and only then consider a separately approved signed release APK.

Prior public PR #15 (continuity), #16 (journey fixtures), #17 (voice) have
passed component/emulator suites but **not** this real-map cross-category
acceptance. Earlier split/public PR #18 and app PR #40 remain a separate
outstanding fix/retest. The first baseline run #68 failed its overall gate;
read its summary before naming the failing tests.

No independent road truth, new automation implementation, test execution,
merge, published map or release is asserted by this checklist.
