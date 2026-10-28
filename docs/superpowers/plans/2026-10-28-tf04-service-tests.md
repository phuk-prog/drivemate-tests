# TF-04 execution plan

1. Freeze ci/test_service_probe.py regressions for exact context/selection, bounded output, HTTP/auth/DNS/TLS/timeouts, redirect policy, payload/schema/hash/expiry and retry/recovery. Record focused and public RED before implementing the helper.
2. Implement ci/service_probe.py fixed-cohort probe_cohort(context,batch='all',endpoint_ids=None). CLI accepts identity/batch/output only, no arbitrary URL or credentials. Use only standard library; output no secrets/private response bytes.
3. Add controlled TLS Android tests against production Http in the historical downloads-category screen directory (current Category4). No production configuration/dependency edits. Actual consumer baseline is explicitly unrun until CI, not a claimed bug fix.
4. Wire the fixed cohort into the existing workflow once per selected batch, attach results/service-probe.json and a distinct summary service-observations block. Do not promote probe findings to product acceptance or weaken existing every-test-passed checks.
5. Run focused and full framework tests; inspect exact-head public Android CI and bounded artifacts, review branch independently, merge only verified changes. Record tested SHAs/counts and remaining category-owned findings; update the TF-04 capability status from evidence.

Review focus:403 without auth challenge must not imply missing credentials; truncated payload/schema and stale/unknown dates never become a pass; retries cannot retry permanent auth/not-found results; redirect targets cannot escape the reviewed cohort; Android TLS globals and server threads must always be restored/closed.
