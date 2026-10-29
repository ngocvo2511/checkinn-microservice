@startuml
actor Customer
boundary "Booking History Page\n(app/booking/history/page.tsx)" as HistoryPage
boundary "New Review Page\n(app/reviews/new/page.tsx)" as NewReviewPage
boundary "Review Form\n(components/ReviewForm.tsx)" as ReviewForm
boundary "Hotel Detail Page\n(app/hotel/[id]/page.tsx)" as HotelDetailPage
boundary "Router\n(next/navigation)" as Router
control "Booking API\n(lib/api/booking.ts)" as BookingApi
control "Hotel API\n(lib/api/hotels.ts)" as HotelApi
control "Review API\n(lib/api/reviews.ts)" as ReviewApi
control "Booking Controller\n(booking-service)" as BookingController
control "Booking Service\n(booking-service)" as BookingService
control "Hotel Controller\n(hotel-service)" as HotelController
control "Hotel Service\n(hotel-service)" as HotelService
control "Review Controller\n(hotel-service)" as ReviewController
control "Review Service\n(hotel-service)" as ReviewService
control "Review Mapper\n(hotel-service)" as ReviewMapper
entity "BookingDB" as BookingDB
entity "HotelDB" as HotelDB
entity "ReviewDB" as ReviewDB

Customer -> HistoryPage: 1. Select write review
activate Customer
activate HistoryPage
note right of Customer: 2. Is booking eligible for review?
alt booking.status != "CHECKED_OUT" or already reviewed
    HistoryPage --> Customer: 3. Keep write-review action unavailable
else booking.status == "CHECKED_OUT" and not reviewed
    HistoryPage -> Router: 4. router.push("/reviews/new?bookingId=...&hotelId=...")
    Router --> NewReviewPage: 4a. Display new review page
    activate NewReviewPage
    deactivate HistoryPage

    NewReviewPage -> NewReviewPage: 5. localStorage.getItem("token") / searchParams.get("bookingId", "hotelId")
    note right of Customer: 6. Is required review context available?
    alt bookingId or hotelId missing
        NewReviewPage -> Router: 7. router.push("/booking/history")
    else userToken missing
        NewReviewPage --> Customer: 7a. Display login required message
    else context available
        NewReviewPage -> BookingApi: 8. bookingApi.getBooking(bookingId)
        activate BookingApi
        BookingApi -> BookingController: 8a. GET /api/bookings/{id}
        activate BookingController
        BookingController -> BookingService: 8b. getBooking(id)
        activate BookingService
        BookingService -> BookingDB: 8c. bookingRepository.findById(id)
        activate BookingDB
        BookingDB --> BookingService: 8d. Booking
        deactivate BookingDB
        BookingService --> BookingController: 8e. BookingResponse
        deactivate BookingService
        BookingController --> BookingApi: 8f. BookingResponse
        deactivate BookingController
        BookingApi --> NewReviewPage: 8g. BookingResponse
        deactivate BookingApi

        NewReviewPage -> HotelApi: 9. hotelApi.getHotelById(hotelId)
        activate HotelApi
        HotelApi -> HotelController: 9a. GET /api/hotel/hotels/{hotelId}
        activate HotelController
        HotelController -> HotelService: 9b. getDetail(hotelId)
        activate HotelService
        HotelService -> HotelDB: 9c. hotelRepository.findById(hotelId)
        activate HotelDB
        HotelDB --> HotelService: 9d. Hotel
        deactivate HotelDB
        HotelService --> HotelController: 9e. HotelResponse
        deactivate HotelService
        HotelController --> HotelApi: 9f. HotelResponse
        deactivate HotelController
        HotelApi --> NewReviewPage: 9g. Hotel image and hotel details
        deactivate HotelApi

        NewReviewPage --> ReviewForm: 10. Display review form
        activate ReviewForm
        Customer -> ReviewForm: 11. Enter rating criteria, title, and content
        ReviewForm -> ReviewForm: 12. calculate averageRating
        Customer -> ReviewForm: 13. Submit review
        ReviewForm -> ReviewForm: 14. validate title, content, and userToken
        note right of Customer: 15. Is review input valid?
        alt invalid review input
            ReviewForm --> Customer: 16. Display review validation error message
        else valid review input
            ReviewForm -> ReviewApi: 17. reviewApi.createReview(request, userToken)
            activate ReviewApi
            ReviewApi -> ReviewApi: 18. decode token and set X-User-Id
            ReviewApi -> ReviewController: 18a. POST /api/v1/reviews
            activate ReviewController
            ReviewController -> ReviewController: 19. validate CreateReviewRequest
            ReviewController -> ReviewService: 19a. createReview(request, guestId)
            activate ReviewService
            ReviewService -> ReviewDB: 20. hotelReviewRepository.findByHotelIdAndGuestIdAndBookingId(hotelId, guestId, bookingId)
            activate ReviewDB
            ReviewDB --> ReviewService: 20a. existing review or empty

            note right of Customer: 21. Has customer already reviewed this booking?
            alt existing review found
                ReviewService --> ReviewController: 22. throw IllegalStateException
                deactivate ReviewDB
                deactivate ReviewService
                ReviewController --> ReviewApi: 22a. error response
                deactivate ReviewController
                ReviewApi --> ReviewForm: 22b. throw Error
                deactivate ReviewApi
                ReviewForm --> Customer: 22c. Display duplicate review error message
            else no existing review
                ReviewService -> ReviewMapper: 23. createRequestToEntity(request)
                ReviewMapper --> ReviewService: 23a. HotelReview
                ReviewService -> ReviewService: 23b. setGuestId(guestId) / setStatus(PUBLISHED)
                ReviewService -> ReviewDB: 23c. hotelReviewRepository.save(review)
                ReviewDB --> ReviewService: 23d. saved review
                deactivate ReviewDB
                ReviewService -> ReviewMapper: 24. entityToResponse(saved)
                ReviewMapper --> ReviewService: 24a. HotelReviewResponse
                ReviewService --> ReviewController: 24b. HotelReviewResponse
                deactivate ReviewService
                ReviewController --> ReviewApi: 24c. 201 Created HotelReviewResponse
                deactivate ReviewController
                ReviewApi --> ReviewForm: 24d. Review
                deactivate ReviewApi
                ReviewForm --> Customer: 25. Display review success message
                ReviewForm -> NewReviewPage: 26. onSuccess()
                deactivate ReviewForm
                NewReviewPage -> Router: 26a. router.push("/booking/history")

                HotelDetailPage -> ReviewApi: 27. reviewApi.getReviewStats(hotelId)
                activate ReviewApi
                ReviewApi -> ReviewController: 27a. GET /api/v1/reviews/stats/hotel/{hotelId}
                activate ReviewController
                ReviewController -> ReviewService: 27b. getHotelReviewStats(hotelId, userId)
                activate ReviewService
                ReviewService -> ReviewDB: 27c. find PUBLISHED reviews and stats
                activate ReviewDB
                ReviewDB --> ReviewService: 27d. published reviews and aggregates
                deactivate ReviewDB
                ReviewService --> ReviewController: 27e. HotelReviewStatsResponse
                deactivate ReviewService
                ReviewController --> ReviewApi: 27f. HotelReviewStatsResponse
                deactivate ReviewController
                ReviewApi --> HotelDetailPage: 27g. recentReviews include created review
                deactivate ReviewApi
                HotelDetailPage --> Customer: 27h. Display published review on hotel detail page
            end
        end
        deactivate NewReviewPage
    end
end

deactivate Customer
@enduml
