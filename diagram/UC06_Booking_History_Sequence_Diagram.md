@startuml
actor Customer
boundary "Header/User Sidebar\n(components/Header.tsx,\ncomponents/UserSidebar.tsx)" as Navigation
boundary "Booking History Page\n(app/booking/history/page.tsx)" as HistoryPage
boundary "Booking Detail Page\n(app/booking/[id]/page.tsx)" as DetailPage
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
entity "BookingDB" as BookingDB
entity "HotelDB" as HotelDB
 
Customer -> Navigation: 1. Select booking history
activate Customer
activate Navigation
Navigation -> Router: 1a. router.push("/booking/history")
deactivate Navigation

activate Router

Router --> HistoryPage: 1b. Display booking history page
deactivate Router

activate HistoryPage
deactivate Navigation

HistoryPage -> HistoryPage: 2. localStorage.getItem("user")
note right of Customer: 3. Is customer logged in?
alt User not found
    HistoryPage --> Customer: 4. Display login required message
else User found
    HistoryPage -> BookingApi: 5. bookingApi.getUserBookings(userId)
    activate BookingApi
    BookingApi -> BookingController: 5a. GET /api/bookings/user/{userId}
    activate BookingController
    BookingController -> BookingService: 6. getUserBookings(userId)
    activate BookingService
    BookingService -> BookingDB: 6a. bookingRepository.findByUserId(userId)
    activate BookingDB
    BookingDB --> BookingService: 6b. bookings
    deactivate BookingDB
    BookingService -> BookingService: 7. toBookingResponse(booking)
    BookingService --> BookingController: 7a. List<BookingResponse>
    deactivate BookingService
    BookingController --> BookingApi: 7b. 200 OK List<BookingResponse>
    deactivate BookingController
    BookingApi --> HistoryPage: 7c. BookingResponse[]
    deactivate BookingApi

    note right of Customer: 8. Is booking history loaded successfully?
    alt Load failed
        HistoryPage --> Customer: 9. Display booking history loading error message
    else Load successful
        HistoryPage -> HistoryPage: 10. sort bookings by createdAt descending
        opt bookings.length > 0
            HistoryPage -> HotelApi: 11. hotelApi.getHotelById(hotelId)
            activate HotelApi
            HotelApi -> HotelController: 11a. GET /api/hotel/hotels/{hotelId}
            activate HotelController
            HotelController -> HotelService: 11b. getDetail(hotelId)
            activate HotelService
            HotelService -> HotelDB: 11c. hotelRepository.findById(hotelId)
            activate HotelDB
            HotelDB --> HotelService: 11d. Hotel
            deactivate HotelDB
            HotelService --> HotelController: 11e. HotelResponse
            deactivate HotelService
            HotelController --> HotelApi: 11f. HotelResponse
            deactivate HotelController
            HotelApi --> HistoryPage: 11g. mediaAssets[0].url
            deactivate HotelApi

            opt checkedOutBookings.length > 0 and token exists
                HistoryPage -> ReviewApi: 12. reviewApi.hasBookingBeenReviewed(bookingId, token)
                activate ReviewApi
                ReviewApi -> ReviewApi: 12a. getReviewByBookingId(bookingId, token)
                ReviewApi -> ReviewController: 12b. GET /api/v1/reviews/check-booking/{bookingId}?userId=...
                activate ReviewController
                ReviewController -> ReviewService: 12c. getReviewByBookingId(bookingId, userId)
                activate ReviewService
                ReviewService -> HotelDB: 12d. find review by bookingId and userId
                activate HotelDB
                HotelDB --> ReviewService: 12e. review or empty
                deactivate HotelDB
                ReviewService --> ReviewController: 12f. Optional<HotelReviewResponse>
                deactivate ReviewService
                ReviewController --> ReviewApi: 12g. 200 OK review or 404
                deactivate ReviewController
                ReviewApi --> HistoryPage: 12h. reviewed flag
                deactivate ReviewApi
            end
        end

        note right of Customer: 13. Are bookings available?
        alt No bookings
            HistoryPage --> Customer: 14. Display empty booking history message
        else Bookings available
            HistoryPage --> Customer: 15. Display booking history list

            Customer -> HistoryPage: 16. Select a booking to view detail
            HistoryPage -> Router: 16a. router.push("/booking/{booking.id}")
            activate Router

            Router --> DetailPage: 16b. Display booking detail page
            deactivate Router

            activate DetailPage
            DetailPage -> BookingApi: 17. bookingApi.getBooking(id)
            activate BookingApi
            BookingApi -> BookingController: 17a. GET /api/bookings/{id}
            activate BookingController
            BookingController -> BookingService: 18. getBooking(id)
            activate BookingService
            BookingService -> BookingDB: 18a. bookingRepository.findById(id)
            activate BookingDB
            BookingDB --> BookingService: 18b. Booking
            deactivate BookingDB

            note right of Customer: 19. Is booking detail loaded successfully?
            alt Detail load failed
                BookingService --> BookingController: 20. throw IllegalArgumentException
                BookingController --> BookingApi: 20a. 404 Not Found
                deactivate BookingController
                BookingApi --> DetailPage: 20b. throw Error
                deactivate BookingApi
                DetailPage --> Customer: 20c. Display booking detail loading error message
            else Detail load successful
                BookingService -> BookingService: 21. toBookingResponse(booking)

                BookingService --> BookingController: 21a. BookingResponse
                deactivate BookingService
activate BookingController

                BookingController --> BookingApi: 21b. 200 OK BookingResponse
                deactivate BookingController
activate BookingApi
                BookingApi --> DetailPage: 21c. BookingResponse
                deactivate BookingApi
                DetailPage --> Customer: 21d. Display booking detail
            end
            deactivate DetailPage
        end
    end
end

deactivate HistoryPage
deactivate Customer
@enduml
