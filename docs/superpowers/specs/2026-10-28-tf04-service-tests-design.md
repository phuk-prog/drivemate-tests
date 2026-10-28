# TF-04 service testing capability

Owner scope: improve testing only; provider migration, accounts, graph publication and product fixes remain category-owned. Execution was requested for TF-04 after that scope correction.

Implementation choice: extend the existing Python/public Android workflow without new dependencies or production edits. A separate scratch probe would omit recurring evidence; changing app services would violate the supplied scope.

Provide a fixed reviewed public endpoint cohort with bounded HTTPS response/redirect handling, DNS/TLS/timeout/status classification, explicit auth/permission uncertainty, byte/hash/schema/expiry observations and bounded retry/recovery evidence. Never expose full URLs, keys or response bodies in public receipts. Bind context to exact app/test/run/attempt and category/batch selection. Provider results are observations, not harness success or navigation acceptance.

Exercise the actual app Http consumer on Android against an isolated TLS metadata test server. Cover status distinctions, bounded payload rejection, redirects, timeout/interruption and explicit recovery; restore temporary TLS test settings even on failure. Controlled metadata is test transport evidence, never a fabricated road or live-provider claim.

Acceptance: frozen meaningful Python RED→identical GREEN; real local-server transport tests; complete existing framework suite; actual public Android execution; all requested bounded receipts present and hash/context checked; provider failures/unknowns kept separate from test failures. Remaining individual provider credentials/data/entitlement or route-to-screen results stay in product categories.
