# ATTRIBUTE-DRIVEN DESIGN DOCUMENT
## Tripto Hotel Booking System

**Prepared by:**
* Hoang Quang Nghia
* Vo Xuan Ngoc

---

## Table of Contents
1. [Design Constraints](#1-design-constraints)
2. [Quality Attribute Requirements](#2-quality-attribute-requirements)
   - 2.1. [Security](#21-security)
   - 2.2. [Performance](#22-performance)
   - 2.3. [Reliability](#23-reliability)
   - 2.4. [Modifiability](#24-modifiability)
   - 2.5. [Auditability](#25-auditability)
   - 2.6. [Scalability](#26-scalability)
   - 2.7. [Interoperability](#27-interoperability)
   - 2.8. [Availability](#28-availability)
3. [Architectural Representation](#3-architectural-representation)
   - 3.1. [Logical View](#31-logical-view)
   - 3.2. [Implementation View](#32-implementation-view)
   - 3.3. [Deployment View](#33-deployment-view)
   - 3.4. [Data View](#34-data-view)
4. [Implementation Notes](#4-implementation-notes)

---

## 1. Design Constraints

* **Scalability:** The system must support up to 150 concurrent users (CCU) during peak booking operations. The storage architecture must scale to manage approximately 500,000 to 1,000,000 business records and 50,000 to 100,000 media/support files, with an estimated data growth rate of 50MB to 200MB per day from hotel media, booking records, payment records, reviews, reports, and policy documents.
* **Performance:** Search results must be returned within 2 seconds under normal load, and at least 95% of complex search queries should satisfy this response-time target. Heavy reporting tasks must not degrade the performance of the primary booking flow.
* **Security:** The system must enforce strong password hashing, OTP authentication with a 10-minute validity period, JWT-based API security, and strict Role-Based Access Control (RBAC) to isolate customer, hotel owner, and administrator operations.
* **Reliability & Consistency:** The system must prevent double-booking scenarios. Booking, room hold, and VNPay payment states must remain consistent so that successful payments confirm valid bookings and failed payments never incorrectly confirm a booking.
* **Availability:** The system must maintain at least 95% uptime during business hours. Major deployments and maintenance activities are restricted to a fixed Sunday 23:00 maintenance window.
* **Modifiability:** The architecture must support dynamic configuration for system regulations, such as platform service fees, point conversion rules, hold expiry time, and pricing policies, without requiring application restarts.

---

## 2. Quality Attribute Requirements

### 2.1. Security

#### Password Policy & Storage
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Users create new accounts or request to reset their passwords. |
| **Stimulus source** | Customers, Hotel Owners, Admins. |
| **Environment** | Normal system operation under potential risk of brute-force attacks. |
| **Artifact** | Identity & Access Management Module, User Service, Auth Service, API Gateway. |
| **Response** | Hash passwords using a secure one-way hashing algorithm such as bcrypt or Argon2. Generate a 6-digit OTP via email with a strict 10-minute validity period. Issue signed JWTs upon successful login and validate protected requests based on user role. Rate limiting can be added at the API Gateway as the system evolves. |
| **Response measure** | 100% of passwords are stored as secure hashes. OTPs remain valid for 10 minutes only. Illegal or unauthorized requests are rejected with the appropriate authentication or authorization error. |

#### Authentication Integrity
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Users request an OTP during the registration or password recovery process. |
| **Stimulus source** | Customers, Hotel Owners, Admins. |
| **Environment** | Normal system operation. |
| **Artifact** | IAM Module, User Service, Auth Service, Notification Service, Message Broker. |
| **Response** | Securely generate, dispatch, and validate a 6-digit OTP. The system enforces a 10-minute expiration window and limits repeated invalid verification attempts. OTP delivery is decoupled from the main user flow through asynchronous notification processing. |
| **Response measure** | OTPs expire after 10 minutes. Invalid OTP attempts are limited. OTP notification failures do not corrupt account state. |

#### Role-Based Authorization (RBAC)
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Hotel Owners attempt to view, modify, or manage bookings, hotels, reviews, and room details. |
| **Stimulus source** | Hotel Owners. |
| **Environment** | Normal operation within the Hotel Owner dashboard. |
| **Artifact** | API Gateway, Hotel Service, Booking Service, Revenue Service. |
| **Response** | Strictly enforce data isolation using RBAC. The system extracts the user's role and identity from the JWT or trusted request context and validates ownership against the requested hotel, room, booking, review, or report resource to prevent Insecure Direct Object Reference (IDOR). |
| **Response measure** | 100% of unauthorized cross-owner data access attempts are blocked with HTTP 403 Forbidden or an equivalent authorization error. |

#### Input Sanitization
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Customers submit hotel reviews or other user-generated text content. |
| **Stimulus source** | Customers. |
| **Environment** | Normal system operation during review submission. |
| **Artifact** | Review Module, Hotel Service, Frontend Validation Layer. |
| **Response** | Validate review content before storing it. If profanity filtering is required, inappropriate terms must be rejected. The system should also neutralize or reject script-like input to reduce the risk of stored XSS. |
| **Response measure** | Malicious scripts are neutralized or rejected. Inappropriate terminology is rejected when profanity filtering is enabled. |

#### File Upload Integrity
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Hotel Owners upload hotel images or business verification documents; Admins manage platform policy documents. |
| **Stimulus source** | Hotel Owners, Admins. |
| **Environment** | Normal system operation during media or document upload. |
| **Artifact** | Hotel Owner Portal, Media Service, Object Storage. |
| **Response** | Uploaded media and documents must be validated by MIME type, allowed extension, and file size before storage. Executable or unsafe file types are rejected, and accepted files are stored outside the transactional database in object storage. |
| **Response measure** | 0 invalid executable file types are accepted into object storage. Accepted uploads have valid metadata and retrievable storage URLs. |

---

### 2.2. Performance

#### Login Latency
| Element | Statement |
| :--- | :--- |
| **Stimulus** | A registered user submits valid login credentials. |
| **Stimulus source** | Customers, Hotel Owners, Admins. |
| **Environment** | Normal system load. |
| **Artifact** | Auth Service, User Service, API Gateway, JWT Service. |
| **Response** | Validate credentials, verify account status, generate a JWT, and return role-based session information without unnecessary cross-service calls. Frequently used identity checks can be optimized through indexing or caching as the system grows. |
| **Response measure** | Login completes within 500ms under normal load for valid credentials. |

#### Latency / Search Optimization
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Customers search and filter hotels based on destination, dates, room quantity, guest capacity, and price. |
| **Stimulus source** | Customers, Guest Users. |
| **Environment** | Normal search operations, with future support for peak booking periods of up to 150 concurrent users. |
| **Artifact** | Search & Discovery Module, Hotel Service, API Gateway, Search Index. |
| **Response** | Return paginated search results without causing UI lag. The current implementation can query relational hotel/location data, while the architecture allows Elasticsearch or another search index to be introduced for larger data volume and more complex filtering. |
| **Response measure** | Search results are returned within 2 seconds under normal load; at least 95% of complex search requests should satisfy this target after search-index optimization is introduced. |

#### Throughput / Batch Processing
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Admins and Hotel Owners generate revenue, occupancy, and platform overview reports. |
| **Stimulus source** | Admins, Hotel Owners. |
| **Environment** | Normal operations, with data growth of 50MB to 200MB per day. |
| **Artifact** | Revenue Service, Analytics & Reporting Module, Transactional Database, future Read-Replica/CQRS model. |
| **Response** | Aggregate transactional data such as bookings, payments, revenue records, and reviews without blocking active customer booking sessions. The architecture can evolve toward read replicas or CQRS when reporting load grows. |
| **Response measure** | Reports are generated in under 10 seconds for expected project-scale datasets. Report generation does not noticeably degrade active search or booking flows. |

#### Concurrency
| Element | Statement |
| :--- | :--- |
| **Stimulus** | A high number of users interact with the platform at the same time. |
| **Stimulus source** | Customers, Hotel Owners, Admins. |
| **Environment** | Peak holiday seasons or promotional events. |
| **Artifact** | API Gateway, Eureka Service Registry, Microservices, Load Balancer. |
| **Response** | Distribute traffic through the API Gateway and service-discovery infrastructure. The architecture supports horizontal scaling of service instances behind a load balancer as deployment maturity increases. |
| **Response measure** | System maintains an average response time below 3 seconds under 150 CCU for core customer workflows. |

---

### 2.3. Reliability

#### Concurrency Control
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Multiple customers attempt to book the last available room of the same room type simultaneously. |
| **Stimulus source** | Customers. |
| **Environment** | High-concurrency booking scenario. |
| **Artifact** | Booking Service, Hotel Availability Module, Transactional Database. |
| **Response** | Use database-level transaction boundaries and optimistic or pessimistic locking during availability checks and room-hold updates. The system increases held inventory only when enough rooms remain available for every date in the requested stay period. |
| **Response measure** | Double-booking rate is 0%. Room availability remains accurate after concurrent booking attempts. |

#### Distributed Transactions
| Element | Statement |
| :--- | :--- |
| **Stimulus** | A customer proceeds to pay for a booking through VNPay. |
| **Stimulus source** | VNPay Gateway, Customers. |
| **Environment** | Cross-service transaction during the booking and payment flow. |
| **Artifact** | Booking Service, Payment Module, Hotel Availability Module, VNPay Integration. |
| **Response** | Maintain consistency between booking status, payment status, and room-hold status. If VNPay payment succeeds, the booking is confirmed and the hold is finalized. If payment fails, the booking remains unconfirmed and the room hold remains active until its expiration so the customer can retry. If the hold expires without successful payment, the booking and active payment attempt are cancelled. |
| **Response measure** | A customer is never charged for a booking that is not reserved. VNPay success, VNPay failure, and hold-expiration states are reflected consistently in the booking database. |

#### Asynchronous Task Execution
| Element | Statement |
| :--- | :--- |
| **Stimulus** | A temporary room hold expires without a successful payment completion. |
| **Stimulus source** | System Timer / Scheduled Task. |
| **Environment** | Continuous background processing. |
| **Artifact** | Booking Service, Hotel Availability Module, Scheduler, Message Broker. |
| **Response** | Scheduled background tasks detect expired holds, cancel pending bookings and active payment attempts, and release room inventory. A delayed-message approach can be introduced later if the system needs finer-grained expiration handling. |
| **Response measure** | Expired room holds are released automatically without manual intervention. Booking/payment cleanup is completed within the configured scheduled-processing interval. |

---

### 2.4. Modifiability

#### Dynamic Configurability
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Admins update system regulations such as platform commission rate, point conversion, price limits, or hold expiry time. |
| **Stimulus source** | Admins. |
| **Environment** | System runtime. |
| **Artifact** | Regulations Service, Config Server, Local/Distributed Cache, Message Broker. |
| **Response** | Store system rules centrally and propagate changes to dependent services through cache refresh or event-based notification. The new values should take effect without restarting the application. |
| **Response measure** | Configuration changes take effect system-wide in under 10 seconds with no downtime for active users. |

#### Payment Method Extension
| Element | Statement |
| :--- | :--- |
| **Stimulus** | The business adds a new payment method in addition to VNPay and pay-at-hotel. |
| **Stimulus source** | Product Owner, Development Team. |
| **Environment** | Planned feature enhancement during system evolution. |
| **Artifact** | Booking Service, Payment Module, Payment Creation Policy/Factory, Payment Repository. |
| **Response** | Encapsulate payment-method-specific creation and validation rules behind a payment strategy, factory, or policy layer. Adding a new payment method should require adding a new payment handler and configuration, while preserving the core booking creation and room-hold logic. |
| **Response measure** | A new payment method can be added without changing the core booking creation flow. Payment-specific changes remain localized to the payment module. |

---

### 2.5. Auditability

#### Action Traceability & Workflow
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Admins perform sensitive actions such as activating/deactivating accounts, approving/rejecting hotel registrations, or updating platform regulations. |
| **Stimulus source** | Admins. |
| **Environment** | Normal system operation. |
| **Artifact** | Admin Governance Module, Regulations Service, Notification Service, Database. |
| **Response** | Log sensitive administrative actions with Admin ID, target resource, timestamp, action type, and reason when applicable. For user-visible decisions such as rejection or account deactivation, the system should asynchronously notify the affected user or hotel owner. |
| **Response measure** | 100% of sensitive admin actions are traceable. User-facing administrative decisions trigger notification within the expected notification-processing window. |

---

### 2.6. Scalability

#### Storage Management
| Element | Statement |
| :--- | :--- |
| **Stimulus** | The system receives a continuous stream of uploaded hotel images, business documents, policy documents, reviews, bookings, and payment records. |
| **Stimulus source** | Customers, Hotel Owners, Admins. |
| **Environment** | Continuous daily data growth over the system's lifespan. |
| **Artifact** | Object Storage Architecture, Media Service, Transactional Database. |
| **Response** | Decouple binary files from the relational database by storing images and documents in object storage such as Supabase Storage, AWS S3, or MinIO, while keeping only metadata and URLs in service databases. Business data remains in the transactional database and can be partitioned or archived as the dataset grows. |
| **Response measure** | The system scales to approximately 500,000 to 1,000,000 business records and 50,000 to 100,000 media/support files, with 50MB to 200MB of expected daily data growth. File retrieval latency remains below 1 second for normal media access. |

---

### 2.7. Interoperability

#### External Service Fault Tolerance
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Third-party services, such as email providers or VNPay, experience timeouts, errors, or downtime. |
| **Stimulus source** | External Services. |
| **Environment** | Registration, password reset, notification, and payment workflows. |
| **Artifact** | Notification Service, Booking Service, Payment Module, Message Broker. |
| **Response** | Handle external service failures gracefully using asynchronous messaging, retry logic, clear failure states, and user-facing error messages. Payment failures must not confirm bookings, and notification failures must not crash the main application flow. |
| **Response measure** | 0% application crashes due to external API timeouts. Failed notifications or external calls are logged and retried or surfaced through a controlled failure response. |

#### VNPay Integration
| Element | Statement |
| :--- | :--- |
| **Stimulus** | VNPay is unavailable, returns an error response, or redirects back with a failed payment result. |
| **Stimulus source** | VNPay Gateway, Customers. |
| **Environment** | Online payment flow after a room hold has been created. |
| **Artifact** | Booking Service, Payment Module, VNPay Utility, Hotel Availability Module. |
| **Response** | The system validates VNPay callback parameters, maps VNPay response codes to internal payment states, and prevents failed or unavailable payment attempts from confirming the booking. When payment fails, the booking remains unconfirmed and the existing room hold is kept until expiration so the customer can retry within the allowed time window. |
| **Response measure** | VNPay failures result in FAILED payment state and do not create CONFIRMED bookings. Successful VNPay callbacks confirm the booking and finalize the room hold exactly once. |

---

### 2.8. Availability

#### System Uptime & Maintenance
| Element | Statement |
| :--- | :--- |
| **Stimulus** | Developers deploy new code, or system maintenance is required. |
| **Stimulus source** | DevOps Team. |
| **Environment** | Production environment. |
| **Artifact** | Deployment Infrastructure, CI/CD Pipeline, API Gateway, Service Registry. |
| **Response** | Maintain a 95% availability standard during business hours. Schedule major deployment and maintenance operations during the Sunday 23:00 maintenance window. Blue/Green deployment can be introduced as the production infrastructure matures. |
| **Response measure** | At least 95% system uptime. No planned user disruption outside the scheduled maintenance timeframe. |

#### Fault Tolerance
| Element | Statement |
| :--- | :--- |
| **Stimulus** | A non-critical service, such as Review Service or recommendation-related functionality, crashes or times out while a customer is searching for or booking a hotel. |
| **Stimulus source** | Users. |
| **Environment** | Normal operation with localized microservice failure. |
| **Artifact** | Review Module, API Gateway, Frontend UI, Circuit Breaker / Graceful Degradation Layer. |
| **Response** | Apply graceful degradation so core search and booking flows continue to operate. The frontend hides the unavailable review section or displays a temporary unavailable state instead of blocking the hotel detail or booking flow. Circuit Breaker support can be added to service calls as the system evolves. |
| **Response measure** | Core search and booking flows remain usable even when secondary features fail. |

#### System Recoverability
| Element | Statement |
| :--- | :--- |
| **Stimulus** | A core processing node, such as Booking Service or Hotel Service, or a database node crashes unexpectedly while customers are using the platform. |
| **Stimulus source** | System/hardware/network failure affecting active users. |
| **Environment** | System runtime. |
| **Artifact** | Load Balancer, Microservices, Service Registry, Database Backup/Replica Strategy. |
| **Response** | Use service health checks and service discovery to route traffic to healthy instances where replicas exist. For major database failure, recover from backups or replicas according to the defined recovery objective. |
| **Response measure** | Users experience minimal disruption when a service replica is available. For major database failure, the system targets RTO < 4 hours and RPO < 1 hour. |

---

## 3. Architectural Representation

### 3.1. Logical View
The architecture is decomposed into independent services and modules:
* **Identity & Access Management (IAM):** Handles registration, login, JWT issuance, OTP generation/verification, password hashing, and role-based access control.
* **Search & Discovery Module:** Handles hotel, location, date, guest, and room-quantity search. The current system can query relational hotel/location data, while the architecture allows a future search index such as Elasticsearch for advanced search optimization.
* **Booking Engine Service:** The transactional core managing booking creation, room holds, hold expiration, booking status transitions, and cancellation.
* **Payment Module:** A module inside the Booking Service responsible for VNPay integration, pay-at-hotel payment records, payment callback handling, and payment/booking reconciliation.
* **Hotel Service:** Exposes APIs for Hotel Owners to manage hotels, room types, policies, media metadata, availability, and hotel review visibility.
* **Media Service:** Stores and retrieves uploaded hotel images and support documents using object storage.
* **Revenue Service:** Aggregates payment, booking, and hotel data for hotel-owner and admin reports.
* **Regulations Service:** Stores configurable system policies such as commission rate, point conversion, and hold expiry settings.
* **Notification Service:** Processes asynchronous email notifications such as OTP and booking/payment-related messages.
* **API Gateway:** The single entry point responsible for routing requests, central CORS handling, service discovery integration, and initial API security controls.

### 3.2. Implementation View
* **Project Structure:** The frontend and backend codebases are separated. The backend follows a Microservices design pattern.
* **Backend Technologies:** Java Spring Boot, Spring Cloud Gateway, Spring Cloud Config, Eureka, Spring Data JPA, gRPC, RabbitMQ.
* **Frontend Technologies:** React/Next.js for Customer, Hotel Owner, and Admin interfaces; Tailwind CSS for styling.
* **Data & Storage Technologies:** PostgreSQL for transactional data; Supabase Storage or S3-compatible object storage for uploaded media and documents.
* **Security Libraries:** Spring Security, JWT, bcrypt password hashing, validation libraries such as Hibernate Validator.
* **Future Extension Points:** Elasticsearch for search optimization, Redis for token blacklist/session cache/OTP cache/delayed jobs, Circuit Breaker support for service-to-service fault tolerance, and read replicas/CQRS for reporting scalability.

### 3.3. Deployment View
* **Environment:** Cloud or VPS deployment target for production; local Docker-based infrastructure for development services such as RabbitMQ.
* **Orchestration:** Services are containerization-ready. Docker Compose can support local development, while Kubernetes, Docker Swarm, or managed container platforms can be adopted for production scaling.
* **Service Discovery:** Eureka is used so services can register and discover each other dynamically.
* **Load Balancer:** API Gateway, Nginx, or a cloud load balancer distributes traffic to support 150+ CCU as the deployment scales horizontally.
* **High Availability (HA):** Application nodes can run in parallel replicas for critical services such as API Gateway, Booking Service, Hotel Service, and Revenue Service.
* **Updates:** Major updates are scheduled during the Sunday 23:00 maintenance window. Blue/Green deployment is a target strategy for zero-downtime releases when production infrastructure supports it.

### 3.4. Data View
* **Transactional DB:** PostgreSQL stores core user, hotel, room, booking, payment, revenue, regulation, and review data with ACID consistency.
* **Search Index:** Elasticsearch or an equivalent search index is a planned optimization for large-scale, multi-dimensional hotel search.
* **In-memory Cache:** Redis Cluster is a planned optimization for token blacklists, OTP/session caching, dynamic configuration cache, and delayed job handling.
* **Message Broker:** RabbitMQ supports asynchronous communication for OTP email, notification, booking, payment, revenue, and regulation-related events.
* **Object Storage:** Supabase Storage, AWS S3, or MinIO stores hotel images and uploaded documents. The database stores metadata and object URLs rather than binary files.

---

## 4. Implementation Notes

This ADD describes the intended architecture for the Tripto system, not only the subset currently implemented in the source code. Some quality-attribute tactics, such as Elasticsearch-based search optimization, Redis cache, Circuit Breaker, read replicas/CQRS, Kubernetes orchestration, and Blue/Green deployment, are architectural extension points planned for later iterations.

The current implementation already demonstrates the core architectural direction through Spring Boot microservices, API Gateway, Eureka service discovery, PostgreSQL transactional storage, RabbitMQ-based asynchronous messaging, Supabase object storage, room-hold scheduling, VNPay payment handling, and role-separated frontend workflows.
