@startuml

|Customer|
start
:1. Access homepage;

|System|
:2. Display hotel search form;

repeat
|Customer|
:3. Enter destination or hotel name,
stay dates, guests, and rooms;
:4. Submit search request;

|System|
:5. Validate search inputs;
backward :5b. Display search validation error message;
repeat while (5a. Is search information valid?) is (No) not (Yes)

if (Search by hotel name?) then (Yes)
|System|
:6. Search request sent to backend;
:7. Search hotels by name;


if (8. Hotels found?) then (Yes)
:9. Redirect to hotel detail page;
else (No)
:10. Display no hotels found message;

endif
else (No)
|System|
:11. Send location search request to backend;
:12. Resolve location to city IDs;
:13. Send hotels search request to backend for selected city;
:14. Search hotels for selected city and return results;

if (15. Hotels found?) then (Yes)
:16. Display matching hotel list;
else (No)
:17. Display no hotels found message;
endif
endif

stop
@enduml
