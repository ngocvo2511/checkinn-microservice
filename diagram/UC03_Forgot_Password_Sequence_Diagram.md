@startuml
actor User
boundary "Forgot Password Form\n(app/page.tsx)" as ForgotForm
control "Auth Controller\n(auth-service)" as AuthController
control "Auth Service\n(auth-service)" as AuthService
control "Otp Service\n(auth-service)" as OtpService
control "Otp Controller\n(user-service)" as OtpController
control "User Service\n(user-service)" as UserService
control "Otp Service\n(user-service)" as UserOtpService
control "Notification Service" as NotificationService
entity "UserDB" as UserDB
boundary "Router" as Router

User -> ForgotForm: 1. Select Forgot Password
activate User
activate ForgotForm
ForgotForm -> User: 2. Display email input form
User -> ForgotForm: 3. Enter email
User -> ForgotForm: 4. Submit password reset request

ForgotForm -> ForgotForm: 5. Validate email on frontend
ForgotForm -> AuthController: 6. authApi.forgotPassword(email)
activate AuthController
AuthController -> OtpService: 7. otpService.generateAndSendOtp(email)
activate OtpService
OtpService -> OtpController: 7a. POST /api/otp/generate?email=... via HTTP
activate OtpController
OtpController -> UserOtpService: 7b. generateAndSendOtp(email)
activate UserOtpService
UserOtpService -> UserDB: 7c. Generate OTP and save record
activate UserDB
UserDB --> UserOtpService: 7d. OTP persisted
deactivate UserDB
UserOtpService -> NotificationService: 7e. publishOtpEvent(email, otpCode) via RabbitMQ
activate NotificationService
deactivate NotificationService
UserOtpService --> OtpController: 7f. return success
deactivate UserOtpService
OtpController --> OtpService: 7g. return OTP sent confirmation
deactivate OtpController
OtpService --> AuthController: 7h. return OTP sent confirmation
deactivate OtpService
note over ForgotForm,AuthController: 8. Is email information valid?

alt invalid (step 8)
	AuthController -> ForgotForm: 9. Display email validation error message
else valid
	AuthController --> ForgotForm: 10. Display OTP verification form


deactivate AuthController

User -> ForgotForm: 11. Enter OTP code
User -> ForgotForm: 12. Submit OTP code

ForgotForm -> ForgotForm: 13. Validate OTP on frontend
ForgotForm -> AuthController: 14. authApi.verifyOtp(email, otp)
activate AuthController
AuthController -> OtpService: 15. otpService.verifyOtp(email, otp)
activate OtpService
note right of OtpService: OtpService calls user-service OTP verify endpoint via HTTP
OtpService -> OtpController: 15a. POST /api/otp/verify {email, otp} via HTTP
activate OtpController
OtpController -> UserOtpService: 15b. verifyOtp(email, otp)
activate UserOtpService
UserOtpService -> UserDB: 15c. Check OTP and expiry
activate UserDB
UserDB --> UserOtpService: 15d. OTP verification result
deactivate UserDB
UserOtpService --> OtpController: 15e. Verification success/failure
OtpController -> UserService: 15f. verifyUserEmail(email)
activate UserService
UserService -> UserDB: 15g. Mark user's email as verified
activate UserDB
UserDB --> UserService: 15h. Email verified
deactivate UserDB
UserService --> OtpController: 15i. Email verification success
deactivate UserService
deactivate UserOtpService
OtpController --> OtpService: 15j. return verification result
deactivate OtpController
OtpService --> AuthController: 15k. return verification result
note over ForgotForm,AuthController: 16. Is OTP valid and not expired?

alt invalid (step 18)
	AuthController -> ForgotForm: 17. Display invalid or expired OTP message
else valid (steps 20)
	AuthController --> ForgotForm: 18. Display new password form


deactivate AuthService
deactivate AuthController

User -> ForgotForm: 19. Enter new password and confirm password
User -> ForgotForm: 20. Submit new password

ForgotForm -> ForgotForm: 21. Validate password on frontend
ForgotForm -> AuthController: 22. authApi.resetPassword(email, otpCode, newPassword)
activate AuthController
AuthController -> AuthService: 23. authService.resetPassword(email, newPassword)
activate AuthService
AuthService -> UserService: 23a. resetPassword(email, newPassword) via gRPC
activate UserService
UserService -> UserDB: 23b. Update password in database
activate UserDB
UserDB --> UserService: 23c. Password updated
deactivate UserDB
UserService --> AuthService: 23d. Reset success
deactivate UserService
AuthService --> AuthController: 23e. Return result
note over ForgotForm,AuthService: 24. Is new password information valid?

alt invalid (step 26)
	AuthController -> ForgotForm: 25. Display password validation error message
else valid (steps 28-31)
	AuthController --> ForgotForm: 26. Display reset password success message
	ForgotForm -> ForgotForm: 27. Close forgot password form
	ForgotForm -> Router: 28. Redirect user to login page
	activate Router
end
end
end
deactivate AuthService
deactivate AuthController
deactivate ForgotForm

@enduml