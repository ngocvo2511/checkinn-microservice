# Five independent projects

**STATIC VERIFIED** target: five existing projects, topology already approved. **HOSTED VERIFIED: none**. No merge or new project proposed.

| Alias only | Owner | Tables / columns | Independent history | Hosted metadata |
|---|---|---|---|---|
| P_USER | User + Loyalty | 7 / 59 | public.flyway_schema_history | NOT VERIFIED |
| P_HOTEL | Hotel | 24 / 188 | public.flyway_schema_history | NOT VERIFIED |
| P_BOOKING | Booking + Payment/Refund | 7 / 118 | public.flyway_schema_history | NOT VERIFIED |
| P_REVENUE | Revenue | 5 / 66 | public.flyway_schema_history | NOT VERIFIED |
| P_REGULATIONS | Regulations | 3 / 27 | public.flyway_schema_history | NOT VERIFIED |

46 tables/458 columns exclude histories. Current V1 REFERENCES resolve within owner's tables. Revenue/Regulations have none. Reviewed source has one Boot datasource per service; no application dblink/postgres_fdw/2PC/JTA/chained multi-datasource configuration found. No cross-project SQL join/FK/transaction requirement in reviewed workflows; this static inspection does not certify deployed infrastructure or every dynamic query.

Boundaries: Booking HotelAvailabilityClient HTTP holds; Hotel review gRPC to Booking; User PointsBookingStateClient gRPC; Booking loyalty API; BookingEventOutbox/RegulationEventRecovery RabbitMQ; Revenue EventListener/EventIngestionService local transactions/advisory locks. @Transactional is owner-local, compensation/recovery distributed through durable commands/events.

Compose has five separate URL/user/password input slots but shared local credential defaults. Canonical variable names are shared across isolated process scopes, not necessarily values. Neither proves hosted separation.6B must compare all five owner→project bindings and runtime/migration secret references. Same pooler hostname or database name is not identity proof; pooler tenant login matters.

Missing private metadata: five owner mappings, region, mode/host/port/database/exact login from Connect panels, deployment IPv4/IPv6, separate secret references, server versions/limits/plans. Do not publish actual refs/endpoints. Verify authenticated control-plane metadata + TLS allowlist + catalog, publish alias/result/time/hash only. Actual endpoints/credentials/history/isolation all NOT VERIFIED.
