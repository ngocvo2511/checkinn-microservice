# Hotel Booking Microservices System

A distributed hotel booking system using Microservices architecture with gRPC communication.

Docker local setup, actual service inventory, configuration, smoke checks and validation results: [docker/README.md](docker/README.md).

## Core Services
- **API Gateway** – Single entry point for all clients
- **Auth Service** – JWT authentication & login/register
- **User Service** – User info management (gRPC)
- **Hotel Service** – Hotel, room, and availability management (gRPC)
- **Booking Service** – Booking, payment, cancellation and recovery (REST / gRPC); payment is a module of this service
- **Media Service** – Media metadata and storage access
- **Revenue Service** – Authorized financial reports and event projections
- **Regulations Service** – Business rules and typed rate APIs
- **Notification Service** – RabbitMQ consumers and SMTP email
- **Config Server** – Centralized configuration management
- **Service Registry** – Service discovery (Eureka)
- **Admin Server** – Spring Boot service monitoring

## Technology Stack
- Spring Boot
- Spring Cloud (Gateway, Config, Eureka)
- gRPC
- Docker
- PostgreSQL (User, Hotel, Booking, Revenue and Regulations)
- RabbitMQ (events), Redis (session revocation and caches)
- Protobuf 3
