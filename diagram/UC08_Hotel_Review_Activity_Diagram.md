@startuml

|Customer|
start
:1. Select write review from booking history;

|System|
if (2. Is booking eligible for review?) then (No)
:3. Keep write-review action unavailable;
stop
else (Yes)
endif

|System|
:4. Open review page with booking and hotel information;
:5. Check review page parameters and login token;

if (6. Is required review context available?) then (No)
:7. Return to booking history or display login required message;
stop
else (Yes)
endif

|System|
:8. Load booking information;
:9. Load hotel information for review context;
:10. Display review form;

|Customer|
:11. Enter rating criteria, title, and content;

|System|
:12. Calculate overall rating;

|Customer|
:13. Submit review;

|System|
:14. Validate review input;

if (15. Is review input valid?) then (No)
:16. Display review validation error message;
stop
else (Yes)
endif

|System|
:17. Send create review request;
:18. Identify customer from login token;
:19. Validate review payload;
:20. Check duplicate review for the booking;

if (21. Has customer already reviewed this booking?) then (Yes)
:22. Display duplicate review error message;
stop
else (No)
endif

|System|
:23. Publish and save review;
:24. Return created review;
:25. Display review success message;
:26. Redirect to booking history;
:27. Make the published review available on hotel detail page;

stop
@enduml
