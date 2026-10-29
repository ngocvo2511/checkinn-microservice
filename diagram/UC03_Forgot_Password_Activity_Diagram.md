@startuml
|User|
start
:1. Select Forgot Password;

|System|
:2. Display email input form;

repeat
|User|
:3. Enter email;
:4. Submit password reset request;

|System|
:5. Validate email on frontend;
:6. Send email validation request to backend;
:7. Generate OTP code and send to user's email;
backward :9. Display email validation error message;
repeat while (8. Is email information valid?) is (No) not (Yes)

|System|
:10. Display OTP verification form;

repeat
|User|
:11. Enter OTP code;
:12. Submit OTP code;

|System|
:13. Validate OTP on frontend;
:14. Send OTP verification request to backend;
:15. Verify OTP code and mark email as verified;
backward :17. Display invalid or expired OTP message;
repeat while (16. Is OTP valid and not expired?) is (No) not (Yes)

|System|
:18. Display new password form;

repeat
|User|
:19. Enter new password and confirm password;
:20. Submit new password;

|System|
:21. Validate password on frontend;
:22. Send password reset request to backend;
:23. Reset password in database;
backward :25. Display password validation error message;
repeat while (24. Is new password information valid?) is (No) not (Yes)

|System|
:26. Display reset password success message;
:27. Close forgot password form;

|User|
:28. Return to login;

stop
@enduml