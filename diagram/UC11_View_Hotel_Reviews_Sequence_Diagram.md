@startuml
actor Owner
boundary "Host Menu\n(components/host/menu/HostMenu.tsx)" as HostMenu
boundary "Host Layout\n(app/host/layout.tsx)" as HostLayout
boundary "Host Reviews Page\n(app/host/reviews/page.tsx)" as HostReviewsPage
boundary "Host Review Card\n(components/HostReviewCard.tsx)" as HostReviewCard
boundary "Router\n(next/navigation)" as Router
control "Hotel API\n(lib/api/hotels.ts)" as HotelApi
control "Review API\n(lib/api/reviews.ts)" as ReviewApi
control "Hotel Controller\n(hotel-service)" as HotelController
control "Hotel Service\n(hotel-service)" as HotelService
control "Review Controller\n(hotel-service)" as ReviewController
control "Review Service\n(hotel-service)" as ReviewService
control "Review Mapper\n(hotel-service)" as ReviewMapper
control "User gRPC Client\n(hotel-service)" as UserGrpcClient
entity "HotelDB" as HotelDB
entity "ReviewDB" as ReviewDB
entity "ReviewResponseDB" as ReviewResponseDB

Owner -> HostMenu: 1. Select hotel review management
activate Owner
activate HostMenu
HostMenu -> Router: 1a. Navigate to "/host/reviews"
Router --> HostLayout: 1b. Display host reviews route
deactivate HostMenu
activate HostLayout

HostLayout -> HostLayout: 2. useProtectedRoute("OWNER")
alt not authenticated or not OWNER
    HostLayout -> Router: 3. router.push("/host") or router.push("/")
