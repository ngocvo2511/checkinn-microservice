@startuml
actor Customer
boundary "Hotel Detail Page\n(app/hotel/[id]/page.tsx)" as HotelDetailPage
boundary "Login Modal\n(app/hotel/[id]/page.tsx)" as LoginModal
boundary "Booking Page\n(app/booking/page.tsx)" as BookingPage
boundary "Payment Page\n(app/booking/payment/page.tsx)" as PaymentPage
boundary "Router\n(next/navigation)" as Router
control "Booking API\n(lib/api/booking.ts)" as BookingApi
control "Loyalty API\n(lib/api/booking.ts)" as LoyaltyApi
control "Booking Controller\n(booking-service)" as BookingController
control "Booking Service\n(booking-service)" as BookingService
control "Hotel Availability Client\n(booking-service)" as HotelAvailabilityClient
control "Availability Controller\n(hotel-service)" as AvailabilityController
control "Availability Service\n(hotel-service)" as AvailabilityService
control "Loyalty Points Client\n(booking-service)" as LoyaltyPointsClient
control "Hotel Event Publisher\n(booking-service)" as HotelEventPublisher
entity "BookingDB" as BookingDB
entity "HotelDB" as HotelDB
entity "RabbitMQ" as RabbitMQ

Customer -> HotelDetailPage: 1. View hotel detail
activate Customer
activate HotelDetailPage
Customer -> HotelDetailPage: 2. Select room type and quantity
Customer -> HotelDetailPage: 3. Click Book Room

HotelDetailPage -> HotelDetailPage: 4. useAuth()
alt user == null
    HotelDetailPage -> LoginModal: 5. Display login form
    activate LoginModal
    Customer -> LoginModal: 6. Log in
    LoginModal --> HotelDetailPage: 6a. useAuth().login(userData)
    deactivate LoginModal
else user != null
end

HotelDetailPage -> HotelDetailPage: 7. validate roomQuantity, selectedRoomType, room.id
alt selected room type invalid
    HotelDetailPage --> Customer: 8. Display room selection error message
else selected room type valid
    HotelDetailPage -> Router: 9. router.push("/booking?...bookingParams")
activate Router
    deactivate HotelDetailPage

    Router --> BookingPage: 10. Display booking form
    deactivate Router

    activate BookingPage
    BookingPage -> BookingPage: 10a. useAuth()
    opt authUser != null
        BookingPage -> LoyaltyApi: 10b. loyaltyApi.getUserPoints(userData.userId)
    activate LoyaltyApi 

        LoyaltyApi --> BookingPage: 10c. LoyaltyPointsResponse
    deactivate LoyaltyApi 

    end

    Customer -> BookingPage: 11. Enter contact and guest information
    Customer -> BookingPage: 12. Submit booking form
    BookingPage -> BookingPage: 13. runValidation()
    alt validation failed
        BookingPage --> Customer: 14. Display booking validation error message
    else validation passed
        BookingPage -> BookingPage: 15. build CreateBookingPayload
        BookingPage -> BookingApi: 16. bookingApi.createBooking(payload)
        activate BookingApi
        BookingApi -> BookingController: 16a. POST /api/bookings
        activate BookingController
        BookingController -> BookingService: 16b. createBooking(request)
        activate BookingService
        BookingService -> BookingService: 16c. validate items and totalQuantity
        BookingService -> HotelAvailabilityClient: 16d. holdRooms(holdRequest)
        activate HotelAvailabilityClient
        HotelAvailabilityClient -> AvailabilityController: 16e. POST /api/v1/availability/hold
        activate AvailabilityController
        AvailabilityController -> AvailabilityService: 16f. checkAndHold(request)
        activate AvailabilityService
        AvailabilityService -> HotelDB: 16g. find room type and availability
        activate HotelDB
        HotelDB --> AvailabilityService: 16h. room type and availability rows
        AvailabilityService -> HotelDB: 16i. save held availability and room hold
        HotelDB --> AvailabilityService: 16j. hold saved
        deactivate HotelDB
        AvailabilityService --> AvailabilityController: 16k. HoldResponse
        deactivate AvailabilityService
        AvailabilityController --> HotelAvailabilityClient: 16l. HoldResponse
        deactivate AvailabilityController
        HotelAvailabilityClient --> BookingService: 16m. HoldResponse
        deactivate HotelAvailabilityClient
note right of Customer: 17. Is room hold successful?
        alt hold failed
            BookingService --> BookingController: 18. throw IllegalStateException

            BookingController --> BookingApi: 18a. 400/500 error body
            deactivate BookingController
            BookingApi --> BookingPage: 18b. throw Error
            deactivate BookingApi
            BookingPage --> Customer: 18c. Display booking creation error message
        else hold successful

            BookingService -> BookingService: 19. calculate totalAmount

            opt pointsToUse > 0
                BookingService -> LoyaltyPointsClient: 20. getLoyaltyPoints(userId)
            activate LoyaltyPointsClient

                LoyaltyPointsClient --> BookingService: 20a. LoyaltyPointsDTO
            deactivate LoyaltyPointsClient

            end
            BookingService -> BookingDB: 21. bookingRepository.save(booking PENDING)
            activate BookingDB
            BookingDB --> BookingService: 21a. saved booking
            BookingService -> BookingDB: 22. bookingItemRepository.saveAll(items)
            BookingDB --> BookingService: 22a. saved items
            deactivate BookingDB
            BookingService -> HotelEventPublisher: 22b. publishBookingStatus(event)
            activate HotelEventPublisher
            HotelEventPublisher -> RabbitMQ: 22c. convertAndSend("booking.status.changed", event)
            activate RabbitMQ
            deactivate RabbitMQ

            HotelEventPublisher --> BookingService: 22d. return
            deactivate HotelEventPublisher
            BookingService --> BookingController: 22e. BookingResponse
deactivate BookingService
activate BookingController 

            deactivate BookingService
            BookingController --> BookingApi: 22f. 201 Created BookingResponse
            deactivate BookingController

activate BookingApi

            BookingApi --> BookingPage: 22g. BookingResponse
            deactivate BookingApi
            BookingPage -> Router: 23. router.push("/booking/payment?bookingId=...")
activate Router
deactivate BookingPage
            Router --> PaymentPage: 24. Display payment page and hold countdown
deactivate Router

            activate PaymentPage
            PaymentPage -> BookingApi: 24a. bookingApi.getBooking(bookingId)
            activate BookingApi
            BookingApi -> BookingController: 24b. GET /api/bookings/{id}
            activate BookingController
            BookingController -> BookingService: 24c. getBooking(id)
            activate BookingService
            BookingService -> BookingDB: 24d. bookingRepository.findById(id)
            activate BookingDB
            BookingDB --> BookingService: 24e. Booking
            deactivate BookingDB
            BookingService --> BookingController: 24f. BookingResponse
            deactivate BookingService
            BookingController --> BookingApi: 24g. BookingResponse
            deactivate BookingController
            BookingApi --> PaymentPage: 24h. setBooking(data)
            deactivate BookingApi
            deactivate PaymentPage
        end
    end
    deactivate BookingPage
end

deactivate Customer
@enduml
