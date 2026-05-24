# Software Architecture Document (SAD)
## Tripto Hotel Booking System

**Architecture template:** arc42  
**Diagram model:** C4 model, written as PlantUML source files  
**Prepared by:** Hoang Quang Nghia, Vo Xuan Ngoc  
**Version:** 1.0

---

## Diagram Sources

The architecture diagrams are stored in a Structurizr DSL workspace so the C4 model can be edited and generated from one source file:

| Diagram | C4 View | DSL workspace | View key |
| :--- | :--- | :--- | :--- |
| System Context | Level 1 | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-SystemContext` |
| Container Diagram | Level 2 | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-Containers` |
| Booking and Payment Component Diagram | Level 3 | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-BookingPaymentComponents` |
| Hotel and Availability Component Diagram | Level 3 | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-HotelAvailabilityComponents` |
| Booking with VNPay Runtime Flow | Dynamic View | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-BookingVNPayDynamic` |
| Development Deployment View | Deployment View | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-DevelopmentDeployment` |
| Target Production Deployment View | Deployment View | [SAD_workspace.dsl](SAD_workspace.dsl) | `SAD-TargetProductionDeployment` |

The previous PlantUML files are kept as optional exports/reference files, but [SAD_workspace.dsl](SAD_workspace.dsl) is the primary diagram source.

---

# 1. Introduction and Goals

## 1.1 Requirements Overview

Tripto is a web-based hotel booking platform for Customers, Hotel Owners, and Administrators. The system supports hotel discovery, hotel detail browsing, room booking, temporary room holds, VNPay and pay-at-hotel payment flows, booking history, hotel reviews, hotel owner operations, revenue reporting, and platform administration.

The requirements are documented in:

| Document | Purpose |
| :--- | :--- |
| [BRD.docx](BRD.docx) | Business scope, actors, domain objects, use cases, security matrix. |
| [SRS.docx](SRS.docx) | Functional requirements, use case behavior, business rules, messages. |
| [user_stories.md](user_stories.md) | User-centered feature and quality expectations. |
| [ASR.md](ASR.md) | Architectural significant requirements mapped to modules. |
| [utility_tree.md](utility_tree.md) | Quality attribute utility tree with priorities. |
| [ADD.md](ADD.md) | Attribute-driven design scenarios and architectural tactics. |
| [ToDo.md](ToDo.md) | Known gaps between documentation and current implementation. |

Main functional areas:

| Area | Key responsibilities |
| :--- | :--- |
| Identity and Access Management | Register, login, forgot password, OTP verification, JWT issuance, role-based access. |
| Search and Discovery | Search hotels by destination, date, guest count, and room quantity; view details and reviews. |
| Booking and Payment | Create bookings, hold inventory, process VNPay or pay-at-hotel payments, manage booking status. |
| Hotel Owner Portal | Manage hotels, rooms, media, bookings, onsite payment confirmation, and customer review responses. |
| Content and Media | Store hotel media, hotel policies, reviews, and support documents. |
| Analytics and Reporting | Generate revenue, occupancy, and platform reports for Hotel Owners and Admins. |
| System Administration | Approve/reject hotels, manage users, update platform regulations. |

## 1.2 Quality Goals

| Priority | Quality goal | Concrete scenario |
| :--- | :--- | :--- |
| 1 | Reliability and consistency | Prevent double-booking and keep booking, hold, and payment states consistent across VNPay success, VNPay failure, and hold expiration. |
| 2 | Security | Enforce secure password hashing, OTP expiration, JWT validation, and strict owner/admin/customer authorization boundaries. |
| 3 | Performance | Return hotel search results within 2 seconds under normal load and complete login within 500ms under normal load. |
| 4 | Modifiability | Support dynamic regulations and future payment method extension without changing core booking creation logic. |
| 5 | Availability and fault tolerance | Keep search and booking usable when secondary functionality such as reviews or notifications is unavailable. |

## 1.3 Stakeholders

