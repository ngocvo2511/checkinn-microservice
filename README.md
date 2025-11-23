# Hotel Booking Microservices System

A distributed hotel booking system using Microservices architecture with gRPC communication.

## Services Included
- **API Gateway** – Single entry point for all clients
- **Auth Service** – JWT authentication & login/register
- **User Service** – User info management (gRPC)
- **Hotel Service** – Hotel, room, and availability management (gRPC)
- **Booking Service** – Room booking, cancellation, and scheduling (gRPC)
- **Payment Service** – Payment processing (REST / gRPC)
- **Notification Service** – Email / SMS notifications (gRPC)
- **Config Server** – Centralized configuration management
- **Service Registry** – Service discovery (Eureka/Consul)

## Technology Stack
- Spring Boot
- Spring Cloud (Gateway, Config, Eureka)
- gRPC
- Docker
- PostgreSQL / MongoDB
- Protobuf 3
