@startuml
actor Customer
boundary "Payment Page\n(app/booking/payment/page.tsx)" as PaymentPage
boundary "VNPay Return Page\n(app/booking/payment/return/page.tsx)" as ReturnPage
boundary "Booking Detail Page\n(app/booking/[id]/page.tsx)" as BookingDetailPage
boundary "Router\n(next/navigation)" as Router
control "Booking API\n(lib/api/booking.ts)" as BookingApi
control "Payment Controller\n(booking-service)" as PaymentController
control "Payment Service\n(booking-service)" as PaymentService
control "Booking Service\n(booking-service)" as BookingService
control "Hotel Availability Client\n(booking-service)" as HotelAvailabilityClient
control "Availability Controller\n(hotel-service)" as AvailabilityController
control "Availability Service\n(hotel-service)" as AvailabilityService
control "VnPay Util\n(booking-service)" as VnPayUtil
control "Loyalty Points Client\n(booking-service)" as LoyaltyPointsClient
control "Hotel Event Publisher\n(booking-service)" as HotelEventPublisher
entity "BookingDB" as BookingDB
entity "HotelDB" as HotelDB
entity "RabbitMQ" as RabbitMQ
entity "VNPay" as VNPay

activate Customer
activate PaymentPage
Customer -> PaymentPage: 25. Select VNPay payment method
Customer -> PaymentPage: 26. Confirm payment
PaymentPage -> PaymentPage: 27. validate bookingId and timeRemaining
alt payment validation failed
    PaymentPage --> Customer: 28. Display payment validation error message
