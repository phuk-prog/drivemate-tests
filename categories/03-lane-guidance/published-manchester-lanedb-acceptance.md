# Category 3 — published Greater Manchester source to production LaneDb (27 October 2026)

**Status: pretest in progress; no app-visible lane strip demonstrated.** Public source audit [run 40570774089](https://github.com/phuk-prog/drivemate-tests/actions/runs/40570774089) passed and published artifact `12764842958`. It authenticated the UK-joined `lanes-106_-5.json` square with SHA-256 `dcaff102f855531e6bce98b77d8a2068684296590f20c032240a4eb8521e5685`, identifying 223 source-tagged multi-lane arrow candidates among 3,915 Stockport-area ways. The artifact contains 12 unaltered source way geometries/tags (not driver routes). The public test resource `app-unit/resources/cat3-published-manchester-lanes-3.json` freezes **three exact ways** from that audit with source SHA.

The five new JVM tests exercise the **production** `LaneDb.parse` and `LaneDb.lanesAt` on actual published OSM-derived geometry and headings, not synthetic roads:

- OSM way **930354**, Great Ancoats Street (A665): source has two lanes, `left;through|through`. Check both lane arrows in road order, permitted maneuver highlighting and no wrong-way claim.
- OSM way **4419709**, Trafford Road (A5063): source has four lanes, `through|through|through;right|right`. Check all four lanes and source-derived right/straight highlighting.
- OSM way **4542409**, Carrington Road (B6104): published `lanes=3` conflicts with two `turn:lanes` entries. Check **null/unknown**, not fabricated third lane.

**Safety and acceptance limits:** These are OSM source tags, **not** independent dated verification of actual painted lane markings, legal vehicle access, a valid connected turn, GPS map matching, NavUi, a rendered strip, installed APK, online/offline parity or rerouting. They must not be scored as legal truth or complete Category 3 acceptance. Freeze and rerun the same source geometry after fixes. Next: choose independently corroborated UK legal lane layouts and route connections; replay real road graph and assert actual `NavigationSession.nav.lanes` plus rendered Compose lane strip, with pass/fail/unknown/provisional separately counted.
