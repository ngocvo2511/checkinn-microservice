# Quality Attribute Utility Tree (Updated)

## 1. Performance (Hiệu năng)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **Latency / Search Optimization** | When a customer searches hotels by destination, dates, guests, and room quantity under normal load, the system should return search results within 2 seconds. | (High, High) |
| **Login Latency** | When a registered user submits valid credentials under normal load, the system should authenticate and return role-based access within 500ms. | (High, Medium) |
| **Concurrency** | The system architecture must support and efficiently handle up to 150 concurrent users interacting with the platform, specifically during peak booking operations. | (High, Medium) |
| **Complex Validation Processing** | When processing a multi-room booking, the system must dynamically validate that all selected rooms belong to the exact same room type and that the combined capacity meets the requested number of adults and children, without causing UI lag. | (Medium, Low) |
| **Throughput / Batch Processing** | When generating hotel or website reports, the system must aggregate large volumes of transactional data, including booking records, payment records, revenue records, and reviews, without degrading the response time of active customer sessions. | (Medium, High) |

---

## 2. Reliability (Độ tin cậy)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **Data Consistency / Distributed Transactions** | When a customer starts booking, the system must create a temporary room hold; if VNPay payment fails, the booking must remain unconfirmed and the hold must be kept until its expiration time to allow retry; if the hold expires without successful payment, the booking and active payment attempt must be cancelled. | (High, High) |
| **Asynchronous Task Execution** | When a room hold expires without successful payment, the booking service must periodically cancel expired PENDING or PENDING_PAYMENT bookings and their active payment attempts, while the hotel service must periodically release the expired room hold from room availability, without requiring manual intervention. | (High, Medium) |
| **Concurrency Control** | Multiple customers might attempt to book the last available room of a specific type simultaneously. The system must utilize optimistic or pessimistic locking at the database level during the availability check to ensure inventory is never over-allocated. | (High, High) |

---

## 3. Security (Bảo mật)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **Password Policy & Storage** | During account creation or password reset, the system must enforce a strong password policy (e.g., minimum 12 characters, breach validation) and securely hash credentials (e.g., Argon2, bcrypt) before storage to prevent unauthorized access. | (High, Medium) |
| **Authentication Integrity** | During user registration or password recovery, the system must securely generate, dispatch, and validate a 6-digit OTP strictly within a 10-minute expiration window. | (High, Low) |
| **Role-Based Authorization (RBAC)** | The system must strictly enforce data isolation; for example, a Hotel Owner must only be authorized to view, modify, and manage bookings/rooms belonging to their registered properties. | (High, High) |
| **Session & Token Management** | Upon successful login, the system generates an authentication token that is stored on the client side. The architecture must ensure this token is securely transmitted, strictly validated on every subsequent API request, and protected against interception or manipulation. | (High, High) |
| **Input Sanitization** | When a customer submits a hotel review, the system must automatically filter the input to detect and reject inappropriate terminology or profanity before storing it in the database. | (Medium, Low) |
| **File Upload Integrity** | When an Admin updates legal documents or the Help Center, the system must validate that uploaded files strictly conform to safe formats (.doc, .docx, .pdf) to prevent malicious script uploads. | (Medium, Medium) |

---

## 4. Availability (Độ khả dụng)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **System Uptime** | The system must be highly available to support continuous data exchanges between businesses and supply partners, maintaining a 95% availability standard during business hours. | (High, Medium) |
| **Maintenance Window** | The architecture must support deployment and maintenance operations exclusively during a designated 1-to-2-hour window on Sunday evenings at 11 PM, ensuring zero disruption outside this timeframe. | (Medium, Low) |
| **Fault Tolerance** | If a non-critical service (e.g., Review Service) crashes or times out, the core system must maintain operations, allowing users to continue searching and booking hotels without interruption. | (High, High) |
| **System Recoverability** | If a server node crashes suddenly while a user is interacting with the platform, the system must automatically redirect traffic (failover) to a backup node immediately to minimize disruption. | (High, High) |

---

## 5. Modifiability (Khả năng thay đổi)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **Dynamic Configurability** | When an Admin updates system regulations such as service fees or pricing limits, the new rules must take effect immediately across all relevant transactions without requiring a system restart. | (High, Medium) |
| **Data Cascading** | If an Admin deletes a category, the system must gracefully handle the relational data by automatically reassigning all affected posts to a default "other" category to prevent orphaned records. | (Medium, Low) |
| **Payment Method Extension** | When a new payment method is added, the payment flow should be extendable without changing the core booking creation logic. | (Medium, Medium) |

---

## 6. Auditability (Khả năng kiểm toán)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **Action Traceability & Workflow** | Highly sensitive actions, such as an Admin banning a user account, must be securely logged by the system, which must also automatically trigger an email notification to the affected user detailing the violation. | (Medium, Low) |

---

## 7. Scalability (Khả năng mở rộng)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **Storage Management** | The system must efficiently manage and retrieve approximately 500,000 to 1,000,000 business records and 50,000 to 100,000 media/support files, with an estimated data growth rate of 50MB to 200MB per day from hotel media, booking records, payment records, reviews, reports, and Help Center or policy documents. | (Medium, Medium) |

---

## 8. Interoperability (Khả năng tương thích ngoại vi)

| Attribute Refinement | Scenario / Description | Priority (Importance, Risk) |
| :--- | :--- | :--- |
| **External Service Fault Tolerance** | The system heavily relies on external services for sending OTPs and notification emails. The architecture must handle third-party service timeouts or failures gracefully, potentially utilizing message queues or automated retry mechanisms. | (High, Medium) |
| **VNPay Integration** | When VNPay is unavailable or returns an error, the system must handle the failure gracefully and prevent the booking from being incorrectly confirmed. | (High, High) |
