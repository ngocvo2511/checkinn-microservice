@startuml
actor Customer
boundary "Header\n(components/Header.tsx)" as Header
boundary "Personal Data Page\n(app/personal-data/page.tsx)" as PersonalDataPage
boundary "Customer Only Route\n(components/CustomerOnlyRoute.tsx)" as CustomerOnlyRoute
boundary "Personal Data Form\n(components/PersonalDataForm.tsx)" as PersonalDataForm
boundary "Router\n(next/navigation)" as Router
control "Auth API\n(lib/api/auth.ts)" as AuthApi
control "Loyalty API\n(lib/api/booking.ts)" as LoyaltyApi
control "User Controller\n(user-service)" as UserController
control "User Service\n(user-service)" as UserService
control "JWT Service\n(user-service)" as JwtService
control "Loyalty Points Controller\n(user-service)" as LoyaltyController
control "Loyalty Points Service\n(user-service)" as LoyaltyService
entity "UserDB" as UserDB
entity "UserProfileDB" as UserProfileDB
entity "LoyaltyPointsDB" as LoyaltyPointsDB

Customer -> Header: 1. Select personal information management
activate Customer
activate Header
Header -> Router: 1a. router.push("/personal-data")
Router --> PersonalDataPage: 1b. Display personal data route
deactivate Header
activate PersonalDataPage

PersonalDataPage -> PersonalDataPage: 2. localStorage.getItem("token")
alt token missing
    PersonalDataPage -> Router: 3. router.push("/")
