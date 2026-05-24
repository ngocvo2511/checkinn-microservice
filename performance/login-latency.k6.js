import http from "k6/http";
import { check, sleep } from "k6";

const baseUrl = __ENV.BASE_URL || "http://localhost:8080";
const usernameOrEmail = __ENV.LOGIN_USER;
const password = __ENV.LOGIN_PASSWORD;
const duration = __ENV.DURATION || "1m";
const vus = Number(__ENV.VUS || "1");

if (!usernameOrEmail || !password) {
  throw new Error("Set LOGIN_USER and LOGIN_PASSWORD before running the login latency test.");
}

export const options = {
  vus,
  duration,
  thresholds: {
    http_req_failed: ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
  },
};

export default function () {
  const response = http.post(
    `${baseUrl}/api/auth/login`,
    JSON.stringify({ usernameOrEmail, password }),
    {
      headers: {
        "Content-Type": "application/json",
      },
      tags: {
        scenario: "login-latency",
      },
    }
  );

  check(response, {
    "login returned 200": (res) => res.status === 200,
    "login returned token": (res) => {
      try {
        return Boolean(res.json("token"));
      } catch {
        return false;
      }
    },
    "login under 500ms": (res) => res.timings.duration < 500,
  });

  sleep(1);
}
