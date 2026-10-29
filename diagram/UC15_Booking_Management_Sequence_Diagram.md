@startuml
actor "Hotel Owner" as Owner
boundary "Host Menu\n(components/host/menu/HostMenu)" as HostMenu
boundary "Host Bookings Page\n(app/host/bookings/page.tsx)" as HostBookingsPage
boundary "Bookings List\n(components/host/BookingsList.tsx)" as BookingsList
boundary "Booking Detail Modal\n(components/host/BookingDetailModal.tsx)" as BookingDetailModal
boundary "Router\n(next/navigation)" as Router
control "Hotel API\n(lib/api/hotels.ts)" as HotelApi
control "Booking API\n(lib/api/booking.ts)" as BookingApi
control "Hotel Controller\n(hotel-service)" as HotelController
control "Hotel Service\n(hotel-service)" as HotelService
control "Booking Controller\n(booking-service)" as BookingController
control "Booking Service\n(booking-service)" as BookingService
control "Payment Controller\n(booking-service)" as PaymentController
control "Payment Service\n(booking-service)" as PaymentService
control "Hotel Availability Client\n(booking-service)" as AvailabilityClient
control "Hotel Event Publisher\n(booking-service)" as EventPublisher
entity "HotelDB" as HotelDB
entity "BookingDB" as BookingDB
entity "PaymentDB" as PaymentDB

Owner -> HostMenu: 1. Select booking management
activate Owner
activate HostMenu
HostMenu -> Router: 1a. Navigate to /host/bookings
Router --> HostBookingsPage: 1b. Display host bookings route
deactivate HostMenu
activate HostBookingsPage

HostBookingsPage -> HostBookingsPage: 2. useProtectedRoute("OWNER")
alt unauthenticated or not OWNER
    HostBookingsPage -> Router: 3. router.push("/host") or router.push("/")
