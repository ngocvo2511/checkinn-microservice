# Reproduce local Batch5B

From backend, Java21/Maven, PostgreSQL18 configured binary path, cached Docker images:

1. First cluster: python docker/batch5b-local-cluster.py start. Retained stopped cluster: restart. Never reset/delete existing data.
2. python docker/batch5b-run-acceptance.py creates five NEW owned timestamp/PID databases, guarded Flyway V1 and runtime validation/workflow tests; failed attempts retained.
3. Broad Maven package command in business-workflow-results.md; python docker/batch5b-catalog-verify.py; python docker/batch5b-security-verify.py.
4. First dependencies: python docker/batch5b-runtime-smoke.py dependencies. Retained stopped: restart-dependencies. Owned labels/names only.
5. python docker/batch5b-runtime-smoke.py start; check until UP5/5; bind (test alias to actual Regulation cache subscribers). Explicit canonical profile/JVMUTC, independent restricted credentials.
6. python docker/batch5b-http-acceptance.py; python docker/batch5b-restart-acceptance.py. Synthetic fixtures/runtime DML only, no provider calls/real mail.
7. python docker/batch5b-runtime-smoke.py stop; python docker/batch5b-local-cluster.py stop. Preserve containers/data/logs, no down -v/drop/clean.

Frontend commands: frontend-place-fix-results.md. Generated private credentials/ownership markers stay in ignored .local, never evidence. Arbitrary target/hosted deployment intentionally outside harness.

Raw `logs/` files and private connection material are ignored local artifacts, not published Git evidence. Linked JSON/Markdown summarizes the original execution; subsequent review checks are documented separately. The acceptance runner fails on Maven errors, absent totals or skipped canonical tests.