| Role | Expectations |
| :--- | :--- |
| Customer | Fast hotel search, safe booking, reliable payment feedback, booking history, and review submission. |
| Hotel Owner | Secure access to owned hotel data only, manageable hotel/room/media workflows, booking visibility, and revenue reports. |
| Administrator | Reliable approval, account governance, platform reporting, and regulation updates. |
| Development Team | Clear service boundaries, traceability to ASR/ADD/SRS, and guidance for planned future features. |
| Test/UAT Team | Observable functional flows, quality scenarios, and clear expected behavior for failure cases. |
| DevOps/Maintainer | Understandable deployment topology, service discovery, configuration, monitoring, and scaling strategy. |

---

# 2. Architecture Constraints

| Constraint | Description |
| :--- | :--- |
| Microservices backend | Backend code is organized as Spring Boot services: `api-gateway`, `auth-service`, `user-service`, `hotel-service`, `booking-service`, `media-service`, `revenue-service`, `regulations-service`, `notification-service`, `config-server`, `eureka-server`, and `admin-server`. |
| Web frontend | Frontend is a Next.js/React application in `checkinn-frontend`, using REST APIs through the API Gateway. |
| Service discovery | Services register with Eureka and are routed through Spring Cloud Gateway. |
| Transactional storage | Core data is stored in PostgreSQL using service-owned schemas/entities. |
| Asynchronous integration | RabbitMQ is used for OTP, payment, booking, revenue, notification, and regulation events. |
| External payment | VNPay is the online payment gateway; sensitive payment processing stays outside Tripto's main database. |
| Object storage | Uploaded hotel media/documents are stored outside the relational database using Supabase Storage or an S3-compatible object storage. |
| Quality targets | 150 CCU target, search within 2 seconds, login within 500ms, 95% business-hours uptime, Sunday 23:00 maintenance window. |
| Partial implementation | Some architectural tactics are planned for later iterations, including Redis, Elasticsearch, Circuit Breaker, Kubernetes, Blue/Green deployment, and read-replica/CQRS reporting. |

---

# 3. System Scope and Context

## 3.1 Business Context

Tripto is the central system used by:

| External actor/system | Interaction with Tripto |
| :--- | :--- |
| Guest / Customer | Searches hotels, views details, books rooms, pays, manages profile, reviews stays. |
| Hotel Owner | Manages hotels, room types, media, bookings, onsite payments, reviews, and revenue reports. |
| Administrator | Approves hotels, manages users, views platform reports, updates regulations. |
| VNPay | Receives payment requests and returns payment results. |
| Email Provider | Sends OTP and booking/payment notifications. |
| Supabase Storage / Object Storage | Stores hotel images and uploaded documents. |

Context diagram source: [SAD_workspace.dsl](SAD_workspace.dsl), view key `SAD-SystemContext`.

## 3.2 Technical Context

| Channel | Used by | Purpose |
| :--- | :--- | :--- |
| HTTPS/REST | Frontend to API Gateway | Main browser-to-backend communication. |
| Spring Cloud Gateway routes | API Gateway to services | Route `/api/auth`, `/api/user`, `/api/bookings`, `/api/payments`, `/api/reports`, `/api/regulations`, `/api/v1`, `/hotels`, and `/cities`. |
| gRPC | Auth/User, Hotel/User, Hotel/Media, Revenue/Hotel, internal booking/payment APIs | Efficient service-to-service calls for internal operations. |
| RabbitMQ | User, Booking, Regulations, Revenue, Notification | Asynchronous OTP, payment, booking, and regulation events. |
| PostgreSQL/JPA | Service data layer | Transactional persistence. |
| Supabase Storage API | Media Service | Binary object upload/delete/retrieve. |
| VNPay HTTPS flow | Booking Payment Module | Payment URL creation and payment return/callback processing. |
| SMTP/API | Notification Service | Email delivery. |

Container diagram source: [SAD_workspace.dsl](SAD_workspace.dsl), view key `SAD-Containers`.

---

# 4. Solution Strategy

