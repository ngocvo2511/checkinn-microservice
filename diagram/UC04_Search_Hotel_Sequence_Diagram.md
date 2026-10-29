@startuml
actor Customer
boundary "Search Page\n(app/page.tsx)" as SearchPage
control "Hotel Controller\n(hotel-service)" as HotelController
control "City Controller\n(hotel-service)" as CityController
control "Hotel Service\n(hotel-service)" as HotelService
control "Availability Service\n(hotel-service)" as AvailabilityService
control "Localtion Service\n(hotel-service)" as LocationService
entity "HotelDB" as HotelDB
boundary "Router" as Router

Customer -> SearchPage: 1. Access homepage
activate Customer
activate SearchPage
SearchPage-> Customer : 2. Display hotel search form

Customer -> SearchPage: 3. Enter destination or hotel name,\nstay dates, guests, and rooms
Customer -> SearchPage: 4. Submit search request

SearchPage -> SearchPage: 5. Validate search inputs
alt search by hotel name
    SearchPage -> HotelController: 6. hotelApi.searchHotelsByName(hotelName)
    activate HotelController
    HotelController -> HotelService: 7. hotelService.searchByName(name)
    activate HotelService
    HotelService -> HotelDB: 7a. query hotels by name
    activate HotelDB
    HotelDB --> HotelService: 7b. matching hotels
    deactivate HotelDB
    HotelService--> HotelController: 7c. return result
    deactivate HotelService
    HotelController --> SearchPage: 7d. return result
    deactivate HotelController
    
note right of Customer: 8. Hotel found?
    alt Hotels found
        SearchPage -> Router: 9. Redirect user to hotel detail page
    else No hotels found
        SearchPage --> Customer: 10. Display no hotels found message
    end

else search by location
    SearchPage -> CityController: 11. (GET /api/hotel/cities/search/locations?query=...)
    activate CityController
    CityController -> LocationService: 12a. locationService.searchLocations(query)
    activate LocationService
    LocationService-> HotelDB: 12b. query locations/cities
    activate HotelDB
    HotelDB --> LocationService: 12c. cities/provinces found
    deactivate HotelDB
    LocationService --> CityController: 12d. return result
    deactivate LocationService
    CityController --> SearchPage: 12e. return result
    deactivate CityController
    
    SearchPage -> HotelController: 13. hotelApi.searchHotelsByCity(cityId)
    activate HotelController
    HotelController -> HotelService: 14a. hotelService.getByCity(cityId)
    activate HotelService
    HotelService -> HotelDB: 14b. query hotels by city
    activate HotelDB
    HotelDB --> HotelService: 14c. hotels for city
    deactivate HotelDB
    HotelService--> HotelController : 14d. return hotels
    deactivate HotelService
    HotelController --> SearchPage: 14e. return hotels
    deactivate HotelController
    note right of Customer: 15. Hotel found?

    alt Hotels found
        SearchPage -> Customer: 16. Display matching hotel list
    else No hotels found
        SearchPage -> Customer: 17. Display no hotels found message
    end
end

deactivate SearchPage
deactivate Customer
@enduml
