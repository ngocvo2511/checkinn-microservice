@startuml

|Hotel Owner|
start
:1. Select booking management;

|System|
:2. Check owner authentication and role;

if (Is owner access valid?) then (No)
:3. Redirect user away from booking management;
stop
else (Yes)
endif

:4. Display booking management page;
:5. Check login token before loading owner hotels;

if (Is login token available?) then (No)
:6. Display login-required message;
stop
else (Yes)
endif

:7. Request hotel list owned by the current owner;
:8. Identify owner from authenticated token;
:9. Retrieve hotels by owner;
:10. Return owner hotel list;

if (11. Are owner hotels available?) then (No)
:12. Display no-hotel message and create-hotel action;
stop
else (Yes)
endif

:13. Select default or chosen hotel;
:14. Request booking list for selected hotel;
:15. Retrieve bookings by hotel id;
:16. Return booking list;

if (17. Is booking list loaded successfully?) then (No)
:18. Display booking loading error message;
stop
else (Yes)
endif

:19. Display booking statistics, search, filter, and table;

|Hotel Owner|
:20. Enter search text or select status filter;

|System|
:21. Filter booking list locally;

if (22. Are visible bookings available?) then (No)
:23. Display empty or no-matching-booking message;
stop
else (Yes)
endif

|Hotel Owner|
:24. Select a booking row;

|System|
:25. Display booking detail;

|Hotel Owner|
:26. Choose processing action;

|System|
if (27. Is hotel payment confirmation allowed?) then (Yes)
:28. Send hotel payment confirmation request;
:29. Mark onsite payment completed, confirm booking, and confirm held room;
:30. Display payment success message and reload bookings;
stop
else (No)
endif

if (31. Is booking status update allowed?) then (Yes)
:32. Send next booking status update request;
:33. Save booking status and publish status event;
:34. Display status update success message and reload bookings;
stop
else (No)
:35. Close booking detail without changing data;
stop
endif

@enduml
