@startuml

|Customer|
start
:1. View hotel detail;
:2. Select room type and quantity;
:3. Click Book Room;

|System|
if (4. Is customer logged in?) then (No)
:5. Display login form;

|Customer|
:6. Log in;

|System|
endif

if (7. Is selected room type valid for requested quantity?) then (No)
:8. Display room selection error message;
stop
else (Yes)
endif

|System|
:9. Redirect to booking form with selected hotel, room, dates, guests, and quantity;
:10. Display booking form;

repeat
|Customer|
:11. Enter contact and guest information;
:12. Submit booking form;

|System|
backward :14. Display booking validation error message;
repeat while (13. Is booking information valid?) is (No) not (Yes)

|System|
:15. Create booking request;
:16. Hold selected room type for requested quantity and stay dates;

if (17. Is room hold successful?) then (No)
:18. Display booking creation error message;
stop
else (Yes)
endif

|System|
:19. Calculate total amount;
:20. Apply loyalty points discount if provided;
:21. Create booking record with PENDING status;
:22. Create booking items;
:23. Redirect to payment page;
:24. Display payment page and hold countdown;

repeat
|Customer|
:25. Select payment method;
:26. Confirm payment;

|System|
backward :28. Display payment validation error message;
repeat while (27. Is payment confirmation valid?) is (No) not (Yes)

if (29. Is payment method VNPay?) then (Yes)
|System|
:30. Create VNPay payment request;
:31. Redirect customer to VNPay payment gateway;

|VNPay|
:32. Process online payment;
:33. Return payment result;

|System|
if (34. Is VNPay payment successful?) then (No)
:35. Mark payment as FAILED and keep booking retryable;
:36. Display payment failure message;

|Customer|
if (37. Retry payment?) then (Yes)
|System|
:38. Verify existing room hold is still active;
if (39. Is room hold still valid?) then (No)
:40. Cancel booking because hold expired;
:41. Display hold expired message;
stop
else (Yes)
endif
:42. Create new VNPay payment request;
:43. Redirect customer to VNPay payment gateway;

|VNPay|
:44. Process online payment again;
:45. Return payment result;

|System|
if (46. Is retry payment successful?) then (No)
:47. Mark retry payment as FAILED and keep booking retryable;
:48. Display payment failure message;
stop
else (Yes)
:49. Mark retry payment as COMPLETED;
:50. Update booking status to CONFIRMED;
:51. Confirm held rooms;
:52. Process loyalty points and publish events;
:53. Display VNPay success confirmation;
endif
else (No)
|System|
:54. Keep booking as PENDING until hold expires;
:55. Display payment failure status;
stop
endif
else (Yes)
:56. Mark payment as COMPLETED;
:57. Update booking status to CONFIRMED;
:58. Confirm held rooms;
:59. Process loyalty points and publish events;
:60. Display VNPay success confirmation;
endif
else (No)
|System|
:61. Create hotel payment record;
:62. Update booking status to PENDING_PAYMENT;
:63. Display hotel payment confirmation;
endif

|Customer|
:64. View booking confirmation;

stop
@enduml
