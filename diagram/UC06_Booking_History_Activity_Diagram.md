@startuml

|Customer|
start
:1. Select booking history;

|System|
:2. Check customer login information;

if (3. Is customer logged in?) then (No)
:4. Display login required message;
stop
else (Yes)
endif

|System|
:5. Send booking history request;
:6. Retrieve bookings by customer;
:7. Prepare booking history response;

if (8. Is booking history loaded successfully?) then (No)
:9. Display booking history loading error message;
stop
else (Yes)
endif

|System|
:10. Sort bookings by newest created date;
:11. Load hotel images for booking list;
:12. Check review status for checked-out bookings;

if (13. Are bookings available?) then (No)
:14. Display empty booking history message;
stop
else (Yes)
:15. Display booking history list;
endif

|Customer|
:16. Select a booking to view detail;

|System|
:17. Send booking detail request;
:18. Retrieve booking detail;

if (19. Is booking detail loaded successfully?) then (No)
:20. Display booking detail loading error message;
stop
else (Yes)
:21. Display booking detail;
endif

stop
@enduml