else owner access valid
    HostBookingsPage --> Owner: 4. Display booking management page
    HostBookingsPage -> HostBookingsPage: 5. localStorage.getItem("token")
    alt token missing
        HostBookingsPage --> Owner: 6. Display login-required message
    else token exists
        HostBookingsPage -> HotelApi: 7. hotelApi.getHotelsByOwner(token)
        activate HotelApi
        HotelApi -> HotelController: 7a. GET /api/v1/hotels/owner
        activate HotelController
        HotelController -> HotelController: 8. getOwnerId(jwt.getSubject())
        HotelController -> HotelService: 9. getByOwner(ownerId)
        activate HotelService
        HotelService -> HotelDB: 9a. hotelRepository.findByOwnerId(ownerId)
        HotelDB --> HotelService: 9b. List<Hotel>
        HotelService --> HotelController: 10. List<Hotel>
        deactivate HotelService
        HotelController --> HotelApi: 10a. owner hotels response
        deactivate HotelController
        HotelApi --> HostBookingsPage: 10b. Hotel[]
        deactivate HotelApi

        note right of Owner: 11. Are owner hotels available?
        alt no owner hotels
            HostBookingsPage --> Owner: 12. Display no-hotel message and create-hotel action
        else owner hotels available
            HostBookingsPage -> HostBookingsPage: 13. setSelectedHotelId(hotelList[0].id or selected id)
            HostBookingsPage -> BookingApi: 14. bookingApi.getHotelBookings(selectedHotelId)
            activate BookingApi
            BookingApi -> BookingController: 14a. GET /api/bookings/hotel/{hotelId}
            activate BookingController
            BookingController -> BookingService: 15. getHotelBookings(hotelId)
            activate BookingService
            BookingService -> BookingDB: 15a. bookingRepository.findByHotelId(hotelId)
            BookingDB --> BookingService: 15b. List<Booking>
            BookingService --> BookingController: 16. List<BookingResponse>
            deactivate BookingService
            BookingController --> BookingApi: 16a. List<BookingResponse>
            deactivate BookingController

            alt booking load fails
                BookingApi --> HostBookingsPage: 17. throw Error
                deactivate BookingApi
                HostBookingsPage --> Owner: 18. Display booking loading error message
            else booking load succeeds
                BookingApi --> HostBookingsPage: 17a. BookingResponse[]
                deactivate BookingApi
                HostBookingsPage --> BookingsList: 19. Display booking statistics, search, filter, and table
                activate BookingsList
                Owner -> BookingsList: 20. Enter search text or select status filter
                BookingsList -> BookingsList: 21. filteredBookings useMemo
                note right of Owner: 22. Are visible bookings available?
                alt no visible bookings
                    BookingsList --> Owner: 23. Display empty or no-matching-booking message
                else visible bookings available
                    Owner -> BookingsList: 24. Select a booking row
                    BookingsList --> BookingDetailModal: 25. Display booking detail
                    activate BookingDetailModal
                    Owner -> BookingDetailModal: 26. Choose processing action

                    alt confirm hotel payment
                        BookingDetailModal -> BookingDetailModal: 27. canConfirmPayment()
                        BookingDetailModal -> BookingApi: 28. bookingApi.confirmPayment(booking.id)
                        activate BookingApi
                        BookingApi -> PaymentController: 28a. POST /api/payments/{bookingId}/confirm
                        activate PaymentController
                        PaymentController -> PaymentService: 28b. confirmHotelPayment(bookingId)
                        activate PaymentService
                        PaymentService -> BookingDB: 29a. bookingRepository.findById(bookingId)
                        BookingDB --> PaymentService: 29b. Booking
                        PaymentService -> PaymentDB: 29c. paymentRepository.findFirstByBookingIdAndStatusOrderByCreatedAtDesc(bookingId, ONSITE_PENDING)
                        PaymentDB --> PaymentService: 29d. Payment
                        PaymentService -> PaymentDB: 29e. paymentRepository.save(payment with COMPLETED)
                        PaymentService -> BookingDB: 29f. bookingRepository.save(booking with CONFIRMED and paidAmount)
                        PaymentService -> BookingService: 29g. confirmBookingHold(bookingId)
                        activate BookingService
                        BookingService -> BookingDB: 29h. bookingRepository.findById(bookingId)
                        BookingService -> AvailabilityClient: 29i. confirmHold(holdId)
                        BookingService --> PaymentService: 29j. hold confirmed
                        deactivate BookingService
                        PaymentService -> EventPublisher: 29k. publishPaymentCompleted(event)
                        PaymentService --> PaymentController: 29l. PaymentResponse
                        deactivate PaymentService
                        PaymentController --> BookingApi: 29m. PaymentResponse
                        deactivate PaymentController
                        BookingApi --> BookingDetailModal: 29n. PaymentResponse
                        deactivate BookingApi
                        BookingDetailModal --> Owner: 30. Display payment success message and reload bookings
                        BookingDetailModal -> HostBookingsPage: 30a. onBookingUpdated()
                    else update stay status
                        BookingDetailModal -> BookingDetailModal: 31. canChangeStatus() and getNextStatus()
                        BookingDetailModal -> BookingApi: 32. bookingApi.updateBookingStatus(booking.id, nextStatus)
                        activate BookingApi
                        BookingApi -> BookingController: 32a. PUT /api/bookings/{id}/status?status={nextStatus}
                        activate BookingController
                        BookingController -> BookingService: 32b. updateBookingStatus(id, status)
                        activate BookingService
                        BookingService -> BookingDB: 33a. bookingRepository.findById(id)
                        BookingDB --> BookingService: 33b. Booking
                        BookingService -> BookingDB: 33c. bookingRepository.save(booking with status)
                        BookingService -> EventPublisher: 33d. publishBookingStatus(event)
                        BookingService --> BookingController: 33e. BookingResponse
                        deactivate BookingService
                        BookingController --> BookingApi: 33f. BookingResponse
                        deactivate BookingController
                        BookingApi --> BookingDetailModal: 33g. BookingResponse
                        deactivate BookingApi
                        BookingDetailModal --> Owner: 34. Display status update success message and reload bookings
                        BookingDetailModal -> HostBookingsPage: 34a. onBookingUpdated()
                    else view only
                        BookingDetailModal --> Owner: 35. Close booking detail without changing data
                    end
                    deactivate BookingDetailModal
                end
                deactivate BookingsList
            end
        end
    end
end

deactivate HostBookingsPage
deactivate Owner
@enduml
