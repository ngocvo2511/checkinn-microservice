# UC04 - Search Hotel Business Rules

| Activity | BR Code | Description |
|---|---|---|
| (5) | BR1 | Validate Rules:<br>❖ The system checks the search information from [selectedCity], [selectedHotel], [hotelName], [checkInDate], [checkOutDate], [adults], [children], and [rooms].<br>❖ If neither [selectedCity] nor [selectedHotel] nor [hotelName] is provided, the system shows MSG23.<br>❖ If [checkInDate] or [checkOutDate] is null, the system shows MSG24.<br>❖ If [checkOutDate] is not after [checkInDate], the system shows MSG24.<br>❖ If [adults] is less than 1, the system sets [adults] = 1.<br>❖ If [children] is less than 0, the system sets [children] = 0.<br>❖ If [rooms] is less than 1, the system sets [rooms] = 1. |
| (6) | BR2 | Message Rules:<br>❖ The system shows MSG23 or MSG24. |
| (8) | BR3 | Search Rules:<br>❖ If [hotelName] is provided, the system searches hotels by [hotelName].<br>❖ The system only keeps hotels that satisfy room availability and capacity rules for [checkIn], [checkOut], [rooms], [adults], and [children]. |
| (9) | BR4 | Validate Rules:<br>❖ The system checks whether the hotel name search result contains at least one eligible hotel.<br>❖ If no eligible hotel is found, the system shows MSG25. |
| (10) | BR5 | Message Rules:<br>❖ The system shows MSG25 or MSG29. |
| (11) | BR6 | Redirect Rules:<br>❖ The system redirects to the hotel detail page using [hotelId].<br>❖ The system keeps [checkIn], [checkOut], [rooms], [adults], and [children] in the URL search parameters. |
| (13) | BR7 | Search Rules:<br>❖ If [cityName] is provided, the system searches matching locations by [cityName].<br>❖ Matching locations may be city or province records. |
| (14) | BR8 | Validate Rules:<br>❖ The system checks whether at least one destination is found from [cityName].<br>❖ If no destination is found, the system shows MSG27. |
| (15) | BR9 | Message Rules:<br>❖ The system shows MSG27. |
| (16) | BR10 | Location Rules:<br>❖ If the matched destination is a city, the system adds its [cityId] to [cityIds].<br>❖ If the matched destination is a province, the system finds all cities in that province and adds their IDs to [cityIds]. |
| (17) | BR11 | Search Rules:<br>❖ The system fetches hotels for each [cityId] in [cityIds]. |
| (18) | BR12 | Data Rules:<br>❖ The system removes duplicate hotels by [id]. |
| (19) | BR13 | Filter Rules:<br>❖ The system only keeps hotels where [isActive] = true.<br>❖ The system only keeps hotels where [approvedStatus] = APPROVED. |
| (20) | BR14 | Filter Rules:<br>❖ The system checks [priceRange].<br>❖ The system only keeps hotels whose minimum room price is within [priceRange]. |
| (21) | BR15 | Review Rules:<br>❖ The system loads review statistics for each matching hotel.<br>❖ If review statistics cannot be loaded, the system continues showing the hotel with default rating information. |
| (22) | BR16 | Availability and Capacity Rules:<br>❖ The system checks active [roomTypes] of each hotel.<br>❖ The system checks available rooms for each [roomTypeId] by [checkIn] and [checkOut].<br>❖ The system does not combine different room types to satisfy one search request.<br>❖ The system only keeps hotels where at least one [roomTypeId] has [availableRooms] >= [rooms].<br>❖ The system only keeps hotels where that same [roomTypeId] has [capacity.adults] * [rooms] >= [adults].<br>❖ The system only keeps hotels where that same [roomTypeId] has [capacity.children] * [rooms] >= [children]. |
| (23) | BR17 | Message Rules:<br>❖ The system shows MSG30 if matching hotels are found.<br>❖ The system shows MSG27 if no matching hotel is found.<br>❖ The system shows MSG28 if the hotel list cannot be loaded. |
| (24) | BR18 | Filter Rules:<br>❖ The customer may filter by [priceRange], [amenities], [types], [starRatings], or [guestRatings]. |
| (25) | BR19 | Filter Rules:<br>❖ The system updates the matching hotel list using [priceRange], [amenities], [types], [starRatings], and [guestRatings]. |
| (28) | BR20 | Redirect Rules:<br>❖ When the customer selects a hotel, the system redirects to the hotel detail page using [hotelId].<br>❖ The system keeps [checkIn], [checkOut], [rooms], [adults], and [children] in the URL search parameters. |
