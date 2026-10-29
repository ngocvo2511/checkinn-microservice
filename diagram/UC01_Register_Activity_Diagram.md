@startuml

|Customer|
start
:1. Access homepage;

|System|
:2. Display hotel search form;

repeat
|Customer|
:3. Enter destination or hotel name,\nstay dates, guests, and rooms;
:4. Submit search request;

|System|
:5. Validate search inputs;
backward :5a. Display search validation error message;
repeat while (5b. Is search information valid?) is (No) not (Yes)

if (Search by hotel name?) then (Yes)
|System|
:6. Sends search request to backend;
:7. Searches hotels by name;

:8. Redirect user to hotel detail page;
stop
else (Location)
|System|
:9. Frontend requests location suggestions from CityController;
:10. Location service searches locations and returns matching city IDs;
:11. Frontend requests hotels for selected city ID from HotelController;
:12. Service queries HotelDB for hotels in city and returns results;
endif

|System|
:13. Frontend displays matching hotel list to Customer;

stop
@enduml

