@startuml
actor Customer
boundary "Payment Page\n(app/booking/payment/page.tsx)" as PaymentPage
boundary "Hotel Confirm Page\n(app/booking/payment/hotel-confirm/page.tsx)" as HotelConfirmPage
boundary "Booking Detail Page\n(app/booking/[id]/page.tsx)" as BookingDetailPage
boundary "Router\n(next/navigation)" as Router
control "Booking API\n(lib/api/booking.ts)" as BookingApi
control "Payment Controller\n(booking-service)" as PaymentController
control "Payment Service\n(booking-service)" as PaymentService
control "Booking Controller\n(booking-service)" as BookingController
control "Booking Service\n(booking-service)" as BookingService
control "Hotel Availability Client\n(booking-service)" as HotelAvailabilityClient
control "Payment Creation Policy Factory\n(booking-service)" as PaymentCreationPolicyFactory
control "Hotel Payment Creation Policy\n(booking-service)" as HotelPaymentCreationPolicy
entity "BookingDB" as BookingDB

activate Customer
activate PaymentPage
Customer -> PaymentPage: 25. Select hotel payment method
Customer -> PaymentPage: 26. Confirm payment
PaymentPage -> PaymentPage: 27. validate bookingId and timeRemaining
alt payment validation failed
    PaymentPage --> Customer: 28. Display payment validation error message
else payment validation passed
    PaymentPage -> PaymentPage: 29. check paymentMethod
    PaymentPage -> BookingApi: 61. bookingApi.createPayment(bookingId, displayTotalPrice, "HOTEL")
    activate BookingApi
    BookingApi -> PaymentController: 61a. POST /api/payments
    activate PaymentController
    PaymentController -> PaymentService: 61b. createPayment(request)
    activate PaymentService
    PaymentService -> BookingDB: 61c. bookingRepository.findById(bookingId)
    activate BookingDB
    BookingDB --> PaymentService: 61d. Booking
    PaymentService -> BookingDB: 61e. paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
    BookingDB --> PaymentService: 61f. latest payment
    deactivate BookingDB

    PaymentService -> BookingService: 61g. ensureActiveHold(bookingId)
    activate BookingService
    BookingService -> HotelAvailabilityClient: 61h. getHold(holdId)
activate HotelAvailabilityClient
    HotelAvailabilityClient --> BookingService: 61i. HoldResponse
    deactivate HotelAvailabilityClient

    BookingService --> PaymentService: 61j. Booking
    deactivate BookingService
    PaymentService -> PaymentCreationPolicyFactory: 61k. getPolicy(HOTEL)
activate PaymentCreationPolicyFactory

    PaymentCreationPolicyFactory --> PaymentService: 61l. HotelPaymentCreationPolicy
    deactivate PaymentCreationPolicyFactory 

    PaymentService -> HotelPaymentCreationPolicy: 61m. createPayment(request)
activate HotelPaymentCreationPolicy

    HotelPaymentCreationPolicy --> PaymentService: 61n. Payment ONSITE_PENDING
    PaymentService -> HotelPaymentCreationPolicy: 62. updateBookingAfterPaymentCreated(booking)
    deactivate HotelPaymentCreationPolicy

    PaymentService -> BookingDB: 62a. bookingRepository.save(PENDING_PAYMENT)
activate BookingDB

    PaymentService -> BookingDB: 62b. paymentRepository.save(payment)
    BookingDB --> PaymentService: 62c. hotel payment and pending-payment booking saved
    deactivate BookingDB
    PaymentService --> PaymentController: 62d. PaymentResponse
    deactivate PaymentService
    PaymentController --> BookingApi: 62e. PaymentResponse
    deactivate PaymentController
    BookingApi --> PaymentPage: 62f. PaymentResponse
    deactivate BookingApi
    PaymentPage -> Router: 63. router.push("/booking/payment/hotel-confirm?bookingId=...")
    activate Router

    deactivate PaymentPage
    Router --> HotelConfirmPage: 63a. Display hotel payment confirmation
    deactivate Router

    activate HotelConfirmPage
    HotelConfirmPage --> Customer: 63b. display MSG55 and MSG56
    Customer -> HotelConfirmPage: 64. View booking confirmation
    HotelConfirmPage -> Router: 64a. router.push("/booking/{bookingId}")

    deactivate HotelConfirmPage
    activate Router

    Router --> BookingDetailPage: 64b. Display booking detail page
    deactivate Router

    activate BookingDetailPage
    BookingDetailPage -> BookingApi: 64c. bookingApi.getBooking(id)
    activate BookingApi
    BookingApi -> BookingController: 64d. GET /api/bookings/{id}
    activate BookingController
    BookingController -> BookingService: 64e. getBooking(id)
    activate BookingService
    BookingService -> BookingDB: 64f. bookingRepository.findById(id)
    activate BookingDB
    BookingDB --> BookingService: 64g. Booking
    deactivate BookingDB
    BookingService --> BookingController: 64h. BookingResponse
    deactivate BookingService
    BookingController --> BookingApi: 64i. BookingResponse
    deactivate BookingController
    BookingApi --> BookingDetailPage: 64j. setBooking(data)
    deactivate BookingApi
    BookingDetailPage --> Customer: 64k. display booking detail
    deactivate BookingDetailPage
end

deactivate Customer
@enduml