else authenticated OWNER
    HostLayout --> HostReviewsPage: 4. Display host reviews page
    activate HostReviewsPage

    HostReviewsPage -> HotelApi: 5. hotelApi.getHotelsByOwner(token)
    activate HotelApi
    HotelApi -> HotelController: 5a. GET /api/hotel/hotels/owner
    activate HotelController
    HotelController -> HotelController: 6. getOwnerId(jwt.getSubject())
    HotelController -> HotelService: 7. getByOwner(ownerId)
    activate HotelService
    HotelService -> HotelDB: 7a. hotelRepository.findByOwnerId(ownerId)
    activate HotelDB
    HotelDB --> HotelService: 7b. owned hotels
    deactivate HotelDB
    HotelService --> HotelController: 7c. List<Hotel>
    deactivate HotelService
    alt hotel loading fails
        HotelController --> HotelApi: 8. error response
        deactivate HotelController
        HotelApi --> HostReviewsPage: 8a. throw Error
        deactivate HotelApi
        HostReviewsPage --> Owner: 9. Display hotel loading error message
    else hotel loading succeeds
        HotelController --> HotelApi: 8b. List<HotelResponse>
        deactivate HotelController
        HotelApi --> HostReviewsPage: 8c. Hotel[]
        deactivate HotelApi
        HostReviewsPage --> Owner: 10. Display hotel filter and review management area

        Owner -> HostReviewsPage: 11. Select all hotels or a specific hotel filter
        HostReviewsPage -> HostReviewsPage: 12. localStorage.getItem("token")
        alt token missing
            HostReviewsPage -> Router: 13. router.push("/host/login")
        else token exists
            HostReviewsPage -> ReviewApi: 14. decode token and set X-User-Id
            activate ReviewApi
            alt selectedHotelId == "all"
                ReviewApi -> ReviewController: 15. GET /api/v1/reviews/owner/all?page=...&size=...
                activate ReviewController
                ReviewController -> ReviewService: 16. getReviewsByOwner(ownerId, pageable)
                activate ReviewService
                ReviewService -> HotelDB: 16a. hotelRepository.findByOwnerId(ownerId)
                activate HotelDB
                HotelDB --> ReviewService: 16b. owned hotelIds
                deactivate HotelDB
                note right of Owner: 17. Is selected hotel owned by owner?
                ReviewService -> ReviewDB: 19. findByHotelIdInAndStatusOrderByCreatedAtDesc(hotelIds, PUBLISHED, pageable)
                activate ReviewDB
                ReviewDB --> ReviewService: 19a. Page<HotelReview>
                deactivate ReviewDB
                ReviewService -> ReviewMapper: 20. entityToResponse(review)
                ReviewMapper --> ReviewService: 20a. HotelReviewResponse
                ReviewService -> UserGrpcClient: 20b. GetUserById(guestId)
                UserGrpcClient --> ReviewService: 20c. guest display data
                ReviewService -> ReviewResponseDB: 20d. reviewResponseRepository.findByReviewId(reviewId)
                activate ReviewResponseDB
                ReviewResponseDB --> ReviewService: 20e. owner response or empty
                deactivate ReviewResponseDB
                ReviewService -> HotelDB: 20f. hotelRepository.findById(hotelId)
                activate HotelDB
                HotelDB --> ReviewService: 20g. hotel name
                deactivate HotelDB
                ReviewService --> ReviewController: 21. Page<HotelReviewResponse>
                deactivate ReviewService
                ReviewController --> ReviewApi: 21a. Page<HotelReviewResponse>
                deactivate ReviewController
                ReviewApi --> HostReviewsPage: 21b. normalized reviews
                deactivate ReviewApi
                HostReviewsPage -> HostReviewsPage: 22. Match review.hotelId with loaded hotels
                note right of Owner: 23. Are reviews available?
                alt reviews empty
                    HostReviewsPage --> Owner: 24. Display empty review list message
                else reviews available
                    HostReviewsPage --> Owner: 25. Display review summary, filters, and list
                    HostReviewsPage --> HostReviewCard: 26. Display review content and detail ratings
                    activate HostReviewCard
                    HostReviewCard --> Owner: 26a. Show guest, hotel, rating, title, content, criteria, and owner response
                    Owner -> HostReviewsPage: 27. Filter by response status, switch analytics tab, or change page
                    HostReviewsPage -> HostReviewsPage: 28. Update displayed review list or analytics from loaded reviews
                    HostReviewsPage --> Owner: 28a. Display updated review view
                    deactivate HostReviewCard
                end
            else selectedHotelId != "all"
                ReviewApi -> ReviewController: 15a. GET /api/v1/reviews/owner/hotel/{hotelId}?page=...&size=...
                activate ReviewController
                ReviewController -> ReviewService: 16c. getReviewsByOwnerAndHotel(ownerId, hotelId, pageable)
                activate ReviewService
                ReviewService -> HotelDB: 17. hotelRepository.existsByIdAndOwnerId(hotelId, ownerId)
                activate HotelDB
                alt hotel not owned by owner
                    HotelDB --> ReviewService: 17a. false
                    deactivate HotelDB
                    ReviewService --> ReviewController: 18. throw IllegalArgumentException
                    deactivate ReviewService
                    ReviewController --> ReviewApi: 18a. error response
                    deactivate ReviewController
                    ReviewApi --> HostReviewsPage: 18b. throw Error
                    deactivate ReviewApi
                    HostReviewsPage --> Owner: 18c. Display unauthorized hotel review error message
                else hotel owned by owner
                    HotelDB --> ReviewService: 17b. true
                    deactivate HotelDB
                    ReviewService -> ReviewDB: 19. findByHotelIdAndStatusOrderByCreatedAtDesc(hotelId, PUBLISHED, pageable)
                    activate ReviewDB
                    ReviewDB --> ReviewService: 19a. Page<HotelReview>
                    deactivate ReviewDB
                    ReviewService -> ReviewMapper: 20. entityToResponse(review)
                    ReviewMapper --> ReviewService: 20a. HotelReviewResponse
                    ReviewService -> UserGrpcClient: 20b. GetUserById(guestId)
                    UserGrpcClient --> ReviewService: 20c. guest display data
                    ReviewService -> ReviewResponseDB: 20d. reviewResponseRepository.findByReviewId(reviewId)
                    activate ReviewResponseDB
                    ReviewResponseDB --> ReviewService: 20e. owner response or empty
                    deactivate ReviewResponseDB
                    ReviewService --> ReviewController: 21. Page<HotelReviewResponse>
                    deactivate ReviewService
                    ReviewController --> ReviewApi: 21a. Page<HotelReviewResponse>
                    deactivate ReviewController
                    ReviewApi --> HostReviewsPage: 21b. normalized reviews
                    deactivate ReviewApi
                    HostReviewsPage -> HostReviewsPage: 22. Match review.hotelId with loaded hotels
                    note right of Owner: 23. Are reviews available?
                    alt reviews empty
                        HostReviewsPage --> Owner: 24. Display empty review list message
                    else reviews available
                        HostReviewsPage --> Owner: 25. Display review summary, filters, and list
                        HostReviewsPage --> HostReviewCard: 26. Display review content and detail ratings
                        activate HostReviewCard
                        HostReviewCard --> Owner: 26a. Show guest, hotel, rating, title, content, criteria, and owner response
                        Owner -> HostReviewsPage: 27. Filter by response status, switch analytics tab, or change page
                        HostReviewsPage -> HostReviewsPage: 28. Update displayed review list or analytics from loaded reviews
                        HostReviewsPage --> Owner: 28a. Display updated review view
                        deactivate HostReviewCard
                    end
                end
            end
        end
    end
    deactivate HostReviewsPage
end

deactivate HostLayout
deactivate Owner
@enduml
