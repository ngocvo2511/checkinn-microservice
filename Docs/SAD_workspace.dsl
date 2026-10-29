workspace "Tripto Hotel Booking System" "Software Architecture Document workspace using arc42 and C4 model views." {

    !identifiers hierarchical

    model {
        customer = person "Guest / Customer" "Searches hotels, books rooms, pays, manages profile, and submits reviews." "Customer"
        owner = person "Hotel Owner" "Manages hotels, room types, media, bookings, onsite payment, reviews, and revenue reports." "Hotel Owner"
        admin = person "Administrator" "Approves hotels, manages users, views platform reports, and updates regulations." "Administrator"

        vnpay = softwareSystem "VNPay" "External online payment gateway." "External System"
        emailProvider = softwareSystem "Email Provider" "External email delivery provider for OTP and booking/payment notifications." "External System"
        supabaseStorage = softwareSystem "Supabase Storage" "Object storage for hotel media and uploaded documents." "External System"

        tripto = softwareSystem "Tripto Hotel Booking System" "Web-based hotel booking platform for Customers, Hotel Owners, and Administrators." {
            frontend = container "Browser Client" "Customer, Hotel Owner, and Admin web UI running in the user's browser." "React 19, Next.js client bundle, Tailwind CSS" "Web Browser"
            nextRuntime = container "Next.js Runtime" "Serves Next.js pages, static assets, and server-side API routes." "Next.js 16, Node.js" "Web Application"
            apiGateway = container "API Gateway" "Single backend entry point, CORS, route mapping, service discovery integration, and future rate/security controls." "Spring Cloud Gateway" "Gateway"
            eurekaServer = container "Eureka Server" "Service registry for backend microservices." "Spring Cloud Netflix Eureka" "Infrastructure"
            adminServer = container "Admin Server" "Operational monitoring and actuator aggregation." "Spring Boot Admin" "Infrastructure"

            authService = container "Auth Service" "Login, register orchestration, forgot password, JWT issuance, and auth gRPC API." "Spring Boot, gRPC" "Service" {
                authController = component "AuthController" "Exposes login, register, OTP, and password recovery endpoints." "Spring REST Controller"
                authServiceComponent = component "AuthService" "Coordinates authentication workflows and user-service calls." "Spring Service"
                jwtService = component "JwtService" "Generates and validates signed JWTs." "Spring Service"
                userGrpcClient = component "UserGrpcClient" "Calls User Service over gRPC." "gRPC Client"
            }

            userService = container "User Service" "Users, profiles, OTP records, loyalty points, and user gRPC API." "Spring Boot, JPA, RabbitMQ, gRPC" "Service" {
                userController = component "UserController" "Exposes user and profile APIs." "Spring REST Controller"
                otpController = component "OtpController" "Exposes OTP verification APIs." "Spring REST Controller"
                loyaltyController = component "LoyaltyPointsController" "Exposes loyalty points APIs." "Spring REST Controller"
                userServiceComponent = component "UserService" "Manages user accounts and profiles." "Spring Service"
                otpService = component "OtpService" "Generates 6-digit OTPs and publishes OTP email events." "Spring Service"
                loyaltyService = component "LoyaltyPointsService" "Manages points balance and transactions." "Spring Service"
                userGrpcService = component "UserGrpcService" "Internal gRPC user API." "gRPC Service"
                userRepository = component "User/Profile/OTP/Loyalty Repositories" "Persists user, profile, OTP, and loyalty data." "Spring Data JPA"
                userRegulationCache = component "RegulationLocalCache" "Caches regulation values used by loyalty calculations." "RabbitMQ Listener / REST Client"
            }

            hotelService = container "Hotel Service" "Hotels, room types, availability, room holds, reviews, policies, city/province search, and media metadata." "Spring Boot, JPA, gRPC" "Service" {
                hotelController = component "HotelController" "Hotel lifecycle, owner operations, and admin approval APIs." "Spring REST Controller"
                roomTypeController = component "RoomTypeController" "Room type creation, update, activation, and deactivation APIs." "Spring REST Controller"
                availabilityController = component "AvailabilityController" "Availability check, hold, confirm hold, release hold, and get hold APIs." "Spring REST Controller"
                reviewController = component "ReviewController" "Review listing, creation, feedback, and owner response APIs." "Spring REST Controller"
                mediaAssetController = component "MediaAssetController" "Media metadata APIs." "Spring REST Controller"
                cityController = component "CityController" "City/province search and location APIs." "Spring REST Controller"
                policyController = component "PolicyController" "Hotel policy APIs." "Spring REST Controller"
                hotelGrpcService = component "HotelGrpcServiceImpl" "Internal hotel data gRPC API." "gRPC Service"
                reviewGrpcService = component "ReviewGrpcServiceImpl" "Internal review data gRPC API." "gRPC Service"

                hotelServiceComponent = component "HotelServiceImpl" "Hotel business logic, owner validation, and approval operations." "Spring Service"
                roomTypeService = component "RoomTypeServiceImpl" "Room type business logic." "Spring Service"
                availabilityService = component "AvailabilityService" "Transactional room availability, hold, confirm, release, and expired-hold cleanup." "Spring Service / Scheduler"
                reviewService = component "ReviewService" "Review creation, duplicate prevention, statistics, feedback, and owner responses." "Spring Service"
                mediaServiceComponent = component "MediaServiceImpl" "Coordinates media metadata and Media Service gRPC upload/delete." "Spring Service"
                locationService = component "LocationService / CityService" "City/province lookup and search." "Spring Service"
                userGrpcClient = component "UserGrpcClient" "Reads owner/customer profile data from User Service over gRPC." "gRPC Client"

                hotelRepositories = component "Hotel and Room Repositories" "Persists hotels, room types, amenities, policies, cities, and provinces." "Spring Data JPA"
                availabilityRepositories = component "Availability Repositories" "Persists room availability and room hold records." "Spring Data JPA"
                reviewRepositories = component "Review Repositories" "Persists hotel reviews, responses, and feedback." "Spring Data JPA"
                mediaRepository = component "MediaAssetRepository" "Persists media metadata and object URLs." "Spring Data JPA"
            }

            bookingService = container "Booking Service" "Booking lifecycle, payment module, VNPay integration, hold validation, scheduled cleanup, and event publishing." "Spring Boot, JPA, RabbitMQ, gRPC" "Service" {
                bookingController = component "BookingController" "Exposes booking creation, query, and status APIs." "Spring REST Controller"
                paymentController = component "PaymentController" "Exposes VNPay and pay-at-hotel payment APIs." "Spring REST Controller"
                bookingGrpcServer = component "BookingGrpcServer" "Internal booking gRPC API." "gRPC Service"
                paymentGrpcServer = component "PaymentGrpcServer" "Internal payment gRPC API." "gRPC Service"
                bookingServiceComponent = component "BookingService" "Booking creation, status transitions, active hold checks, and hold confirmation." "Spring Service"
                paymentService = component "PaymentService" "VNPay and pay-at-hotel payment lifecycle." "Spring Service"
                paymentPolicyFactory = component "PaymentCreationPolicyFactory" "Selects payment method creation policy." "Factory"
                vnpayPaymentPolicy = component "VnPayPaymentCreationPolicy" "VNPay-specific payment creation and validation rules." "Payment Policy"
                hotelPaymentPolicy = component "HotelPaymentCreationPolicy" "Pay-at-hotel payment creation and validation rules." "Payment Policy"
                bookingScheduledTask = component "BookingScheduledTask" "Cancels expired PENDING/PENDING_PAYMENT bookings and active payment attempts." "Spring Scheduler"
                hotelAvailabilityClient = component "HotelAvailabilityClient" "Calls Hotel Service availability APIs." "REST Client"
                loyaltyPointsClient = component "LoyaltyPointsClient" "Calls User Service loyalty points APIs." "REST Client"
                hotelEventPublisher = component "HotelEventPublisher" "Publishes payment and booking status events." "RabbitMQ Publisher"
                vnpayUtil = component "VnPayUtil / VnPayProperties" "Builds and signs VNPay payment URLs." "Utility / Configuration"
                bookingRepositories = component "Booking and Payment Repositories" "Persists bookings, booking items, and payments." "Spring Data JPA"
            }

            mediaService = container "Media Service" "Uploads and deletes object storage files through Supabase Storage." "Spring Boot, gRPC, Supabase HTTP API" "Service" {
                mediaGrpcService = component "MediaGrpcServiceImpl" "Internal gRPC API for media upload/delete." "gRPC Service"
                supabaseStorageService = component "SupabaseStorageService" "Calls Supabase Storage object API." "Spring Service"
                supabaseStorageConfig = component "SupabaseStorageConfig" "Configures Supabase URL, bucket, and credentials." "Configuration"
            }

            revenueService = container "Revenue Service" "Consumes booking/payment events and generates owner/admin reports." "Spring Boot, JPA, RabbitMQ, gRPC" "Service" {
                revenueController = component "RevenueReportController" "Exposes owner/admin reporting APIs." "Spring REST Controller"
                revenueReportService = component "RevenueReportService" "Aggregates revenue, occupancy, and platform metrics." "Spring Service"
                eventIngestionService = component "EventIngestionService" "Stores payment and booking status events for reporting." "Spring Service"
                revenueEventListener = component "EventListener" "Consumes payment and booking status events." "RabbitMQ Listener"
                hotelGrpcClient = component "HotelGrpcClient / HotelCapacityClient" "Reads hotel and capacity data." "gRPC/REST Client"
                revenueRegulationCache = component "RegulationLocalCache" "Caches regulation values for reporting calculations." "RabbitMQ Listener / REST Client"
                revenueRepositories = component "PaymentRecord and BookingStatusRecord Repositories" "Persists reporting records." "Spring Data JPA"
            }

            regulationsService = container "Regulations Service" "Dynamic platform regulations such as commission rate, point conversion, and hold expiry settings." "Spring Boot, JPA, RabbitMQ" "Service" {
                regulationController = component "RegulationController" "Exposes regulation read/update APIs." "Spring REST Controller"
                regulationServiceComponent = component "RegulationService" "Validates, updates, and snapshots regulation values." "Spring Service"
                regulationCacheProvider = component "RegulationCacheProvider" "Provides cached/default regulation values." "Spring Service"
                regulationEventPublisher = component "RegulationEventPublisher" "Publishes regulation update events." "RabbitMQ Publisher"
                regulationRepositories = component "Regulation Repositories" "Persists regulations and snapshots." "Spring Data JPA"
            }

            notificationService = container "Notification Service" "Sends OTP and booking/payment notification emails from RabbitMQ events." "Spring Boot, RabbitMQ, Email" "Service" {
                otpEmailListener = component "OtpEmailListener" "Consumes OTP email events." "RabbitMQ Listener"
                bookingPaymentListener = component "BookingPaymentListener" "Consumes payment notification events." "RabbitMQ Listener"
                emailNotificationService = component "EmailNotificationService" "Sends transactional emails." "Spring Service"
            }

            rabbitmq = container "RabbitMQ" "Message broker for OTP, payment, booking, revenue, notification, and regulation events." "RabbitMQ" "Message Broker"
            prometheus = container "Prometheus" "Scrapes service metrics from /actuator/prometheus using Eureka service discovery." "Prometheus" "Monitoring"
            grafana = container "Grafana" "Dashboards for metrics and logs." "Grafana" "Monitoring"
            loki = container "Loki" "Central log store for service log files." "Grafana Loki" "Monitoring"
            promtail = container "Promtail" "Tails Spring Boot log files and pushes them to Loki." "Grafana Promtail" "Monitoring"
            logFiles = container "Service Log Files" "Local Spring Boot log files written under logs/*.log." "Filesystem" "Log Store"
            userDb = container "User Database" "Users, profiles, OTPs, loyalty points, and point transactions owned by User Service." "PostgreSQL: checkinn_user" "Database"
            hotelDb = container "Hotel Database" "Hotels, room types, availability, holds, reviews, policies, cities, media metadata, and hotel content owned by Hotel Service." "PostgreSQL: checkinn_hotel" "Database"
            bookingDb = container "Booking Database" "Bookings, booking items, and payment attempts owned by Booking Service." "PostgreSQL: checkinn_booking" "Database"
            revenueDb = container "Revenue Database" "Payment records, booking status records, and report data owned by Revenue Service." "PostgreSQL: checkinn_revenue" "Database"
            regulationsDb = container "Regulations Database" "Regulation values and regulation snapshots owned by Regulations Service." "PostgreSQL: checkinn_regulations" "Database"
            redis = container "Redis Cluster" "Token blacklist, OTP/session cache, dynamic configuration cache, and delayed jobs." "Redis" "Cache, Planned"
            elasticsearch = container "Elasticsearch" "Search optimization for hotel discovery." "Elasticsearch" "Search Index, Planned"
            reportingStore = container "Read Replica / CQRS Store" "Reporting read model isolated from booking transactions." "PostgreSQL read replica / CQRS" "Reporting Store, Planned"
        }

        # System context relationships
        customer -> tripto "Searches hotels, books rooms, pays, manages profile, and submits reviews"
        owner -> tripto "Manages hotels, rooms, bookings, media, reviews, and reports using"
        admin -> tripto "Approves hotels, manages users, views reports, and updates regulations using"
        tripto -> vnpay "Creates payment requests and processes return/callback results using" "HTTPS"
        tripto -> emailProvider "Sends OTP and booking/payment emails using" "SMTP/API"
        tripto -> supabaseStorage "Stores and retrieves media/documents using" "HTTPS object API"

        # Container relationships
        customer -> tripto.frontend "Uses" "HTTPS"
        owner -> tripto.frontend "Uses" "HTTPS"
        admin -> tripto.frontend "Uses" "HTTPS"
        tripto.frontend -> tripto.nextRuntime "Loads pages, assets, and client bundle from" "HTTPS"
        tripto.frontend -> tripto.apiGateway "Calls APIs using" "HTTPS/REST"
        tripto.nextRuntime -> tripto.apiGateway "Calls backend APIs for server-side routes using" "HTTPS/REST"

        tripto.apiGateway -> tripto.authService "Routes /api/auth/** to" "Spring Cloud Gateway"
        tripto.apiGateway -> tripto.userService "Routes /api/user/** and /api/loyalty-points/** to"
        tripto.apiGateway -> tripto.hotelService "Routes /api/hotel/**, /api/v1/**, /hotels/**, /cities/** to"
        tripto.apiGateway -> tripto.bookingService "Routes /api/bookings/** and /api/payments/** to"
        tripto.apiGateway -> tripto.revenueService "Routes /api/reports/** to"
        tripto.apiGateway -> tripto.regulationsService "Routes /api/regulations/** to"
        tripto.apiGateway -> tripto.eurekaServer "Discovers service instances using"

        tripto.authService -> tripto.userService "Creates and validates users using" "gRPC"
        tripto.hotelService -> tripto.userService "Looks up owner/user data using" "gRPC"
        tripto.hotelService -> tripto.mediaService "Uploads/deletes media using" "gRPC"
        tripto.bookingService -> tripto.hotelService "Holds, confirms, releases, and checks availability using" "REST"
        tripto.bookingService -> tripto.userService "Uses loyalty points using" "REST"
        tripto.revenueService -> tripto.hotelService "Reads hotel/capacity/review data using" "gRPC/REST"
        tripto.revenueService -> tripto.regulationsService "Reads regulation values using" "REST"
        tripto.userService -> tripto.regulationsService "Reads point conversion values using" "REST"

        tripto.userService -> tripto.rabbitmq "Publishes OTP email events to"
        tripto.bookingService -> tripto.rabbitmq "Publishes payment and booking status events to"
        tripto.regulationsService -> tripto.rabbitmq "Publishes regulation update events to"
        tripto.rabbitmq -> tripto.notificationService "Delivers OTP/payment notification events to"
        tripto.rabbitmq -> tripto.revenueService "Delivers payment/booking events to"
        tripto.rabbitmq -> tripto.userService "Delivers regulation update events to"
        tripto.rabbitmq -> tripto.revenueService "Delivers regulation update events to"

        tripto.userService -> tripto.userDb "Reads/writes users, profiles, OTPs, and loyalty data using" "JPA"
        tripto.hotelService -> tripto.hotelDb "Reads/writes hotels, rooms, holds, reviews, policies, and media metadata using" "JPA"
        tripto.bookingService -> tripto.bookingDb "Reads/writes bookings and payments using" "JPA"
        tripto.revenueService -> tripto.revenueDb "Reads/writes reporting records using" "JPA"
        tripto.regulationsService -> tripto.regulationsDb "Reads/writes regulation values and snapshots using" "JPA"

        tripto.mediaService -> supabaseStorage "Uploads/deletes objects using" "HTTPS"
        tripto.bookingService -> vnpay "Builds payment URL and processes return/callback using" "HTTPS"
        tripto.notificationService -> emailProvider "Sends emails using" "SMTP/API"

        tripto.apiGateway -> tripto.redis "Uses token blacklist/rate limit cache" "Future"
        tripto.userService -> tripto.redis "Uses OTP/session cache" "Future"
        tripto.bookingService -> tripto.redis "Uses delayed hold expiration jobs" "Future"
        tripto.hotelService -> tripto.elasticsearch "Indexes/searches hotel data" "Future"
        tripto.revenueService -> tripto.reportingStore "Reads report projections" "Future"
    
        tripto.adminServer -> tripto.apiGateway "Monitors actuator endpoints"
        tripto.adminServer -> tripto.authService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.userService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.hotelService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.bookingService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.revenueService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.regulationsService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.notificationService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.mediaService "Monitors actuator endpoints"
        tripto.adminServer -> tripto.eurekaServer "Monitors actuator endpoints"
        tripto.prometheus -> tripto.eurekaServer "Discovers service instances using" "Eureka SD"
        tripto.prometheus -> tripto.apiGateway "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.authService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.userService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.hotelService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.bookingService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.mediaService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.revenueService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.regulationsService "Scrapes /actuator/prometheus"
        tripto.prometheus -> tripto.notificationService "Scrapes /actuator/prometheus"
        tripto.grafana -> tripto.prometheus "Queries metrics from"
        tripto.grafana -> tripto.loki "Queries logs from"
        tripto.promtail -> tripto.logFiles "Tails logs from"
        tripto.promtail -> tripto.loki "Pushes logs to"

        # Booking component relationships
        tripto.apiGateway -> tripto.bookingService.bookingController "Routes booking requests to" "REST"
        tripto.apiGateway -> tripto.bookingService.paymentController "Routes payment requests to" "REST"
        tripto.bookingService.bookingController -> tripto.bookingService.bookingServiceComponent "Uses"
        tripto.bookingService.paymentController -> tripto.bookingService.paymentService "Uses"
        tripto.bookingService.bookingGrpcServer -> tripto.bookingService.bookingServiceComponent "Uses"
        tripto.bookingService.paymentGrpcServer -> tripto.bookingService.paymentService "Uses"
        tripto.bookingService.bookingServiceComponent -> tripto.bookingService.hotelAvailabilityClient "Holds/confirms/releases rooms using"
        tripto.bookingService.bookingServiceComponent -> tripto.bookingService.loyaltyPointsClient "Checks available points using"
        tripto.bookingService.bookingServiceComponent -> tripto.bookingService.bookingRepositories "Reads/writes"
        tripto.bookingService.bookingServiceComponent -> tripto.bookingService.hotelEventPublisher "Publishes booking status events using"
        tripto.bookingService.paymentService -> tripto.bookingService.bookingServiceComponent "Ensures active hold and confirms hold using"
        tripto.bookingService.paymentService -> tripto.bookingService.paymentPolicyFactory "Selects payment creation policy using"
        tripto.bookingService.paymentPolicyFactory -> tripto.bookingService.vnpayPaymentPolicy "Uses"
        tripto.bookingService.paymentPolicyFactory -> tripto.bookingService.hotelPaymentPolicy "Uses"
        tripto.bookingService.paymentService -> tripto.bookingService.bookingRepositories "Reads/writes"
        tripto.bookingService.paymentService -> tripto.bookingService.vnpayUtil "Builds signed VNPay URL using"
        tripto.bookingService.paymentService -> tripto.bookingService.loyaltyPointsClient "Deducts/earns points using"
        tripto.bookingService.paymentService -> tripto.bookingService.hotelEventPublisher "Publishes payment/booking events using"
        tripto.bookingService.bookingScheduledTask -> tripto.bookingService.bookingRepositories "Cancels expired bookings/payments using"
        tripto.bookingService.bookingScheduledTask -> tripto.redis "Uses delayed expiration jobs" "Future"
        tripto.bookingService.bookingRepositories -> tripto.bookingDb "Persists booking and payment state in" "JPA"
        tripto.bookingService.hotelAvailabilityClient -> tripto.hotelService.availabilityController "Calls"
        tripto.bookingService.hotelAvailabilityClient -> tripto.hotelService "Calls availability APIs on"
        tripto.bookingService.loyaltyPointsClient -> tripto.userService.loyaltyController "Calls"
        tripto.bookingService.loyaltyPointsClient -> tripto.userService "Calls loyalty point APIs on"
        tripto.bookingService.hotelEventPublisher -> tripto.rabbitmq "Publishes to"
        tripto.bookingService.vnpayUtil -> vnpay "Creates signed redirect URL for"
        vnpay -> tripto.frontend "Redirects customer back with payment result"

        # Hotel component relationships
        tripto.apiGateway -> tripto.hotelService.hotelController "Routes hotel requests to" "REST"
        tripto.apiGateway -> tripto.hotelService.roomTypeController "Routes room type requests to" "REST"
        tripto.apiGateway -> tripto.hotelService.availabilityController "Routes availability requests to" "REST"
        tripto.apiGateway -> tripto.hotelService.reviewController "Routes review requests to" "REST"
        tripto.apiGateway -> tripto.hotelService.mediaAssetController "Routes media metadata requests to" "REST"
        tripto.apiGateway -> tripto.hotelService.cityController "Routes location/search requests to" "REST"
        tripto.apiGateway -> tripto.hotelService.policyController "Routes policy requests to" "REST"
        tripto.bookingService.hotelAvailabilityClient -> tripto.hotelService.availabilityController "Creates/confirms/releases holds using" "REST"
        tripto.revenueService.hotelGrpcClient -> tripto.hotelService.hotelGrpcService "Reads hotel/capacity data using" "gRPC"
        tripto.revenueService.hotelGrpcClient -> tripto.hotelService.reviewGrpcService "Reads review data using" "gRPC"
        tripto.hotelService.hotelController -> tripto.hotelService.hotelServiceComponent "Uses"
        tripto.hotelService.roomTypeController -> tripto.hotelService.roomTypeService "Uses"
        tripto.hotelService.availabilityController -> tripto.hotelService.availabilityService "Uses"
        tripto.hotelService.reviewController -> tripto.hotelService.reviewService "Uses"
        tripto.hotelService.mediaAssetController -> tripto.hotelService.mediaServiceComponent "Uses"
        tripto.hotelService.cityController -> tripto.hotelService.locationService "Uses"
        tripto.hotelService.hotelGrpcService -> tripto.hotelService.hotelServiceComponent "Uses"
        tripto.hotelService.reviewGrpcService -> tripto.hotelService.reviewService "Uses"
        tripto.hotelService.hotelServiceComponent -> tripto.hotelService.userGrpcClient "Loads owner profile using"
        tripto.hotelService.reviewService -> tripto.hotelService.userGrpcClient "Loads reviewer/owner profile using"
        tripto.hotelService.userGrpcClient -> tripto.userService.userGrpcService "Calls"
        tripto.hotelService.userGrpcClient -> tripto.userService "Reads user profile data from" "gRPC"
        tripto.hotelService.hotelServiceComponent -> tripto.hotelService.hotelRepositories "Reads/writes"
        tripto.hotelService.roomTypeService -> tripto.hotelService.hotelRepositories "Reads/writes"
        tripto.hotelService.availabilityService -> tripto.hotelService.availabilityRepositories "Reads/writes"
        tripto.hotelService.availabilityService -> tripto.hotelService.hotelRepositories "Reads total room data from"
        tripto.hotelService.reviewService -> tripto.hotelService.reviewRepositories "Reads/writes"
        tripto.hotelService.mediaServiceComponent -> tripto.hotelService.mediaRepository "Reads/writes"
        tripto.hotelService.mediaServiceComponent -> tripto.mediaService.mediaGrpcService "Uploads/deletes objects using" "gRPC"
        tripto.hotelService.locationService -> tripto.hotelService.hotelRepositories "Reads city/province/hotel data from"
        tripto.hotelService.hotelRepositories -> tripto.hotelDb "Persists hotel and room data in" "JPA"
        tripto.hotelService.availabilityRepositories -> tripto.hotelDb "Persists availability and hold data in" "JPA"
        tripto.hotelService.reviewRepositories -> tripto.hotelDb "Persists review data in" "JPA"
        tripto.hotelService.mediaRepository -> tripto.hotelDb "Persists media metadata in" "JPA"
        tripto.hotelService.hotelServiceComponent -> tripto.elasticsearch "Indexes approved hotel data in" "Future"
        tripto.hotelService.locationService -> tripto.elasticsearch "Queries optimized hotel search from" "Future"

        deploymentEnvironment "Development" {
            deploymentNode "Developer Machine" "Local development workstation." "Windows / local runtime" {
                deploymentNode "Browser" "User web browser that renders pages and runs the downloaded client bundle." "Chrome/Edge/Firefox" {
                    devBrowserClient = containerInstance tripto.frontend
                }
                deploymentNode "Next.js Runtime" "Runs the frontend dev server." "Node.js" {
                    devNextRuntime = containerInstance tripto.nextRuntime
                }
                deploymentNode "Spring Boot Runtime" "Runs backend microservices locally." "JVM" {
                    devGateway = containerInstance tripto.apiGateway
                    devAuth = containerInstance tripto.authService
                    devUser = containerInstance tripto.userService
                    devHotel = containerInstance tripto.hotelService
                    devBooking = containerInstance tripto.bookingService
                    devMedia = containerInstance tripto.mediaService
                    devRevenue = containerInstance tripto.revenueService
                    devRegulations = containerInstance tripto.regulationsService
                    devNotification = containerInstance tripto.notificationService
                    devEureka = containerInstance tripto.eurekaServer
                    devAdmin = containerInstance tripto.adminServer
                }
                deploymentNode "Docker Container - RabbitMQ" "Local message broker." "RabbitMQ Docker image" {
                    devRabbit = containerInstance tripto.rabbitmq
                }
                deploymentNode "Monitoring Stack" "Local observability stack from monitoring/docker-compose.yml." "Docker Compose" {
                    devPrometheus = containerInstance tripto.prometheus
                    devGrafana = containerInstance tripto.grafana
                    devLoki = containerInstance tripto.loki
                    devPromtail = containerInstance tripto.promtail
                    devLogFiles = containerInstance tripto.logFiles
                }
                deploymentNode "PostgreSQL" "Local or external database server." "PostgreSQL" {
                    devUserDb = containerInstance tripto.userDb
                    devHotelDb = containerInstance tripto.hotelDb
                    devBookingDb = containerInstance tripto.bookingDb
                    devRevenueDb = containerInstance tripto.revenueDb
                    devRegulationsDb = containerInstance tripto.regulationsDb
                }
                deploymentNode "Docker Container - Redis" "Local cache/delayed-job store for future quality tactics." "Redis Docker image" "Planned" {
                    devRedis = containerInstance tripto.redis
                }
                deploymentNode "Docker Container - Elasticsearch" "Local search index for future hotel discovery optimization." "Elasticsearch Docker image" "Planned" {
                    devElastic = containerInstance tripto.elasticsearch
                }
                deploymentNode "PostgreSQL Read Replica / CQRS" "Local reporting read model for future reporting isolation." "PostgreSQL / CQRS projection" "Planned" {
                    devReporting = containerInstance tripto.reportingStore
                }
            }
            deploymentNode "Supabase Cloud" "External object storage." "Supabase" {
                softwareSystemInstance supabaseStorage
            }
            deploymentNode "VNPay Sandbox" "External payment gateway sandbox." "VNPay" {
                softwareSystemInstance vnpay
            }
            deploymentNode "Email Provider" "External email provider." "SMTP/API" {
                softwareSystemInstance emailProvider
            }
        }

        deploymentEnvironment "Target Production" {
            deploymentNode "User Device" "Customer, owner, or admin browser that renders the delivered web UI." "Browser" {
                prodBrowserClient = containerInstance tripto.frontend
            }
            deploymentNode "Frontend Runtime" "Hosts the Next.js frontend." "Node.js / managed frontend platform" {
                prodNextRuntime = containerInstance tripto.nextRuntime
            }
            deploymentNode "Production Load Balancer" "Routes public API traffic." "Nginx / Cloud Load Balancer" "Planned" {
                prodGateway = containerInstance tripto.apiGateway
            }
            deploymentNode "Container Platform" "Runs replicated Spring Boot services." "Kubernetes / managed containers" "Planned" {
                prodAuth = containerInstance tripto.authService
                prodUser = containerInstance tripto.userService
                prodHotel = containerInstance tripto.hotelService
                prodBooking = containerInstance tripto.bookingService
                prodMedia = containerInstance tripto.mediaService
                prodRevenue = containerInstance tripto.revenueService
                prodRegulations = containerInstance tripto.regulationsService
                prodNotification = containerInstance tripto.notificationService
                prodEureka = containerInstance tripto.eurekaServer
                prodAdmin = containerInstance tripto.adminServer
            }
            deploymentNode "Data Platform" "Managed data services." "Cloud/VPS data layer" {
                prodUserDb = containerInstance tripto.userDb
                prodHotelDb = containerInstance tripto.hotelDb
                prodBookingDb = containerInstance tripto.bookingDb
                prodRevenueDb = containerInstance tripto.revenueDb
                prodRegulationsDb = containerInstance tripto.regulationsDb
                prodRabbit = containerInstance tripto.rabbitmq
                prodRedis = containerInstance tripto.redis
                prodElastic = containerInstance tripto.elasticsearch
                prodReporting = containerInstance tripto.reportingStore
            }
            deploymentNode "Observability Platform" "Metrics, logs, and service health dashboards." "Prometheus / Grafana / Loki / Promtail / Spring Boot Admin" {
                prodPrometheus = containerInstance tripto.prometheus
                prodGrafana = containerInstance tripto.grafana
                prodLoki = containerInstance tripto.loki
                prodPromtail = containerInstance tripto.promtail
                prodLogFiles = containerInstance tripto.logFiles
            }
            deploymentNode "External Services" "External SaaS/integration providers." "" {
                softwareSystemInstance supabaseStorage
                softwareSystemInstance vnpay
                softwareSystemInstance emailProvider
            }
        }
    }

    views {
        systemContext tripto "SAD-SystemContext" {
            include customer
            include owner
            include admin
            include tripto
            include vnpay
            include emailProvider
            include supabaseStorage
            autoLayout lr
        }

        container tripto "SAD-Containers" {
            include *
            autoLayout lr
        }

        component tripto.bookingService "SAD-BookingPaymentComponents" {
            include *
            include tripto.frontend
            include tripto.apiGateway
            include tripto.hotelService
            include tripto.userService
            include tripto.rabbitmq
            include tripto.adminServer
            include tripto.prometheus
            include tripto.bookingDb
            include tripto.redis
            include vnpay
            autoLayout lr
        }

        component tripto.hotelService "SAD-HotelAvailabilityComponents" {
            include *
            include tripto.frontend
            include tripto.apiGateway
            include tripto.bookingService
            include tripto.revenueService
            include tripto.userService
            include tripto.userService.userGrpcService
            include tripto.mediaService
            include tripto.adminServer
            include tripto.prometheus
            include tripto.hotelDb
            include tripto.elasticsearch
            include supabaseStorage
            autoLayout lr
        }

        dynamic tripto.bookingService "SAD-BookingVNPayDynamic" "Booking with VNPay payment flow." {
            customer -> tripto.frontend "Selects room and submits booking"
            tripto.frontend -> tripto.apiGateway "POST /api/bookings"
            tripto.apiGateway -> tripto.bookingService.bookingController "Routes booking request"
            tripto.bookingService.bookingController -> tripto.bookingService.bookingServiceComponent "createBooking(request)"
            tripto.bookingService.bookingServiceComponent -> tripto.bookingService.hotelAvailabilityClient "Requests room hold"
            tripto.bookingService.hotelAvailabilityClient -> tripto.hotelService.availabilityController "POST /api/v1/availability/hold"
            tripto.bookingService.bookingServiceComponent -> tripto.bookingService.bookingRepositories "Save PENDING booking with holdId"
            tripto.bookingService.bookingServiceComponent -> tripto.bookingService.hotelEventPublisher "Publish booking.status.changed"
            tripto.bookingService.hotelEventPublisher -> tripto.rabbitmq "Publish event"
            tripto.frontend -> tripto.apiGateway "POST /api/payments/vnpay/create"
            tripto.apiGateway -> tripto.bookingService.paymentController "Routes payment request"
            tripto.bookingService.paymentController -> tripto.bookingService.paymentService "initVnPayPayment(bookingId)"
            tripto.bookingService.paymentService -> tripto.bookingService.bookingServiceComponent "ensureActiveHold(bookingId)"
            tripto.bookingService.bookingServiceComponent -> tripto.bookingService.hotelAvailabilityClient "Checks active hold"
            tripto.bookingService.hotelAvailabilityClient -> tripto.hotelService.availabilityController "GET /hold/{holdId}"
            tripto.bookingService.paymentService -> tripto.bookingService.bookingRepositories "Save PENDING payment and PENDING_PAYMENT booking"
            tripto.bookingService.paymentService -> tripto.bookingService.vnpayUtil "Build signed payment URL"
            tripto.bookingService.vnpayUtil -> vnpay "Create redirect URL"
            vnpay -> tripto.frontend "Return with responseCode"
            tripto.frontend -> tripto.apiGateway "GET /api/payments/vnpay/return"
            tripto.apiGateway -> tripto.bookingService.paymentController "Routes return"
            tripto.bookingService.paymentController -> tripto.bookingService.paymentService "processVNPayCallback(...)"
            tripto.bookingService.paymentService -> tripto.bookingService.bookingRepositories "Update payment/booking state"
            tripto.bookingService.paymentService -> tripto.bookingService.bookingServiceComponent "Confirm hold on success"
            tripto.bookingService.bookingServiceComponent -> tripto.bookingService.hotelAvailabilityClient "Requests hold confirmation"
            tripto.bookingService.hotelAvailabilityClient -> tripto.hotelService.availabilityController "POST /hold/{holdId}/confirm"
            tripto.bookingService.paymentService -> tripto.bookingService.loyaltyPointsClient "Process loyalty points on success"
            tripto.bookingService.loyaltyPointsClient -> tripto.userService.loyaltyController "Deduct/earn points"
            tripto.bookingService.paymentService -> tripto.bookingService.hotelEventPublisher "Publish payment/booking events"
            tripto.bookingService.hotelEventPublisher -> tripto.rabbitmq "Publish events"
            autoLayout lr
        }

        deployment tripto "Development" "SAD-DevelopmentDeployment" {
            include *
            autoLayout lr
        }

        deployment tripto "Target Production" "SAD-TargetProductionDeployment" {
            include *
            autoLayout lr
        }

        styles {
            element "Person" {
                shape Person
                background #0f766e
                color #ffffff
            }
            element "Customer" {
                background #0369a1
                color #ffffff
            }
            element "Hotel Owner" {
                background #047857
                color #ffffff
            }
            element "Administrator" {
                background #7c3aed
                color #ffffff
            }

            element "Software System" {
                background #1d4ed8
                color #ffffff
            }
            element "External System" {
                background #64748b
                color #ffffff
            }
            element "Container" {
                background #2563eb
                color #ffffff
            }
            element "Service" {
                background #0f766e
                color #ffffff
            }
            element "Gateway" {
                background #9333ea
                color #ffffff
            }
            element "Infrastructure" {
                background #475569
                color #ffffff
            }
            element "Resilience" {
                background #be123c
                color #ffffff
            }
            element "Message Broker" {
                shape Pipe
                background #16a34a
                color #ffffff
            }
            element "Monitoring" {
                background #0891b2
                color #ffffff
            }
            element "Log Store" {
                shape Folder
                background #f97316
                color #ffffff
            }
            element "Cache" {
                shape Cylinder
                background #dc2626
                color #ffffff
            }
            element "Search Index" {
                shape Cylinder
                background #7c3aed
                color #ffffff
            }
            element "Reporting Store" {
                shape Cylinder
                background #0891b2
                color #ffffff
            }
            element "Database" {
                shape Cylinder
                background #ca8a04
                color #ffffff
            }
            element "Web Browser" {
                shape WebBrowser
            }
            element "Component" {
                background #bfdbfe
                color #0f172a
            }
            element "Planned" {
                color #111827
                border dashed
                opacity 80
            }
            relationship "Future" {
                dashed true
                color #6b7280
            }
        }
    }
}
