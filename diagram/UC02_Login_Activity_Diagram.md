@startuml
|User|
start
:1. Select Login;

|System|
:2. Display login form;

repeat
|User|
:3. Enter username or email and password;
:4. Submit login form;


|System|
:5. Validate input on frontend;
:6. Send login request to authentication service;
:7. Validate login information and check role;
backward :9. Display login error message;
repeat while (8. Is login information valid?) is (No) not (Yes)

|System|
:10. Generate authentication token;
:11. Return token and user information;



|System|
:12. Store authentication session;
:13. Redirect user to home page;


stop
@enduml
