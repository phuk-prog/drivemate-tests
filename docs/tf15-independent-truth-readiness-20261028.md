# TF-15 independent UK legal truth — readiness inventory

Status: **OPEN / acquisition required**, 28 October 2026. This is a read-only evidence inventory, not legal verification or navigation acceptance. Inspected app commit `a52ddb6c2df4dabb36d0de26c68a41636cd9bc2d`; public suite base `bf35f68a3b92945ffadaebb69c961994991002b3`. Existing Category 1 and J27-03 blockers remain open.

## Existing evidence and its limits

- `evidence/legal-truth/` contains **0 records**. The process preflight reports zero as missing evidence, not a passing comparison. No official document bytes or road-case legal facts were acquired in this audit.
- `ci/process_contract.py:independent_truth` validates a declared official host, SHA-256 shape and nonfuture timestamp. It does **not** fetch/authenticate the document, bind it to a road/approach/vehicle/time interval, establish a sealed order, check revocation, verify distribution rights or prove the asserted restriction from its contents. A structurally valid record alone cannot close TF-15.
- `provenance/manchester-lanes/source-samples.json` freezes 12 actual OSM-derived ways under source digest `dcaff102f855531e6bce98b77d8a2068684296590f20c032240a4eb8521e5685`. That is source provenance, not independent dated legal truth.
- Existing `categories/*/law` checks exercise generic UK units, policy and speed-tag decoding. Generic national rules do not establish the current signs, markings or orders on a particular road.
- The validator currently excludes `gov.wales`/`www.gov.wales`, despite those official sources being cited in the app's speed-limit register. Before accepting Welsh official evidence, add a narrowly scoped authentic-source regression and review the official-host policy. Do not replace missing case evidence with an allowlist change.

## Prioritised acquisition cohort

These are public source-road candidates already recorded in the register, not driver location traces. Each row remains **UNKNOWN** for present road-law truth.

| Priority / case | Frozen source or incident | Independent evidence still needed | Acceptance limitation |
|---|---|---|---|
| P0 / J27-03 | M60 J1 Pyramid/Stockport roundabout; actual incident says guidance promoted Yew Street while still circulating | Dated official junction/exit geometry and applicable signs/markings, approach/direction binding, restriction/order applicability; separately authenticated route and session evidence | Legal truth cannot determine whether the navigation engine prematurely advanced; TF-13/14 replay and visible guidance are separate gates |
| Carrington Road B6104 | OSM way `4542409`: `lanes=3`, two `turn:lanes` entries | Road authority layout/marking record and dated lawful corroboration for the sampled approach; effective restrictions and directions | Source discrepancy remains unknown legal truth; do not synthesise a third arrow |
| Trafford Road A5063 | OSM ways `4189397` and `4419709` are different sampled positions | Sealed current order, schedules/maps and subsequent changes matched to each precise segment/approach | Existing Salford 2020/2021 notices cited by the app register describe proposals; they do not prove current 2026 restrictions or applicability to either way |
| Welsh speed limits | Generic `GB:WLS:urban` policy tests only | Freeze an actual road cohort, current signed exceptions and effective local orders, vehicle class and date/time applicability | GOV.WALES national default guidance does not prove each particular road is restricted or an exception |

## Evidence required before marking a case verified

1. Preserve the exact official document URL, retrieval time, content bytes or a lawful bounded archival receipt, SHA-256, issuer, title and document/order identifier. Record retrieval failures and access restrictions independently.
2. Record the road/approach/direction, source way IDs and geometry binding, claimed lane/turn/speed fact, vehicle class, effective-from/to and conditional time applicability. Distinguish proposal, sealed order, operative order and physical marking observation.
3. Preserve a reviewable excerpt/page/schedule reference establishing the fact, independent dated corroboration when markings are asserted, and contradictions/revocations. Missing or conflicting facts remain UNKNOWN/PROVISIONAL; never infer legality from OSM or an engine agreeing with itself.
4. Record rights for retaining and distributing the exact evidence. Official-domain status is not by itself redistribution permission; keep restricted documents out of public test artifacts.
5. Bind the accepted truth to the frozen map/app/test cohort and compare the actual consumer decision separately. Report correct/eligible, failed, unknown and provisional without counting schema validation as a legal pass.

No web fetch, document hash, legal conclusion, new executable test, release approval or real-drive observation is claimed by this preparation. Next executable work is a bounded acquisition for the P0 case, followed by independent review and a road-specific assertion only when the necessary facts are authenticated.
