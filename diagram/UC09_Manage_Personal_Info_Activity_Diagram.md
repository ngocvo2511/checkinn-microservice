@startuml

|Customer|
start
:1. Select personal information management;

|System|
:2. Check login token;

if (Is login token available?) then (No)
:3. Redirect unauthenticated user to home page;
stop
else (Yes)
endif

:4. Check whether the current role can stay on the personal data page;

if (Is role allowed on this page?) then (No)
:5. Redirect admin or owner to the matching dashboard;
stop
else (Yes)
endif

:6. Display personal data screen;
:7. Load profile;
:8. Identify user from authorization token;
:9. Get profile data;

if (10. Does profile exist?) then (No)
:10a. Create default profile;
else (Yes)
endif

:11. Return profile data;
:12. Load loyalty points;
:13. Return loyalty point summary;
:14. Display account profile and security tabs;

|Customer|
:15. Choose profile update or password change;

if (Selected action?) then (Update profile)
:16. Edit editable profile fields;
:17. Submit profile changes;

|System|
:18. Check login token before saving;

if (Is login token available?) then (No)
:19. Display authentication error message;
stop
else (Yes)
endif

:20. Send profile update request;
:21. Identify user from authorization token;
:22. Update editable non-null profile fields;
:23. Save profile;

if (Is profile update successful?) then (Yes)
:24. Return updated profile;
:25. Display profile update success message;
stop
else (No)
:26. Display profile update error message;
stop
endif

else (Change password)
:27. Open password and security tab;
:28. Enter current password, new password, and confirmation password;

|System|
:29. Validate password input;

if (Is password input valid?) then (No)
:30. Display password validation error message;
stop
else (Yes)
endif

:31. Check login token before changing password;

if (Is login token available?) then (No)
:32. Display authentication error message;
stop
else (Yes)
endif

:33. Send change password request;
:34. Identify user from authorization token;
:35. Validate current password and new password;

if (Is password change valid?) then (No)
:36. Display password change error message;
stop
else (Yes)
endif

:37. Save encoded new password;
:38. Display password change success message;
stop
endif

@enduml