else token exists
    PersonalDataPage -> CustomerOnlyRoute: 4. Render CustomerOnlyRoute
    activate CustomerOnlyRoute
    CustomerOnlyRoute -> CustomerOnlyRoute: 4a. decode JWT role
    alt role == "ADMIN"
        CustomerOnlyRoute -> Router: 5. router.replace("/admin/dashboard")
    else role == "OWNER"
        CustomerOnlyRoute -> Router: 5a. router.replace("/host/dashboard")
    else role allowed
        CustomerOnlyRoute --> PersonalDataForm: 6. Display personal data screen
        activate PersonalDataForm

        PersonalDataForm -> UserController: 7. GET /api/user/profile
        activate UserController
        UserController -> JwtService: 8. extractUserId(token)
        activate JwtService
        JwtService --> UserController: 8a. userId
        deactivate JwtService
        UserController -> UserService: 9. getUserProfile(userId)
        activate UserService
        UserService -> UserDB: 9a. userRepository.findById(userId)
        activate UserDB
        UserDB --> UserService: 9b. User
        deactivate UserDB
        UserService -> UserProfileDB: 9c. userProfileRepository.findByUserId(userId)
        activate UserProfileDB
        UserProfileDB --> UserService: 9d. UserProfile or empty
        note right of Customer: 10. Does profile exist?
        alt profile missing
            UserService -> UserProfileDB: 10a. userProfileRepository.save(newProfile)
            UserProfileDB --> UserService: 10b. UserProfile
        else profile exists
        end
        UserService --> UserController: 11. UserProfileDto
        deactivate UserProfileDB
        deactivate UserService
        UserController --> PersonalDataForm: 11a. UserProfileDto
        deactivate UserController

        PersonalDataForm -> LoyaltyApi: 12. loyaltyApi.getUserPoints(data.id)
        activate LoyaltyApi
        LoyaltyApi -> LoyaltyController: 12a. GET /api/loyalty-points/{userId}
        activate LoyaltyController
        LoyaltyController -> LoyaltyService: 12b. getLoyaltyPoints(userId)
        activate LoyaltyService
        LoyaltyService -> LoyaltyPointsDB: 12c. loyaltyPointsRepository.findByUserId(userId)
        activate LoyaltyPointsDB
        LoyaltyPointsDB --> LoyaltyService: 12d. LoyaltyPoints or empty
        LoyaltyService --> LoyaltyController: 13. LoyaltyPointsResponse
        deactivate LoyaltyPointsDB
        deactivate LoyaltyService
        LoyaltyController --> LoyaltyApi: 13a. LoyaltyPointsResponse
        deactivate LoyaltyController
        LoyaltyApi --> PersonalDataForm: 13b. LoyaltyPointsResponse
        deactivate LoyaltyApi

        PersonalDataForm --> Customer: 14. Display account profile and security tabs
        Customer -> PersonalDataForm: 15. Choose profile update or password change

        alt Update profile
            Customer -> PersonalDataForm: 16. Edit full name, phone, birthday, gender, country, and address
            Customer -> PersonalDataForm: 17. Submit profile changes
            PersonalDataForm -> PersonalDataForm: 18. localStorage.getItem("token")
            alt token missing
                PersonalDataForm --> Customer: 19. Display authentication error message
            else token exists
                PersonalDataForm -> UserController: 20. PUT /api/user/profile
                activate UserController
                UserController -> JwtService: 21. extractUserId(token)
                activate JwtService
                JwtService --> UserController: 21a. userId
                deactivate JwtService
                UserController -> UserService: 22. updateUserProfile(userId, dto)
                activate UserService
                UserService -> UserDB: 22a. userRepository.findById(userId)
                activate UserDB
                UserDB --> UserService: 22b. User
                deactivate UserDB
                UserService -> UserProfileDB: 22c. userProfileRepository.findByUserId(userId)
                activate UserProfileDB
                UserProfileDB --> UserService: 22d. UserProfile or empty
                UserService -> UserService: 22e. update non-null fullName, phone, gender, birthday, country, address
                UserService -> UserProfileDB: 23. userProfileRepository.save(profile)
                alt save succeeds
                    UserProfileDB --> UserService: 23a. saved profile
                    UserService --> UserController: 24. UserProfileDto
                    deactivate UserProfileDB
                    deactivate UserService
                    UserController --> PersonalDataForm: 24a. UserProfileDto
                    deactivate UserController
                    PersonalDataForm --> Customer: 25. Display profile update success message
                else save fails
                    UserProfileDB --> UserService: 26. error
                    deactivate UserProfileDB
                    deactivate UserService
                    UserController --> PersonalDataForm: 26a. error response
                    deactivate UserController
                    PersonalDataForm --> Customer: 26b. Display profile update error message
                end
            end
        else Change password
            Customer -> PersonalDataForm: 27. Open password and security tab
            Customer -> PersonalDataForm: 28. Enter current password, new password, and confirmation password
            PersonalDataForm -> PersonalDataForm: 29. validate passwordData
            alt invalid password input
                PersonalDataForm --> Customer: 30. Display password validation error message
            else valid password input
                PersonalDataForm -> PersonalDataForm: 31. localStorage.getItem("token")
                alt token missing
                    PersonalDataForm --> Customer: 32. Display authentication error message
                else token exists
                PersonalDataForm -> AuthApi: 33. authApi.changePassword(currentPassword, newPassword, token)
                activate AuthApi
                AuthApi -> UserController: 33a. PUT /api/user/change-password
                activate UserController
                UserController -> JwtService: 34. extractUserId(token)
                activate JwtService
                JwtService --> UserController: 34a. userId
                deactivate JwtService
                UserController -> UserService: 35. changePassword(userId, currentPassword, newPassword)
                activate UserService
                UserService -> UserDB: 35a. userRepository.findById(userId)
                activate UserDB
                UserDB --> UserService: 35b. User
                UserService -> UserService: 35c. passwordEncoder.matches(currentPassword, user.password) and newPassword length check
                alt invalid current password or new password
                    UserService --> UserController: 36. throw RuntimeException
                    deactivate UserDB
                    deactivate UserService
                    UserController --> AuthApi: 36a. error response
                    deactivate UserController
                    AuthApi --> PersonalDataForm: 36b. throw Error
                    deactivate AuthApi
                    PersonalDataForm --> Customer: 36c. Display password change error message
                else valid password change
                    UserService -> UserDB: 37. userRepository.save(user with encoded password)
                    UserDB --> UserService: 37a. saved user
                    deactivate UserDB
                    UserService --> UserController: 37b. void
                    deactivate UserService
                    UserController --> AuthApi: 37c. 200 OK
                    deactivate UserController
                    AuthApi --> PersonalDataForm: 37d. void
                    deactivate AuthApi
                    PersonalDataForm --> Customer: 38. Display password change success message
                end
                end
            end
        end
        deactivate PersonalDataForm
    end
    deactivate CustomerOnlyRoute
end

deactivate PersonalDataPage
deactivate Customer
@enduml
