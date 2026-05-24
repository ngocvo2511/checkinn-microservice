import http from "k6/http";
import { check } from "k6";
import { Counter } from "k6/metrics";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const token = __ENV.AUTH_TOKEN || "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIwZjE5OGMwNy0wNzFiLTRhYjYtODY1ZS0wZTQ5NjljMjFkMzgiLCJyb2xlIjoiQ1VTVE9NRVIiLCJpYXQiOjE3Nzk2MzAwMzYsImV4cCI6MTc3OTcxNjQzNn0.JWN7NH5aSUcFYxMR6kfk2hfJ_KovijNfatww6prrmF0";
const attempts = Number(__ENV.ATTEMPTS || "10");
const expectedSuccesses = Number(__ENV.EXPECTED_SUCCESSES || "1");
const successfulBookings = new Counter("successful_bookings");

function required(name) {
  const value = __ENV[name];
  if (!value) {
    throw new Error(`Set ${name} before running the booking hold concurrency test.`);
  }
  return value;
}

export const options = {
  scenarios: {
    concurrent_booking_attempts: {
      executor: "shared-iterations",
      vus: attempts,
      iterations: attempts,
      maxDuration: "30s",
    },
  },
  thresholds: {
    successful_bookings: [`count<=${expectedSuccesses}`],
  },
};

export default function () {
  const checkInDate = required("CHECK_IN_DATE");
  const checkOutDate = required("CHECK_OUT_DATE");
  const roomTypeId = required("ROOM_TYPE_ID");

  const payload = {
    userId: __ENV.USER_ID || `load-user-${__VU}`,
    hotelId: required("HOTEL_ID"),
    hotelName: __ENV.HOTEL_NAME || "Concurrency Test Hotel",
    checkInDate,
    checkOutDate,
    adults: Number(__ENV.ADULTS || "1"),
    children: Number(__ENV.CHILDREN || "0"),
    contactName: `Load User ${__VU}`,
    contactEmail: `load-user-${__VU}@example.com`,
    contactPhone: "0900000000",
    specialRequests: "booking hold concurrency test",
    items: [
      {
        roomTypeId,
        roomTypeName: __ENV.ROOM_TYPE_NAME || "Concurrency Test Room",
        ratePlanId: "d1e2f3a4-b5c6-7890-defa-111111111111" || null,
        checkInDate,
        checkOutDate,
        quantity: Number(__ENV.ROOM_QUANTITY || "1"),
        unitPrice: Number(__ENV.UNIT_PRICE || "1000000"),
        guestName: `Load User ${__VU}`,
        cancellationPolicy: "standard",
      },
    ],
  };

  const response = http.post(`${baseUrl}/api/bookings`, JSON.stringify(payload), {
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    tags: {
      scenario: "booking-hold-concurrency",
    },
  });

  console.log(`VU=${__VU}, status=${response.status}, body=${response.body}`);

  const created = response.status === 201;
  if (created) {
    successfulBookings.add(1);
  }

  check(response, {
    "created or rejected without server error": (res) => res.status === 201 || res.status === 400 || res.status === 409,
  });
}