| Quality goal | Scenario | Solution approach | Link to Details |
| :--- | :--- | :--- | :--- |
| Modifiability | New hotel, booking, payment, reporting, regulation, or notification features must be added without forcing one large monolithic change. | Decompose the backend into microservices by business capability: Auth, User, Hotel, Booking, Media, Revenue, Regulations, Notification, Gateway, Config, Eureka, and Admin Server. | [Building Block View](#5-building-block-view), [Design Decisions](#9-design-decisions) |
| Security | Customers, Hotel Owners, and Admins access different features and data scopes. Hotel Owners must not access other owners' hotels, bookings, reviews, or revenue. | Use API Gateway routing, JWT-based identity, service-level validation, and RBAC/ownership checks at resource boundaries. | [Cross-cutting Concepts - Security and Authorization](#81-security-and-authorization), [Quality Requirements](#10-quality-requirements) |
| Reliability | Multiple customers may try to book the last available room, and VNPay may fail or return an unsuccessful payment result. | Create a room hold before payment, store booking/payment states explicitly, validate active holds before payment attempts, and confirm/release holds through Hotel Service availability APIs. | [Runtime View](#61-booking-with-vnpay-payment), [Booking Service and Payment Module](#booking-service-and-payment-module) |
| Modifiability | The business wants to add future payment methods beyond VNPay and pay-at-hotel. | Keep payment behavior inside a Payment Module and use a payment creation policy/factory so new payment methods can be added locally. | [Booking Service and Payment Module](#booking-service-and-payment-module), [Design Decisions](#9-design-decisions) |
| Performance | Search should return results within 2 seconds, login should complete within 500ms, and the system should support 150 CCU. | Use API Gateway and stateless service instances with Eureka; keep search in Hotel Service today and allow a future Elasticsearch index and horizontal scaling through a load balancer/container platform. | [System Scope and Context](#3-system-scope-and-context), [Deployment View](#7-deployment-view), [Quality Requirements](#10-quality-requirements) |
| Scalability | Hotel media and support documents grow independently from transactional business data. | Store binary files in object storage through Media Service and keep only metadata/URLs in PostgreSQL. | [Cross-cutting Concepts - Media and File Handling](#86-media-and-file-handling), [Deployment View](#7-deployment-view) |
| Interoperability | OTP email, payment notification, revenue ingestion, and regulation propagation should not block user-facing flows. | Use RabbitMQ events to decouple notification, reporting, and regulation update processing from synchronous API requests. | [Cross-cutting Concepts - Messaging and Eventing](#84-messaging-and-eventing), [Runtime View](#6-runtime-view) |
| Availability | Secondary features such as reviews, notifications, or reporting may fail while search and booking should remain usable. | Apply graceful degradation in the frontend and plan Circuit Breaker support for service-to-service calls. Use scheduled maintenance and future Blue/Green deployment to reduce planned downtime. | [Deployment View](#7-deployment-view), [Cross-cutting Concepts](#8-cross-cutting-concepts), [Risks and Technical Debts](#11-risks-and-technical-debts) |
| Auditability | Sensitive Admin actions and regulation changes must be traceable. | Store regulation snapshots, publish regulation events, and plan a consistent audit log for Admin account/hotel/regulation actions. | [Cross-cutting Concepts - Data Ownership](#82-data-ownership), [Risks and Technical Debts](#11-risks-and-technical-debts) |

---

# 5. Building Block View

## 5.1 Whitebox Overall System

Overall decomposition is shown in the C4 Container Diagram in [SAD_workspace.dsl](SAD_workspace.dsl), view key `SAD-Containers`.

| Building block | Responsibility | Code location |
| :--- | :--- | :--- |
| Browser Client | Customer, Hotel Owner, and Admin web interface running in the user's browser. | `checkinn-frontend/` |
| Next.js Runtime | Serves Next.js pages, static assets, client bundle, and server-side API routes. | `checkinn-frontend/` |
| API Gateway | Routes frontend/API traffic to backend services through Eureka. | `checkinn/api-gateway/` |
| Auth Service | Login/register orchestration, JWT issuance, auth gRPC API. | `checkinn/auth-service/` |
| User Service | User accounts, profiles, OTP records, loyalty points, user gRPC API. | `checkinn/user-service/` |
| Hotel Service | Hotels, rooms, availability, room holds, policies, reviews, media metadata, city/province search. | `checkinn/hotel-service/` |
| Booking Service | Booking lifecycle, payment module, VNPay integration, hold validation, scheduled cleanup, events. | `checkinn/booking-service/` |
| Media Service | Upload/delete objects in Supabase Storage through gRPC. | `checkinn/media-service/` |
| Revenue Service | Event ingestion and owner/admin reports. | `checkinn/revenue-service/` |
| Regulations Service | Dynamic regulation values and regulation update events. | `checkinn/regulations-service/` |
| Notification Service | OTP and booking/payment email delivery. | `checkinn/notification-service/` |
| Eureka Server | Service registry. | `checkinn/eureka-server/` |
| Config Server | Centralized configuration source. | `checkinn/config-server/` |
| Admin Server | Operational monitoring through Spring Boot Admin. | `checkinn/admin-server/` |
| Circuit Breaker Module (planned) | Embedded resilience policy for gateway and service-to-service calls. | Planned |
| RabbitMQ | Asynchronous messaging. | `checkinn/docker/rabbitmq/definitions.json` |
| Monitoring Stack | Prometheus metrics, Grafana dashboards, Loki log storage, and Promtail log shipping. | `checkinn/monitoring/` |
| Service-owned PostgreSQL databases | Separate logical databases for User, Hotel, Booking, Revenue, and Regulations services. | `checkinn/*-service/src/main/resources/application.yml` |
| Elasticsearch (planned) | Internal search index for larger-scale hotel discovery and filtering. | Planned |

## 5.2 Level 2: Important Service Responsibilities

### Auth Service

Purpose: authenticate users, call User Service when needed, generate JWTs, and expose login/register/forgot-password endpoints.

Important interfaces:

| Interface | Purpose |
| :--- | :--- |
| REST `/api/auth/**` via API Gateway | Public authentication workflows. |
| gRPC client to User Service | Create and validate user account data. |
| JWT service | Signed token creation and validation. |

Quality notes: login latency target is 500ms under normal load; password data is stored as secure hashes in user-related persistence.

### User Service

Purpose: manage users, profiles, OTPs, loyalty points, and user data exposed to other services.

Important interfaces:

| Interface | Purpose |
| :--- | :--- |
| REST `/api/user/**`, `/api/loyalty-points/**` | Profile and loyalty operations. |
| gRPC user API | Internal user lookup and account operations. |
| RabbitMQ OTP events | Decouple OTP generation from email delivery. |
| Regulation cache/client | Use dynamic point conversion settings. |

### Hotel Service

Purpose: manage hotel content, room types, availability, room holds, reviews, policies, city/province discovery, and media metadata.

Component diagram source: [SAD_workspace.dsl](SAD_workspace.dsl), view key `SAD-HotelAvailabilityComponents`.

Key components:

| Component | Responsibility |
| :--- | :--- |
| HotelController / HotelServiceImpl | Hotel CRUD, owner operations, admin approval. |
| RoomTypeController / RoomTypeServiceImpl | Room type creation, update, activation/deactivation. |
| AvailabilityController / AvailabilityService | Availability check, room hold, confirm hold, release hold, expired hold cleanup. |
| ReviewController / ReviewService | Customer reviews, duplicate prevention, owner responses, review statistics. |
| MediaAssetController / MediaServiceImpl | Media metadata and upload/delete coordination with Media Service. |
| CityController / LocationService | Destination search by city/province. |
| UserGrpcClient | Reads owner/customer profile data from User Service for hotel approval and review display. |

External dependencies shown in this component view include API Gateway routing, Hotel Database ownership, Media Service/Supabase object storage, Booking Service availability calls, Revenue Service gRPC reads, Spring Boot Admin/Prometheus monitoring, and future Elasticsearch search indexing.

### Booking Service and Payment Module

Purpose: create bookings, hold rooms, manage payment attempts, integrate VNPay, support pay-at-hotel, publish booking/payment events, and cleanup expired holds.

Component diagram source: [SAD_workspace.dsl](SAD_workspace.dsl), view key `SAD-BookingPaymentComponents`.

Key components:

| Component | Responsibility |
| :--- | :--- |
| BookingController / BookingService | Booking creation, status updates, hold verification, booking queries. |
| PaymentController / PaymentService | VNPay creation/return, pay-at-hotel payment creation, payment confirmation. |
| PaymentCreationPolicyFactory | Extension point for new payment methods. |
| VnPayPaymentCreationPolicy / HotelPaymentCreationPolicy | Method-specific payment validation and creation. |
| BookingScheduledTask | Cancel expired PENDING/PENDING_PAYMENT bookings and active payments. |
| HotelAvailabilityClient | REST calls to Hotel Service availability APIs. |
| LoyaltyPointsClient | REST calls to User Service loyalty points APIs. |
| HotelEventPublisher | RabbitMQ payment and booking status events. |

External dependencies shown in this component view include API Gateway routing, Hotel Service availability APIs, User Service loyalty APIs, Booking Database ownership, RabbitMQ event publishing, VNPay redirect/signature integration, Spring Boot Admin/Prometheus monitoring, and future Redis delayed expiration support.

### Revenue Service

Purpose: consume booking/payment events, store reporting records, and provide admin/owner reporting APIs.

Important interfaces:

| Interface | Purpose |
| :--- | :--- |
| REST `/api/reports/**` | Admin and owner reports. |
| RabbitMQ listeners | Payment and booking status ingestion. |
| gRPC/REST clients to Hotel Service | Hotel and capacity context for reports. |
| Regulations client/cache | Commission and point-conversion data. |

### Regulations Service

Purpose: manage dynamic system regulations such as commission rate, point conversion, and hold expiry settings.

Important interfaces:

| Interface | Purpose |
| :--- | :--- |
| REST `/api/regulations/**` | Admin updates and service reads. |
| RabbitMQ regulation events | Notify dependent services when regulation values change. |
| PostgreSQL snapshots | Preserve regulation history for audit and rollback. |

### Notification Service

Purpose: consume OTP and booking/payment events and send emails through an external provider.

Important interfaces:

| Interface | Purpose |
| :--- | :--- |
| RabbitMQ listeners | `notification.otp.queue`, `notification.payment.queue`. |
| Email provider | Send OTP and payment/booking notifications. |

---

# 6. Runtime View

## 6.1 Booking with VNPay Payment

Dynamic diagram source: [SAD_workspace.dsl](SAD_workspace.dsl), view key `SAD-BookingVNPayDynamic`.

Architecturally significant behavior:

1. Customer creates a booking from the frontend.
2. Booking Service asks Hotel Service to create a temporary room hold.
3. Booking Service stores a PENDING booking with `holdId` and `holdExpiresAt`.
4. Customer selects VNPay.
5. Payment Module validates that the hold is active and creates a PENDING payment attempt.
6. Payment Module returns a signed VNPay payment URL.
7. VNPay returns payment result.
8. On success, payment becomes COMPLETED, booking becomes CONFIRMED, the hold is finalized, loyalty points are processed, and events are published.
9. On failure, payment becomes FAILED, booking returns to PENDING, and the hold remains until expiration to allow retry.

## 6.2 Login

1. User submits credentials through the Next.js frontend.
2. API Gateway routes the request to Auth Service.
3. Auth Service validates credentials through user data and account status.
4. Auth Service generates a JWT containing identity and role information.
5. Frontend stores the session/token and redirects based on role: Customer, Hotel Owner, or Admin.

Quality relevance: login latency target is 500ms under normal load; invalid credentials and unverified accounts must not reveal sensitive detail.

## 6.3 Hotel Search

1. Customer enters destination, dates, guest count, and room count.
2. Frontend calls Hotel/City APIs through API Gateway.
3. Hotel Service resolves city/province and hotel/room data.
4. Current implementation queries relational data; future architecture can add Elasticsearch for larger or more complex search.
5. Results are paginated and returned to the frontend.

Quality relevance: search should complete within 2 seconds under normal load.

## 6.4 Regulation Update Propagation

1. Admin updates a regulation through Regulations Service.
2. Regulations Service validates and stores the new value and snapshot.
3. Regulations Service publishes a regulation update event to RabbitMQ.
4. Dependent services refresh their local cache or read the new value on demand.
5. Future Redis/distributed cache support can reduce propagation latency and centralize cache invalidation.

Quality relevance: system rules should take effect without service restart.

## 6.5 Expired Hold Cleanup

1. Hotel Service periodically releases expired room holds.
2. Booking Service periodically cancels PENDING/PENDING_PAYMENT bookings whose hold has expired.
3. Active payment attempts for expired bookings are cancelled.
4. The system avoids manual intervention and prevents stale room inventory from remaining held forever.

Quality relevance: protects inventory accuracy and payment/booking consistency.

---

# 7. Deployment View

Deployment diagram source: [SAD_workspace.dsl](SAD_workspace.dsl), view keys `SAD-DevelopmentDeployment` and `SAD-TargetProductionDeployment`.

## 7.1 Current Development/Project Topology

| Node | Deployed artifacts |
| :--- | :--- |
| Browser | Runs the Browser Client downloaded from the Next.js runtime. |
| Next.js runtime | Hosts/deploys the Next.js Runtime for `checkinn-frontend`. |
| Spring Boot runtime | API Gateway, Auth, User, Hotel, Booking, Media, Revenue, Regulations, Notification, Config, Eureka, Admin Server, and planned Circuit Breaker module. |
| RabbitMQ Docker container | `hotel.events`, payment queues, notification queues. |
| Monitoring Docker Compose stack | Prometheus on `9090`, Grafana on `3001`, Loki on `3100`, Promtail log shipper. |
| PostgreSQL | Service-owned databases: `checkinn_user`, `checkinn_hotel`, `checkinn_booking`, `checkinn_revenue`, and `checkinn_regulations`. |
| Redis, Elasticsearch, and reporting read model | Separate optional local deployment nodes for cache/delayed jobs, search indexing, and CQRS/read-replica reporting. |
| Supabase Cloud | Uploaded media and document objects. |
| VNPay sandbox/production | External payment gateway. |
| Email provider | OTP and notification delivery. |

## 7.2 Target Production Evolution

| Planned element | Purpose |
| :--- | :--- |
| Nginx or Cloud Load Balancer | Route traffic to API Gateway replicas and support 150+ CCU. |
| Kubernetes / managed container platform | Run service replicas, health checks, rolling/Blue-Green deployment. |
| Redis Cluster | Token blacklist, OTP/session cache, dynamic config cache, delayed jobs. |
| Elasticsearch | Advanced hotel search and filtering. |
| Read replica / CQRS store | Isolate reporting reads from booking transactions. |
| Circuit Breaker | Isolate secondary-service failures from search and booking flows. |
| Prometheus/Grafana/Loki/Promtail | Production-grade metrics dashboards and centralized log viewing. |

---

# 8. Cross-cutting Concepts

## 8.1 Security and Authorization

* JWT is used to represent authenticated users and roles.
* API Gateway routes protected APIs to backend services; services also validate JWT or trusted identity context where needed.
* RBAC separates Customer, Hotel Owner, and Admin operations.
* Owner-scoped operations must validate hotel/resource ownership to prevent IDOR.
* Passwords are stored using a secure hash such as bcrypt.
* OTPs are 6-digit codes with a 10-minute validity period.
* Future improvements include token revocation/blacklist, rate limiting, and stronger password policy alignment.

## 8.2 Data Ownership

| Service | Owned database/storage | Owned data |
| :--- | :--- | :--- |
| User Service | `checkinn_user` | Users, profiles, OTPs, loyalty points, point transactions. |
| Hotel Service | `checkinn_hotel` | Hotels, room types, availability, room holds, reviews, policies, amenities, cities/provinces, media metadata. |
| Booking Service | `checkinn_booking` | Bookings, booking items, payment attempts, VNPay fields. |
| Revenue Service | `checkinn_revenue` | Reporting records derived from payment and booking events. |
| Regulations Service | `checkinn_regulations` | Regulation values and snapshots. |
| Media Service | Supabase Storage | Object storage integration; binary data stored externally. |

Auth Service does not own a separate database in the current implementation; it orchestrates authentication and uses User Service as the owner of account/profile data.

## 8.3 Consistency and Transaction Boundaries

* Room hold operations are transactional in Hotel Service.
* Booking creation stores hold information and booking state in Booking Service.
* VNPay success confirms both payment and booking, then finalizes the hold.
* VNPay failure keeps the booking unconfirmed and preserves the hold until expiration.
* Cross-service operations are coordinated through explicit service calls and events rather than distributed database transactions.

## 8.4 Messaging and Eventing

RabbitMQ decouples non-blocking work:

| Publisher | Event examples | Consumers |
| :--- | :--- | :--- |
| User Service | OTP email events | Notification Service |
| Booking Service | `payment.completed`, booking status changes | Revenue Service, Notification Service |
| Regulations Service | Regulation changed events | Revenue Service, User Service, future services |

## 8.5 Observability and Operations

* Spring Boot Actuator endpoints expose health and metrics.
* Spring Boot Admin Server is available for service monitoring.
* Eureka provides service registration and health-oriented discovery.
* Prometheus discovers services through Eureka and scrapes `/actuator/prometheus`.
* Grafana reads metrics from Prometheus and logs from Loki.
* Promtail tails Spring Boot log files from `logs/*.log` and pushes them to Loki.
* Future production operations should add alerting rules, dashboards, and distributed tracing.

## 8.6 Media and File Handling

* Binary files are stored in object storage, not in PostgreSQL.
* Hotel Service stores metadata and object URLs.
* Media Service is the integration boundary for Supabase/S3-compatible storage.
* File upload integrity requires MIME type, extension, and size validation.

## 8.7 Planned Extension Points

| Extension | Reason |
| :--- | :--- |
| Elasticsearch | Improve complex hotel search at larger scale. |
| Redis | Token blacklist, OTP/session cache, delayed hold expiration, dynamic config cache. |
| Circuit Breaker | Graceful degradation when secondary services fail. |
| Read replica/CQRS | Keep reporting workloads from slowing booking transactions. |
| Blue/Green deployment | Support maintenance window and reduce user-visible deployment disruption. |
| Payment method policy/factory | Add new payment methods without changing booking creation. |

---

# 9. Design Decisions

| ID | Context | Decision | Rationale | Consequence |
| :--- | :--- | :--- | :--- | :--- |
| ADR-01 | The platform has separate Customer, Hotel Owner, and Admin workflows across identity, hotel content, booking, payment, reporting, regulations, media, and notifications. | Use microservices by business capability. | Matches IAM, hotel, booking, reporting, regulation, media, and notification boundaries. | More deployment and integration complexity, but clearer ownership. |
| ADR-02 | Frontend clients need a stable backend entry point while backend service instances can change during development or deployment. | Use API Gateway and Eureka. | Provides one backend entry point and service discovery. | Gateway routing must stay synchronized with service APIs. |
| ADR-03 | Booking and payment state transitions are tightly coupled, especially for VNPay return handling and pay-at-hotel confirmation. | Keep payment logic inside Booking Service as a module. | Current code stores booking and payment lifecycle together and requires tight consistency. | Simpler current implementation; can be split later if payment complexity grows. |
| ADR-04 | Customers may compete for the last available room, and payment can be delayed, retried, or fail. | Use room holds before payment. | Prevents double-booking and supports VNPay retry. | Requires cleanup schedulers and cross-service hold checks. |
| ADR-05 | OTP email, payment notifications, reporting ingestion, and regulation propagation should not slow user-facing requests. | Use RabbitMQ for asynchronous notification/reporting/regulation events. | Prevents email/reporting work from blocking user-facing flows. | Requires event schemas, retry handling, and monitoring. |
| ADR-06 | Hotel media and supporting documents can grow faster than transactional data and should not bloat relational tables. | Store media in object storage. | Keeps PostgreSQL focused on transactional metadata and supports media growth. | Requires file validation and storage URL lifecycle management. |
| ADR-07 | The current implementation covers only part of the target features, while ASR/ADD include future quality scenarios. | Document planned tactics in the architecture. | The project is partially implemented, but ASR/ADD describe future quality targets. | SAD must clearly mark planned components to avoid confusing them with current code. |

---

# 10. Quality Requirements

## 10.1 Quality Tree Summary

Quality requirements are derived from [utility_tree.md](utility_tree.md) and [ADD.md](ADD.md).

| Quality attribute | Important scenarios |
| :--- | :--- |
| Performance | Search within 2 seconds, login within 500ms, 150 CCU, reporting without degrading booking. |
| Reliability | No double-booking, consistent booking/payment/hold states, automatic expired hold cleanup. |
| Security | Password hashing, OTP integrity, RBAC, token/session management, input/file validation. |
| Availability | 95% uptime, maintenance window, graceful degradation, recoverability. |
| Modifiability | Dynamic regulations, payment method extension, future data cascading. |
| Auditability | Trace sensitive admin actions and notify affected users when appropriate. |
| Scalability | 500,000 to 1,000,000 business records, 50,000 to 100,000 media/support files, 50MB to 200MB daily growth. |
| Interoperability | Email provider fault tolerance and VNPay failure handling. |

## 10.2 Quality Scenarios and Tactics

| Scenario | Tactic in architecture |
| :--- | :--- |
| Search results within 2 seconds | Current relational search plus future Elasticsearch/search index. |
| Login within 500ms | Auth/User service separation, JWT issuance, indexed user lookup, future cache where needed. |
| 150 CCU | API Gateway, Eureka, stateless service replicas, future load balancer/container platform. |
| Prevent double-booking | Transactional availability hold, database-level locking strategy, explicit hold status. |
| VNPay failure must not confirm booking | Payment state machine, callback validation, booking remains unconfirmed on failure. |
| Expired hold cleanup | Booking and Hotel scheduled tasks; future delayed queue/Redis enhancement. |
| New payment method | Payment policy/factory module inside Booking Service. |
| Regulation changes without restart | Regulations Service, events, local/distributed cache. |
| Review/file input protection | Content validation, MIME/extension/size validation, object storage boundary. |
| Secondary service failure | Graceful UI fallback and future Circuit Breaker. |

---

# 11. Risks and Technical Debts

| Risk / Debt | Impact | Mitigation |
| :--- | :--- | :--- |
| Some documented tactics are not yet implemented | Architecture can appear inconsistent with current code. | Keep planned items clearly marked; implement by priority from ASR/utility tree. |
| No complete server-side token revocation yet | Logout/session security may be weaker than target requirement. | Add token blacklist or refresh-token/session store, likely using Redis. |
| Search currently relies on relational queries | Complex search may not scale to target data volume. | Add Elasticsearch or equivalent search index when dataset grows. |
| Reporting may compete with transactional DB | Heavy reports can slow booking flow. | Add read replica/CQRS reporting store and event-driven projections. |
| Notification retry/dead-letter operations need monitoring | OTP/payment emails now use retry and DLQ configuration, but operators still need visibility into failed messages. | Monitor `notification.payment.dlq` and `notification.otp.dlq`; add alerting/dashboard rules before production. |
| Audit trail is incomplete for all admin actions | Accountability requirement may not be fully satisfied. | Add audit log model and enforce logging at admin service boundaries. |
| Circuit Breaker not fully implemented | Secondary service failure could affect core flows. | Add resilience4j or gateway/service-level fallback policies. |
| Some utility tree terms still need cleanup | Documentation may contain old marketplace wording. | Continue aligning utility tree/SRS/BRD/ADD with Tripto domain terms. |

---

# 12. Glossary

| Term | Definition |
| :--- | :--- |
| Tripto | Hotel booking platform documented in this SAD. |
| Customer | User who searches hotels, books rooms, pays, and reviews stays. |
| Hotel Owner | Business user who manages hotels, rooms, bookings, media, reviews, and revenue. |
| Admin | Platform administrator who manages approvals, accounts, reports, and regulations. |
| Room Hold | Temporary reservation of room inventory before payment confirmation. |
| VNPay | External online payment gateway used by Tripto. |
| Pay-at-hotel | Payment method where the customer pays onsite and the owner confirms payment. |
| OTP | One-time password used for email verification and password recovery. |
| JWT | Signed token used to authenticate API requests. |
| RBAC | Role-Based Access Control for Customer, Hotel Owner, and Admin permissions. |
| IDOR | Insecure Direct Object Reference, prevented by ownership validation. |
| RabbitMQ | Message broker used for asynchronous events. |
| Eureka | Service registry used by Spring Cloud services. |
| API Gateway | Spring Cloud Gateway service that routes public API traffic. |
| Supabase Storage | Current object storage provider for uploaded media/documents. |
| Elasticsearch | Planned search index for larger-scale hotel search. |
| Redis | Planned cache/delayed-job/token-blacklist infrastructure. |
| CQRS | Command Query Responsibility Segregation, planned for reporting scale-out. |
| C4 Model | Architecture diagram model using context, container, component, and dynamic/deployment views. |
| arc42 | Architecture documentation template used to structure this SAD. |
