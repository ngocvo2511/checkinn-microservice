Activity	BR Code	Description
5	BR14	Validate Rules:
❖ The system checks the item [email] (View: `app/page.tsx`).
❖ If [email] is null or blank, the system shows MSG16.
❖ Otherwise, the system sends the password reset request with [email].
7	BR16	OTP Rules:
❖ The system generates a 6-digit [otpCode].
❖ The system sets [expiryTime] to 10 minutes after OTP creation.
❖ The system sets [attemptCount] = 0 and [isVerified] = false for the OTP record.
(OtpService.generateAndSendOtp())

Notification Rules:
❖ The system sends [otpCode] to [email].
(sendOtpViaRabbitMQ([email], [otpCode]))
9	BR17	Message Rules:
❖ The system shows MSG16 or MSG17
13	BR18	Validate Rules:
❖ The system checks [otpValue].
❖ If [otpValue] is null, blank, or not 6 digits, the system shows MSG10.
(View: `app/page.tsx`, handleVerifyOtp())
❖ Otherwise, the system delegates OTP verification to `OtpService.verifyOtp()` (Component: `OtpService` in `auth-service`).
15		OTP Verification & Email Verification Rules:
❖ If [otpValue] does not match the saved [otpCode], the system shows MSG11.
❖ If [otpCode] is expired, the system shows MSG11.
❖ If [attemptCount] reaches the maximum allowed attempts, the system shows MSG11.
❖ Otherwise, the system marks the OTP as verified.
(OtpService.verifyOtp())
17	BR19	Message Rules:
❖ The system shows MSG10, MSG11, or MSG15. 
21	BR20	Validate Rules:
❖ The system checks the items [newPassword] and [confirmPassword].
❖ If [newPassword] or [confirmPassword] is null or blank, the system shows MSG18.
❖ If [newPassword] does not equal [confirmPassword], the system shows MSG07.
❖ Otherwise, the system sends password reset request to backend with [email] and [newPassword].
(View: `app/page.tsx`, handleResetPassword())
23		Reset Rules:
❖ The system checks the items [newPassword].
❖ If [newPassword] has fewer than 6 characters, the system shows MSG19.
❖ The system finds the account by [email].
❖ The system hashes [newPassword].
❖ The system updates the account password with hash([newPassword]). 
(UserService.resetPassword())
25	BR21	Message Rules:
❖ The system shows MSG07, MSG18, MSG19, MSG20, or MSG21.
