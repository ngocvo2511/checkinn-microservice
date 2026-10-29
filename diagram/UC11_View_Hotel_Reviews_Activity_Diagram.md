@startuml

|Owner|
start
:1. Select hotel review management;

|System|
:2. Check owner authentication and role;

if (Is owner allowed?) then (No)
:3. Redirect user to login or home page;
stop
else (Yes)
endif

:4. Display host reviews page;
:5. Load owner's hotel list;
:6. Identify owner from login token;
:7. Get hotels owned by the owner;

if (8. Are owner hotels loaded successfully?) then (No)
:9. Display hotel loading error message;
stop
else (Yes)
endif

:10. Display hotel filter and review management area;

|Owner|
:11. Select all hotels or a specific hotel filter;

|System|
:12. Check login token before loading reviews;

if (Is login token available?) then (No)
:13. Redirect owner to host login page;
stop
else (Yes)
endif

:14. Decode owner identity for review request;
:15. Send review list request;
:16. Determine owned hotel scope;

if (17. Is selected hotel owned by owner?) then (No)
:18. Display unauthorized hotel review error message;
stop
else (Yes)
endif

:19. Get published reviews in owner scope;
:20. Enrich review display data;
:21. Return paged review list;
:22. Match review items with hotel names;

if (23. Are reviews available?) then (No)
:24. Display empty review list message;
stop
else (Yes)
endif

:25. Display review summary, filters, and list;

|Owner|
:26. View review content and detail ratings;
:27. Filter by response status, switch analytics tab, or change page;

|System|
:28. Update displayed review list or analytics from loaded reviews;
stop

@enduml
