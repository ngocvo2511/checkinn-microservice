@startuml
actor Customer
boundary "Register page" as Frontend
control "Auth Controller (auth-service)" as AuthController
control "Auth Service (auth-service)" as AuthService
control "Jwt Service" as JwtService
control "User Service (user-service)" as UserService
control "Otp Service (user-service)" as UserOtpService
control "Otp Controller (user-service)" as OtpController
control "Otp Service (auth-service)" as AuthOtpService
control "Notification Service" as NotificationService
entity "UserDB" as UserDB
boundary "Home Page" as Router

Customer -> Frontend: 1. Select Register Account
activate Customer
activate Frontend
Frontend -> Customer: 2. Display registration form

Customer -> Frontend: 3. Enter full name, username, \nemail, password, and confirm password
Customer -> Frontend: 4. Submit registration form
Frontend -> Frontend: 5. Validate input on frontend
Frontend -> AuthController: 6. authApi.register(payload)
activate AuthController
AuthController -> AuthService: 7. authService.register(request, userRole)
activate AuthService
AuthService -> UserService: 7a. registerUser(request, role) via gRPC
activate UserService
UserService -> UserDB: 7b. Check username/email uniqueness
activate UserDB
UserDB --> UserService: 7c. Return result
deactivate UserDB

note over Frontend,AuthService: 8. Is registration information valid?

alt invalid
	AuthController -> Frontend: 9. Display validation error message
else valid
	UserService -> UserDB: 10a. Create new account with unverified email status
activate UserDB
	UserDB --> UserService: 10b. User saved
deactivate UserDB

	UserService -> UserOtpService: 11a. generateAndSendOtp()
	activate UserOtpService
	UserOtpService -> UserDB: 11b. Create OTP record
activate UserDB
	UserDB --> UserOtpService: 11c. OTP saved
deactivate UserDB

	UserOtpService -> NotificationService: 12a. sendOtpViaRabbitMQ(email, otp)
activate NotificationService
deactivate NotificationService

	UserOtpService --> UserService: 12b. OTP sent to user's email
	deactivate UserOtpService
	UserService --> AuthService: 12c. Return UserResponse
deactivate UserService
AuthService -> JwtService: 12d. generateToken(userId, role)
	activate JwtService
JwtService-> AuthService: 12e. return token
	deactivate JwtService
	AuthService --> AuthController: 12f. Return AuthResponseDto
deactivate AuthService
	AuthController --> Frontend: 13. Display OTP verification form


deactivate UserService
deactivate AuthService
deactivate AuthController

Customer -> Frontend: 14. Enter OTP code
Customer -> Frontend: 15. Submit OTP code
Frontend -> Frontend: 16. Validate input on frontend
Frontend -> AuthController: 17. authApi.verifyOtp(email, otp)
activate AuthController
AuthController -> AuthOtpService: 18a. otpService.verifyOtp(email, otp)
activate AuthOtpService
AuthOtpService -> OtpController: 18b. POST /api/otp/verify via HTTP
activate OtpController
OtpController -> UserOtpService: 18c. otpService.verifyOtp(email, otp)
activate UserOtpService
UserOtpService -> UserDB: 18d. Verify OTP code and \nmark user's email as verified
activate UserDB
UserDB --> UserOtpService: 18e. OTP verification result
deactivate UserDB

UserOtpService --> OtpController: 18f. Verification result
deactivate UserOtpService 
OtpController -> UserService: 18g. verifyUserEmail(email)
activate UserService
UserService -> UserDB: 18h. Mark user's email as verified
activate UserDB
UserDB --> UserService: 18i. Email verified
deactivate UserDB

UserService --> OtpController: 18j. Email verification success
deactivate UserService
deactivate UserOtpService
OtpController --> AuthOtpService: 18k. Return result

deactivate OtpController
AuthOtpService --> AuthController: 18l. Verification result
deactivate AuthOtpService

note over Frontend,AuthController: 19. Is OTP valid and not expired?

alt invalid
	AuthController -> Frontend: 20. Display invalid or expired OTP message
else valid
	AuthController -> Frontend: 21. Notify frontend with verification success and token
	Frontend -> Frontend: 22. useAuth().setToken(token)
	Frontend -> Frontend: 23. useAuth().setUser(user)
	Frontend -> Router: 24. Redirect user to home page
	activate Router
end
end
deactivate AuthController
deactivate Frontend
deactivate Router

@enduml