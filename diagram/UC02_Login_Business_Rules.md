Activity	BR Code	Description
5	BR1	Validate Rules:
❖ The system checks the items [email] and [password] (View: `app/page.tsx`, Component: `LoginModal`).
❖ If [email] or [password] is null or blank, the system shows MSG01. 
7	2	Validate Rules:
❖ The system checks the items [usernameOrEmail] and [password] (UserService.login([usernameOrEmail], [password])).
❖ If [usernameOrEmail] does not exist, the system shows MSG02.
❖ If the account matching [usernameOrEmail] has not verified email, the system shows MSG02.
❖ If hash([password]) does not match the stored password, the system shows MSG02.
9	BR2	Message Rules:
❖ ❖ The system shows MSG01 or MSG02
10	BR3	Token Rules:
❖ If login information is valid, the system generates [token] from [userId] and [role] (JwtService.generateToken([userId], [role])).
11	BR4	Response Rules:
❖ The system returns [token], [userId], [email], [fullName], and [role].
12	BR5	Session Rules:
❖ The system stores [token] in localStorage.
❖ The system stores the authenticated [user] session in localStorage.
13	BR6	Redirect Rules:
❖ If [role] is ADMIN, the system redirects to /admin/dashboard.
❖ If [role] is OWNER or HOST, the system redirects to /host/dashboard.
❖ Otherwise, the system reloads the current page to update the customer login state.
