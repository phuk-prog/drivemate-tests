# Category 3.1 — following-lane continuity acceptance

Date: 27 October 2026 (UK). Scope: non-physical lane-continuity checks only.

## Evidence observed before this change

- The route model exposes optional `RouteStep.followingLaneEvidence`; its default is null.
- `NavigationSession.turnLanesFor` passes this evidence to `LanePolicy.forFollowing`.
- `LanePolicy.forFollowing` requires complete source-verified links, matching both layout
  signatures and the next decision, sufficient confidence and an approved speed-limit window.
- The searched route-provider pipeline does not populate `followingLaneEvidence`; no
  real-world preferred-lane continuity capability is claimed from this check.
- Existing checks: `LaneConnectivityDecisionsTest.kt` already covers many validity guards.

## Added independent regression coverage

`LaneContinuityAcceptanceTest.kt` checks the route-model handoff and the safety-critical
fallback when provider evidence is missing; full, incomplete and ambiguous lane connections;
changed downloaded layouts, rerouting/next-decision changes, timing windows and following-lane
restrictions. All cases are contract fixtures at geographically plausible Greater Manchester
coordinates **without a verified link to specific real OSM ways**. They do not prove UK
junction ground truth or provider-supplied lane connectivity.

## Acceptance status

- **Pending:** compare the actual dated UK lane-map and routing data for several real,
  multi-junction approaches with independently verified lane connections, lane markings,
  legal access and turn restrictions. The present map/provider does not expose sufficient
  signed-off connections to assert a preferred lane.
- A missing or unsupported lane link is **unknown**, not a safety failure or permission to guess.
- Do not mark Category 3 complete, claim a real-road lane-continuity pass or begin an APK
  build based solely on this test. Continue with the subsequent end-to-end acceptance checks.
- Test execution result must be recorded from GitHub Actions summary.json on the exact
  tested app commit; the presence of these files is not a passed run.
