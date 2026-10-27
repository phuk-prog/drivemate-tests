# Whole-journey, whole-experience navigation benchmarking — J27-WE (27 Oct 2026)

## Mandatory scope rule — targeted tests only, created when needed

**Do not build or execute a blanket whole-journey/whole-app benchmark for every category.** Each new test batch is commissioned for the **specific currently active item(s)** in the existing DriveMate register. Select the minimum real UK route, journey length, GPS profile(s), comparison app(s), and screen checkpoints needed to answer that item's question. Do not invent extra scenarios simply because they are listed in this reference. Add more only when the first observations identify a concrete linked fault worth diagnosing; avoid starting broad speculative test suites.

- Name the active category, register item and question; define pretest and exact pass/fail evidence before code changes.
- Build only relevant short, medium, long or specialised end-to-end journeys. Full travel from start to arrival is used when it is necessary to validate the active behaviour (including rerouting), rather than automatically for every check.
- A single targeted drive **may incidentally reveal** GPS, camera, guidance, map, audio or performance issues. Capture these as *observations for triage*, linked by time/location, but **do not count unrelated categories as formally tested or passed**, and do not create a new suite for them until prioritised.
- Run the existing CI safety/regression checks when making app changes, but distinguish those protective checks from newly scoped acceptance tests. Do not treat a full-suite run as evidence that unobserved UI or competitor behaviour passed.
- Compare external apps/engines **only where they provide relevant, lawfully observable evidence for the active item**. If one competitor cannot provide comparable output, mark it not tested/noncomparable; never fabricate a baseline.
- **Current next check:** Category 3's `q3_3_6_7_27_completeJourneyAdvancesAndClearsLaneStrip` Android settings-readiness timeout and the driver's reported absence of lane strips. Focus on navigation-session readiness and visible lane guidance on appropriate real UK routes. Roundabout, 3D or other feature checks are separate follow-up batches when their turn comes. No new simulations or engine-wide benchmark matrix is authorised by this plan alone.

## Optional observation catalogue — use only relevant rows
One real UK drive *can* cover multiple dimensions where they directly matter to the active item. The following are **capabilities to draw from**, not a requirement to execute them simultaneously:
- **Category 1 / guidance:** whether an instruction is the correct manoeuvre (roundabout exit, left vs bear/slight left, motorway number, multi-step order), distance, timing, spoken words and continuity until the real exit.
- **Category 3 / lanes:** lane strip actually visible, usable arrows, lane continuity, advance timing, stale information after manoeuvres, missing data labelled unknown rather than invented.
- **Category 4 / junction view; Category 5 / immersive 3D:** whether they activate when expected, road shape, camera height/bearing, 3D continuity, perspective, scene stability and absence of confusing overlays.
- **GPS and map matching:** vehicle-pointer placement, heading, false jumps, signal-acquisition and recovery, position confidence, nearby parallel roads, tunnels, stopped traffic, high/medium/low accuracy, dropouts, incorrect/wrong-direction fixes.
- **Routing and rerouting:** legal route, wrong turn, missed exit, chosen rejoin, no unsafe U-turn, detection-to-new-instruction timing, route line adhered to mapped carriageway.
- **Map/data/display:** offline and online availability, missing tiles, names and junction numbers, road details, clear route colours/layers, blue line alignment, position-marker scaling, camera framing.
- **Interaction:** pan/return-to-car, responsiveness, tap targets, arrival, reporting and parked review, overlays that block controls, legibility of route guidance while other notices arrive.
- **Sound:** prompt clarity and timing, priorities, interruptions, ducking rather than pausing Bluetooth music. Automated checks may verify requested audio focus, *not* claim to have heard real car Bluetooth output.
- **Performance:** visible frame pacing, stutter, camera latency, reroute time, battery/memory and crash-free operation; record device/emulator, screen resolution, refresh rate, navigation source and app versions.

**Each drive scores only the item(s) being checked.** Give the drive one primary purpose. Log unrelated anomalies as unverified observations for later triage, without scoring or claiming to have tested their categories. Existing historic category passes stay recorded; later real-drive counter-evidence may reopen them.

## Real UK routes, journey sizes, GPS profiles and wrong turns
The route selector must read actual signed road edges and turns from the versioned DMN/OSM-derived UK road graph, not make up geometry or lane markings. Freeze source identifiers, graph SHA, origin/destination, chosen path/turns, direction and date for repeatability. Choose **only the required** short urban (roughly 1–3 km), medium mixed (5–15 km), long motorway-plus-urban (25+ km), or focused real-road roundabout route. Do **not** run all journey sizes by default. When a specific item requires it, eligible real-road scenarios include UK mini-roundabouts, ordinary or large multilane roundabouts, partially tagged rings, close succession of junctions, motorway exits, slip roads, dual carriageways, bridges, tunnels, car parks, urban canyons, parallel and service roads. **Do not include these other scenarios in a test merely for coverage.** Do not mislabel an unavailable case as passed.

When relevant to the active item, replay baseline **high-quality GPS** and specifically selected variants: **medium**, **low**, **temporary blackout**, **short isolated jump**, **multiple erroneous fixes**, **drift onto parallel road**, or **wrong-turn/missed-exit** using source-backed *alternative traversable graph edges*, never a fabricated illegal road. **Do not run the full matrix without evidence-driven need.** Signal accuracy is injected explicitly rather than inferred from the map shape; distinguish simulated uncertainty from actual radio reception. Record GPS `accuracy_m`, age, fix time, heading, speed, signed distance to true source path, and progress along path. A GPS jump should not falsely complete a roundabout or produce a confident lane recommendation; stable recovery is required after plausible fixes return. Incorrect reroutes or skipped turns fail the relevant category; absent independently checked law is **provisional/unknown**.

## Baselines, competitors and truth
Use DMN and **one or more relevant comparators only when needed** from Google Maps, Waze, TomTom, HERE, Mapbox, Valhalla, OSRM, GraphHopper, OsmAnd, Organic Maps and BRouter, provided lawful comparable observations are available. **These are observational benchmarks, not ground truth or source-code dependencies**. Do not modify, reverse engineer, scrape restricted output, bypass licensing, publish private provider assets/credentials, or assert an automated competitor replay is available without evidence. Record `not_tested` if no lawful comparable run exists.

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
1. **Pretest** the unchanged app against the **smallest suitable** frozen, source-backed real UK road scenario for the currently active item; if the behaviour requires a complete journey, run from navigation start through arrival and preserve failures and timing. Existing public engine and Android-session tests should be reused rather than reimplemented as mock-only equivalents.
2. **Change** only the supported defect in the non-production app branch, keep baseline fixtures frozen.
3. **Retest** the identical item-specific trip, GPS seed/profile(s) and observation checkpoints. Measure before/after, checking any directly linked safety side effects without expanding into unrelated test categories.
4. **Compare** competitors where lawful equivalent observations are actually available; show disagreements, reason, independent source checks and which behaviour is better **only after evidence**.
5. **Gate** safety-critical manoeuvre, wrong-way/reroute and verified lane errors. A passing mocked/component test is not a passing visible, end-to-end journey. Keep Categories 1, 3, 4 and 5 open for the user's 27 Oct journey, but **work through one active item at a time**, testing its actual displayed state before closing it.

This document is a **test-selection policy and future test reference**, not a test execution result. No new comparable competitor results, GPS replay fixtures, or cross-category passes are claimed until they are actually implemented and run as needed.
