# Catalog — PASS5/5

docker/batch5b-catalog-verify.py compares every table/column name, format_type/nullability/default presence/identity against reviewed model plus5 documented unbounded-money decisions. Verifies explicit constraint count/validated state and all intended secondary/partial index names. Exact default expressions, CHECK/FK definitions, index predicates, sequences/history checksums captured in owner-catalog.json; [results](catalog-results.json).

| Owner | Tables | Columns | pg_constraint rows | Indexes | Sequences | Result |
|---|---:|---:|---:|---:|---:|---|
| user | 7 | 59 | 69 | 19 | 0 | PASS |
| hotel | 24 | 188 | 208 | 49 | 0 | PASS |
| booking | 7 | 118 | 110 | 28 | 1 | PASS |
| revenue | 5 | 66 | 66 | 13 | 0 | PASS |
| regulations | 3 | 27 | 33 | 8 | 2 | PASS |

PG18 NOT NULL kind n captured and verified through attnotnull for each column, separately from explicit constraint count. TIMESTAMP alias normalizes to timestamp without time zone. Real guards, negative uniqueness/nonnegative tests, JPA queries/workflows supplement structure. Rate0.1234567890123456789 and discount1.001 exact SQL roundtrip PASS. UUID types/raw Revenue TEXT exception verified. No cross-database SQL dependency.
