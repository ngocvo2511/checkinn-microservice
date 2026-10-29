@startuml
actor User
boundary "Sign In page" as LoginModal
control "Auth Controller" as AuthController
control "Auth Service" as AuthService
control "Jwt Service" as JwtService
control "User Service" as UserService
entity "UserDB" as UserDB
boundary "Home Page" as Router

User -> LoginModal: 1. Select Login
activate User
activate LoginModal
LoginModal -> User: 2. Display login form
User -> LoginModal: 3. Enter username or email and password
User -> LoginModal: 4. Submit login form

LoginModal -> LoginModal: 5. Validate input on frontend
LoginModal -> AuthController: 6. authApi.login(usernameOrEmail, password)
activate AuthController
AuthController -> AuthService: 7. authService.login(request)
activate AuthService
note right of AuthService: AuthService delegates credential verification to user-service via gRPC
AuthService -> UserService: 7a. login(usernameOrEmail, password) via gRPC
activate UserService
UserService -> UserDB: 7b. Find account and verify password/email status
activate UserDB
UserDB --> UserService: 7c. Login result
deactivate UserDB
UserService --> AuthService: 7d. UserResponse
deactivate UserService
note over LoginModal,AuthService: 8. Is login information valid?

alt invalid
	AuthController -> LoginModal: 9. Display login error message
else valid
	AuthService -> JwtService: 10a. generateToken(userId, role)
	activate JwtService
	JwtService-> AuthService: 10b. return token
	deactivate JwtService
	AuthService --> AuthController: 11. Return token and user information
	AuthController --> LoginModal: 11. Return token and user information
	LoginModal -> LoginModal: 12. useAuth.login(response) / localStorage.setItem("token", response.token)
	LoginModal -> Router: 13. Redirect user based on role or reload page
	activate Router
end

deactivate AuthService
deactivate AuthController
deactivate LoginModal

@enduml