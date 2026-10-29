Activity	BR Code	Description
5	BR7	Validate Rules:
❖ The system checks the items [fullName], [username], [email], [password], and [confirmPassword] (View: `app/page.tsx`, Component: `SignupModal`).
❖ If any of them is null or blank, the system shows MSG01.
❖ If [password] does not equal [confirmPassword], the system shows MSG07.
❖ Otherwise, the system creates [registerData] from [fullName], [username], [email], and [password].
7		Validate Rules:
❖ The system checks the items [username], [email] (UserService.registerUser()).
❖ If [username] already exists, the system shows MSG08 (function: userRepository.existsByUsername([username])).
❖ If [email] already exists, the system shows MSG09 (function: userRepository.existsByEmail([email])).
9	BR8	Message Rules:
❖ The system shows the corresponding registration validation error message.
10	BR9	Account Rules:
(Service: `AuthService.register()`, External client: `UserGrpcClient`).
❖ The system creates a new [user] account with [isEmailVerified] = false.
❖ The system creates the user profile from [fullName].
11	BR10	OTP Rules:
(OtpService.generateAndSendOtp())
❖ The system generates a 6-digit [otpCode].
❖ The system sets [expiryTime] to 10 minutes after OTP creation.
❖ The system sets [attemptCount] = 0 and [isVerified] = false for the OTP record.
16	BR11	Validate Rules:
❖ The system checks [otpValue].
❖ If [otpValue] is null, blank, or not 6 digits, the system shows MSG10 (View: `app/page.tsx`, function: handleVerifyOtp()).
18		Validate Rules:
❖ The system checks [otpValue].
❖ If [otpValue] does not match the saved [otpCode], the system shows MSG11.
❖ If [otpCode] is expired, the system shows MSG11.
❖ If [attemptCount] reaches the maximum allowed attempts, the system shows MSG11.
❖ Otherwise, the system marks the OTP as verified (function: OtpService.verifyOtp()).
Account Rules:
❖ The system updates [isEmailVerified] = true for the account matching [email] (Controller: `OtpController.verifyOtp()`, Service: `UserService.verifyUserEmail()`).
20	BR12	Message Rules:
❖ The system shows MSG10, MSG11, or MSG15.
