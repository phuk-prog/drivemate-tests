# Whole-journey, whole-experience navigation benchmarking — J27-WE (27 Oct 2026)

## Scope
One real UK drive is an integration test, **not merely a routing test**. Observe the *same* navigation state at the same geographically identified checkpoints and simultaneously evaluate:
- **Category 1 / guidance:** whether an instruction is the correct manoeuvre (roundabout exit, left vs bear/slight left, motorway number, multi-step order), distance, timing, spoken words and continuity until the real exit.
- **Category 3 / lanes:** lane strip actually visible, usable arrows, lane continuity, advance timing, stale information after manoeuvres, missing data labelled unknown rather than invented.
- **Category 4 / junction view; Category 5 / immersive 3D:** whether they activate when expected, road shape, camera height/bearing, 3D continuity, perspective, scene stability and absence of confusing overlays.
- **GPS and map matching:** vehicle-pointer placement, heading, false jumps, signal-acquisition and recovery, position confidence, nearby parallel roads, tunnels, stopped traffic, high/medium/low accuracy, dropouts, incorrect/wrong-direction fixes.
- **Routing and rerouting:** legal route, wrong turn, missed exit, chosen rejoin, no unsafe U-turn, detection-to-new-instruction timing, route line adhered to mapped carriageway.
- **Map/data/display:** offline and online availability, missing tiles, names and junction numbers, road details, clear route colours/layers, blue line alignment, position-marker scaling, camera framing.
- **Interaction:** pan/return-to-car, responsiveness, tap targets, arrival, reporting and parked review, overlays that block controls, legibility of route guidance while other notices arrive.
- **Sound:** prompt clarity and timing, priorities, interruptions, ducking rather than pausing Bluetooth music. Automated checks may verify requested audio focus, *not* claim to have heard real car Bluetooth output.
- **Performance:** visible frame pacing, stutter, camera latency, reroute time, battery/memory and crash-free operation; record device/emulator, screen resolution, refresh rate, navigation source and app versions.

**Every drive logs every applicable category.** Give the drive one primary purpose so diagnosis stays specific, and mark *secondary* problems found in other categories as new incidents linked to the same timestamp/checkpoint. Old category passes are historical; reopening a category does not delete them.

## Real UK routes, journey sizes, GPS profiles and wrong turns
The route selector must read actual signed road edges and turns from the versioned DMN/OSM-derived UK road graph, not make up geometry or lane markings. Freeze source identifiers, graph SHA, origin/destination, chosen path/turns, direction and date for repeatability. Select short urban (roughly 1–3 km), medium mixed (5–15 km) and long motorway-plus-urban (25+ km) drives, plus focused loops/roundabouts with different shapes, size and tagging quality. Include UK mini-roundabouts, ordinary and large multilane roundabouts, partially tagged rings, close succession of junctions, motorway exits, slip roads, dual carriageways, bridges, tunnels, car parks, urban canyons, parallel and service roads where actual source coverage exists. Do not mislabel an unavailable case as passed.

Replay a baseline **high-quality GPS** path and variations **medium**, **low**, **temporary blackout**, **short isolated jump**, **multiple erroneous fixes**, **drift onto parallel road**, and **wrong-turn/missed-exit** from source-backed *alternative traversable graph edges*, never a fabricated illegal road. Signal accuracy is injected explicitly rather than inferred from the map shape; distinguish simulated uncertainty from actual radio reception. Record GPS `accuracy_m`, age, fix time, heading, speed, signed distance to true source path, and progress along path. A GPS jump should not falsely complete a roundabout or produce a confident lane recommendation; stable recovery is required after plausible fixes return. Incorrect reroutes or skipped turns fail the relevant category; absent independently checked law is **provisional/unknown**.

## Baselines, competitors and truth
Start with DMN and available, lawfully observed Google Maps, Waze, TomTom, HERE, Mapbox, Valhalla, OSRM, GraphHopper, OsmAnd, Organic Maps and BRouter where they support comparable vehicle navigation. **These are observational benchmarks, not ground truth or source-code dependencies**. Do not modify, reverse engineer, scrape restricted output, bypass licensing, publish private provider assets/credentials, or assert an automated competitor replay is available without evidence. Record `not_tested` if no lawful comparable run exists.

A Google "left" arrow versus DMN "slight left" is a *disagreement*, not proof of a defect. Check signed/dated UK highway authority records, Traffic Regulation Orders where applicable, verified geometry, official signs/markings or other lawful dated evidence. Decide *correct/incorrect/provisional* independently for **manoeuvre semantics**, not simply whether the UI looks alike. If official truth is unavailable, do not invent a lane or alter a legal instruction. UX presentation is judged separately against observable criteria (legibility, map alignment, time visible, frame pacing) and is not automatically "right" because one app is popular.

Use same vehicle class, directions, manoeuvre checkpoints and comparable settings. Record each engine's version, map date, device, online/offline state, GPS source and scenario. If the other app cannot accept the exact simulated trace or share the same environment, mark **noncomparable** and report observed differences *without a ranked head-to-head result*. Do not store screenshots containing home addresses, personal routes, account data or third-party licensed assets in this public repo.

## Required evidence per observation
Record a run ID and date, real route graph ID/SHA, app build/engine, GPS profile, device/renderer, checkpoint and elapsed time, expected behaviour, actually observed screen/voice/position/latency, source-backed or dated legal truth (if claimed), screenshot/video/log reference where publishing is permitted, and the test verdict:
- `pass`: observed against an independently stated criterion, with evidence.
- `fail`: repeatable mismatch, with evidence and a linked incident.
- `unknown`: source data absent, unavailable competitor output or missing evidence.
- `provisional`: evidence exists but legal/map truth is disputed, stale or incomplete.
- `not_tested`: scenario not executed (never counted as pass).

Keep two separate judgements: **functional/road correctness** and **visual/experience quality**. Use 0–10 quality scores only where measured with a published rubric and comparable settings; exclude unknown/noncomparable values from ranking. Record timestamps and each check's reason. Store no invented results.

## Acceptance and execution
1. **Pretest** unchanged app against frozen source-backed full journey with real map graph and route controls; preserve failures and timing. Existing public engine and Android-session tests should be reused rather than reimplemented as mock-only equivalents.
2. **Change** only the supported defect in the non-production app branch, keep baseline fixtures frozen.
3. **Retest** on the identical trip, GPS seed/profiles and external observation checkpoints; measure before/after and seek regressions in all linked categories.
4. **Compare** competitors where lawful equivalent observations are actually available; show disagreements, reason, independent source checks and which behaviour is better **only after evidence**.
5. **Gate** safety-critical manoeuvre, wrong-way/reroute and verified lane errors. A passing mocked/component test is not a passing visible, end-to-end journey. Keep Categories 1, 3, 4 and 5 open for the user's 27 Oct journey until a complete emulator/screen replay confirms the actual displayed state.

The `ci/whole_journey_compare.py` data contract and its tests make absent competitors and unverifiable claims explicit. They are **report validation and side-by-side analysis**, not yet an Android simulator or a competitor execution engine. The route/profile manifest is a selection and acceptance plan; no planned scenario is reported as run.