else payment validation passed
    PaymentPage -> PaymentPage: 29. check paymentMethod
    PaymentPage -> BookingApi: 30. bookingApi.initVnPay(bookingId)
    activate BookingApi
    BookingApi -> PaymentController: 30a. POST /api/payments/vnpay/create?bookingId=...
    activate PaymentController
    PaymentController -> PaymentService: 30b. initVnPayPayment(bookingId, clientIp)
    activate PaymentService
    PaymentService -> BookingDB: 30c. bookingRepository.findById(bookingId)
    activate BookingDB
    BookingDB --> PaymentService: 30d. Booking
    PaymentService -> BookingDB: 30e. paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
    BookingDB --> PaymentService: 30f. latest payment
    PaymentService -> BookingService: 30g. ensureActiveHold(bookingId)
    activate BookingService
    BookingService -> HotelAvailabilityClient: 30h. getHold(holdId)
    activate HotelAvailabilityClient
    HotelAvailabilityClient -> AvailabilityController: 30i. GET /api/v1/availability/hold/{holdId}
    activate AvailabilityController
    AvailabilityController -> AvailabilityService: 30j. getHold(holdId)
    activate AvailabilityService
    AvailabilityService -> HotelDB: 30k. roomHoldRepository.findById(holdId)
    activate HotelDB
    HotelDB --> AvailabilityService: 30l. RoomHold
    deactivate HotelDB
    AvailabilityService --> AvailabilityController: 30m. HoldResponse
    deactivate AvailabilityService
    AvailabilityController --> HotelAvailabilityClient: 30n. HoldResponse
    deactivate AvailabilityController
    HotelAvailabilityClient --> BookingService: 30o. HoldResponse
    deactivate HotelAvailabilityClient
    BookingService --> PaymentService: 30p. Booking
    deactivate BookingService
    PaymentService -> BookingDB: 30q. bookingRepository.save(PENDING_PAYMENT)
    PaymentService -> BookingDB: 30r. paymentRepository.save(payment PENDING)
    PaymentService -> VnPayUtil: 30s. buildQuery(params)
    VnPayUtil --> PaymentService: 30t. query
    PaymentService -> VnPayUtil: 30u. hmacSHA512(hashSecret, query)
    VnPayUtil --> PaymentService: 30v. secureHash
    PaymentService -> BookingDB: 30w. paymentRepository.save(payment with vnpayOrderId)
    BookingDB --> PaymentService: 30x. saved payment
    deactivate BookingDB
    PaymentService --> PaymentController: 30y. VnPayInitResponse
    deactivate PaymentService
    PaymentController --> BookingApi: 30z. VnPayInitResponse
    deactivate PaymentController
    BookingApi --> PaymentPage: 30aa. redirectUrl
    deactivate BookingApi

    PaymentPage -> VNPay: 31. window.location.href = redirectUrl
    deactivate PaymentPage
    activate VNPay
    Customer -> VNPay: 32. process online payment
    VNPay --> ReturnPage: 33. redirect(returnUrl)
    deactivate VNPay
    activate ReturnPage
    ReturnPage -> BookingApi: 34. bookingApi.processVnPayReturn(qs)
    activate BookingApi
    BookingApi -> PaymentController: 34a. GET /api/payments/vnpay/return?...
    activate PaymentController
    PaymentController -> VnPayUtil: 34b. buildQuery(paramsForHash)
    VnPayUtil --> PaymentController: 34c. data
    PaymentController -> VnPayUtil: 34d. hmacSHA512(hashSecret, data)
    VnPayUtil --> PaymentController: 34e. expected
    PaymentController -> PaymentService: 34f. processVNPayCallback(orderId, responseCode, transactionNo)
    activate PaymentService
    PaymentService -> BookingDB: 34g. paymentRepository.findByVnpayOrderId(orderId)
    activate BookingDB
    BookingDB --> PaymentService: 34h. Payment

    alt responseCode != "00"
        PaymentService -> BookingDB: 35. save payment FAILED and booking PENDING
        BookingDB --> PaymentService: 35a. failed payment and pending booking saved
        deactivate BookingDB
        PaymentService --> PaymentController: 35b. PaymentResponse
        deactivate PaymentService
        PaymentController --> BookingApi: 35c. PaymentResponse
        deactivate PaymentController
        BookingApi --> ReturnPage: 35d. PaymentResponse
        deactivate BookingApi
        ReturnPage --> Customer: 36. Display payment failure message

        alt retry payment
            Customer -> ReturnPage: 37. Retry payment
            ReturnPage -> Router: 37a. push("/booking/payment?bookingId=...")
            Router --> PaymentPage: 38. Display payment page with existing booking
            deactivate ReturnPage
            activate PaymentPage
            Customer -> PaymentPage: 38a. Confirm retry payment
            PaymentPage -> BookingApi: 38b. bookingApi.initVnPay(bookingId)
            activate BookingApi
            BookingApi -> PaymentController: 38c. POST /api/payments/vnpay/create?bookingId=...
            activate PaymentController
            PaymentController -> PaymentService: 38d. initVnPayPayment(bookingId, clientIp)
            activate PaymentService
            PaymentService -> BookingService: 38e. ensureActiveHold(bookingId)
            activate BookingService
            BookingService -> HotelAvailabilityClient: 38f. getHold(holdId)
            activate HotelAvailabilityClient
            HotelAvailabilityClient -> AvailabilityController: 38g. GET /api/v1/availability/hold/{holdId}
            activate AvailabilityController
            AvailabilityController -> AvailabilityService: 38h. getHold(holdId)
            activate AvailabilityService
            AvailabilityService --> AvailabilityController: 38i. HoldResponse
            deactivate AvailabilityService
            AvailabilityController --> HotelAvailabilityClient: 38j. HoldResponse
            deactivate AvailabilityController
            HotelAvailabilityClient --> BookingService: 38k. HoldResponse
            deactivate HotelAvailabilityClient

            note right of Customer: 39. Is room hold still valid?
            alt Hold expired or not HELD
                BookingService -> BookingDB: 40. bookingRepository.save(CANCELLED)
                activate BookingDB
                BookingDB --> BookingService: 40a. saved booking
                deactivate BookingDB
                BookingService -> HotelEventPublisher: 40b. publishBookingStatus(event)
                activate HotelEventPublisher
                HotelEventPublisher -> RabbitMQ: 40c. convertAndSend("booking.status.changed", event)
                HotelEventPublisher --> BookingService: 40d. return
                deactivate HotelEventPublisher
                BookingService --> PaymentService: 40e. throw IllegalStateException
                deactivate BookingService
                PaymentService --> PaymentController: 40f. error
                deactivate PaymentService
                PaymentController --> BookingApi: 40g. 400 Bad Request
                deactivate PaymentController
                BookingApi --> PaymentPage: 40h. throw Error
                deactivate BookingApi
                PaymentPage --> Customer: 41. Display hold expired message
            else Hold still valid
                BookingService --> PaymentService: 38l. Booking
                deactivate BookingService
                PaymentService -> BookingDB: 42. paymentRepository.save(new VNPAY payment)
                activate BookingDB
                BookingDB --> PaymentService: 42b. saved payment
                deactivate BookingDB
                PaymentService -> VnPayUtil: 42c. buildQuery(params)
                PaymentService -> VnPayUtil: 42d. hmacSHA512(hashSecret, query)
                PaymentService --> PaymentController: 42e. VnPayInitResponse
                deactivate PaymentService
                PaymentController --> BookingApi: 42f. VnPayInitResponse
                deactivate PaymentController
                BookingApi --> PaymentPage: 42g. redirectUrl
                deactivate BookingApi
                PaymentPage -> VNPay: 43. window.location.href = redirectUrl
                deactivate PaymentPage
                activate VNPay
                Customer -> VNPay: 44. process online payment again
                VNPay --> ReturnPage: 45. redirect(returnUrl)
                deactivate VNPay
                activate ReturnPage
                ReturnPage -> BookingApi: 46. bookingApi.processVnPayReturn(qs)
                activate BookingApi
                BookingApi -> PaymentController: 46a. GET /api/payments/vnpay/return?...
                activate PaymentController
                PaymentController -> PaymentService: 46b. processVNPayCallback(orderId, responseCode, transactionNo)
                activate PaymentService
                PaymentService -> BookingDB: 46c. paymentRepository.findByVnpayOrderId(orderId)
                activate BookingDB
                BookingDB --> PaymentService: 46d. Payment

                alt retry responseCode != "00"
                    PaymentService -> BookingDB: 47. save payment FAILED and booking PENDING
                    BookingDB --> PaymentService: 47a. failed payment and pending booking saved
                    deactivate BookingDB
                    PaymentService --> PaymentController: 47b. PaymentResponse
                    deactivate PaymentService
                    PaymentController --> BookingApi: 47c. PaymentResponse
                    deactivate PaymentController
                    BookingApi --> ReturnPage: 47d. PaymentResponse
                    deactivate BookingApi
                    ReturnPage --> Customer: 48. Display payment failure message
                else retry responseCode == "00"
                    PaymentService -> BookingDB: 49. save payment COMPLETED
                    PaymentService -> BookingDB: 50. save booking CONFIRMED
                    BookingDB --> PaymentService: 50a. completed retry payment and confirmed booking saved
                    deactivate BookingDB
                    PaymentService -> BookingService: 51. confirmBookingHold(bookingId)
                    activate BookingService
                    BookingService -> HotelAvailabilityClient: 51a. confirmHold(holdId)
                    activate HotelAvailabilityClient
                    HotelAvailabilityClient -> AvailabilityController: 51b. POST /api/v1/availability/hold/{holdId}/confirm
                    activate AvailabilityController
                    AvailabilityController -> AvailabilityService: 51c. confirmHold(holdId)
                    activate AvailabilityService
                    AvailabilityService -> HotelDB: 51d. update held/booked and hold status
                    activate HotelDB
                    HotelDB --> AvailabilityService: 51e. saved
                    deactivate HotelDB
                    AvailabilityService --> AvailabilityController: 51f. HoldResponse
                    deactivate AvailabilityService
                    AvailabilityController --> HotelAvailabilityClient: 51g. HoldResponse
                    deactivate AvailabilityController
                    HotelAvailabilityClient --> BookingService: 51h. return
                    deactivate HotelAvailabilityClient
                    BookingService --> PaymentService: 51i. return
                    deactivate BookingService
                    PaymentService -> LoyaltyPointsClient: 52. usePoints(request) / earnPoints(userId, bookingId, totalAmount)
                    PaymentService -> HotelEventPublisher: 52a. publishPaymentCompleted(event)
                    activate HotelEventPublisher
                    HotelEventPublisher -> RabbitMQ: 52b. convertAndSend("payment.completed", event)
                    PaymentService -> HotelEventPublisher: 52c. publishBookingStatus(event)
                    HotelEventPublisher -> RabbitMQ: 52d. convertAndSend("booking.status.changed", event)
                    HotelEventPublisher --> PaymentService: 52e. return
                    deactivate HotelEventPublisher
                    PaymentService --> PaymentController: 52g. PaymentResponse
                    deactivate PaymentService
                    PaymentController --> BookingApi: 52h. PaymentResponse
                    deactivate PaymentController
                    BookingApi --> ReturnPage: 52i. PaymentResponse
                    deactivate BookingApi
                    ReturnPage --> Customer: 53. Display VNPay success confirmation
                    Customer -> ReturnPage: 64. View booking confirmation
                    ReturnPage -> Router: 64a. router.push("/booking/{bookingId}")
                    Router --> BookingDetailPage: 64b. Display booking detail page
                end
            end
        else no retry
            ReturnPage -> ReturnPage: 54. Keep failed result page
            ReturnPage --> Customer: 55. Display failed payment status
        end
    else responseCode == "00"
        PaymentService -> BookingDB: 56. save payment COMPLETED
        PaymentService -> BookingDB: 57. save booking CONFIRMED
        BookingDB --> PaymentService: 57a. completed payment and confirmed booking saved
        deactivate BookingDB
        PaymentService -> BookingService: 58. confirmBookingHold(bookingId)
        activate BookingService
        BookingService -> HotelAvailabilityClient: 58a. confirmHold(holdId)
        activate HotelAvailabilityClient
        HotelAvailabilityClient -> AvailabilityController: 58b. POST /api/v1/availability/hold/{holdId}/confirm
        activate AvailabilityController
        AvailabilityController -> AvailabilityService: 58c. confirmHold(holdId)
        activate AvailabilityService
        AvailabilityService -> HotelDB: 58d. update held/booked and hold status
        activate HotelDB
        HotelDB --> AvailabilityService: 58e. saved
        deactivate HotelDB
        AvailabilityService --> AvailabilityController: 58f. HoldResponse
        deactivate AvailabilityService
        AvailabilityController --> HotelAvailabilityClient: 58g. HoldResponse
        deactivate AvailabilityController
        HotelAvailabilityClient --> BookingService: 58h. return
        deactivate HotelAvailabilityClient
        BookingService --> PaymentService: 58i. return
        deactivate BookingService
        PaymentService -> LoyaltyPointsClient: 59. usePoints(request) / earnPoints(userId, bookingId, totalAmount)
        PaymentService -> HotelEventPublisher: 59a. publishPaymentCompleted(event)
        activate HotelEventPublisher
        HotelEventPublisher -> RabbitMQ: 59b. convertAndSend("payment.completed", event)
        PaymentService -> HotelEventPublisher: 59c. publishBookingStatus(event)
        HotelEventPublisher -> RabbitMQ: 59d. convertAndSend("booking.status.changed", event)
        HotelEventPublisher --> PaymentService: 59e. return
        deactivate HotelEventPublisher
        PaymentService --> PaymentController: 59g. PaymentResponse
        deactivate PaymentService
        PaymentController --> BookingApi: 59h. PaymentResponse
        deactivate PaymentController
        BookingApi --> ReturnPage: 59i. PaymentResponse
        deactivate BookingApi
        ReturnPage --> Customer: 60. Display VNPay success confirmation
        Customer -> ReturnPage: 64. View booking confirmation
        ReturnPage -> Router: 64a. router.push("/booking/{bookingId}")
        Router --> BookingDetailPage: 64b. Display booking detail page
    end
end

deactivate ReturnPage
deactivate Customer
@enduml
