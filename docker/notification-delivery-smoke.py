"""Real SMTP/retry/DLQ/privacy checks in a disposable test RabbitMQ vhost.

Requires the rebuilt notification image, checkinn-test RabbitMQ and Mailpit only.
Never consumes existing queues or uses external SMTP/production databases.
No message bodies, credentials or private log excerpts are printed.
"""
import base64
import importlib.util
import json
from pathlib import Path
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parent
spec = importlib.util.spec_from_file_location("h", ROOT / "booking-security-smoke.py")
h = importlib.util.module_from_spec(spec)
spec.loader.exec_module(h)


def main():
    config = json.loads(h.run(h.COMPOSE + ["config", "--format", "json"]))
    env = config["services"]["notification-service"]["environment"]
    assert env["SPRING_MAIL_HOST"] == "mailpit", "NEEDS_ENVIRONMENT: test SMTP must be Mailpit"
    rabbit = config["services"]["rabbitmq"]["environment"]
    user, password = rabbit["RABBITMQ_DEFAULT_USER"], rabbit["RABBITMQ_DEFAULT_PASS"]
    auth = "Basic " + base64.b64encode((user + ":" + password).encode()).decode()
    marker = "synthetic-private-" + uuid.uuid4().hex
    vhost = "notificationprobe" + uuid.uuid4().hex
    email = marker + "@example.invalid"
    proof = "synthetic-proof-" + uuid.uuid4().hex
    workers, mail_ids = [], []
    count = 0

    def api(path, data=None, method=None, mail=False):
        headers = {"Content-Type": "application/json"}
        if not mail:
            headers["Authorization"] = auth
        request = urllib.request.Request(("http://127.0.0.1:8025" if mail else "http://127.0.0.1:15672")
                                         + path, headers=headers, method=method,
                                         data=None if data is None else json.dumps(data).encode())
        try:
            with urllib.request.urlopen(request, timeout=10) as response:
                raw = response.read()
                if not raw:
                    return None
                return json.loads(raw) if "json" in response.headers.get("Content-Type", "") else None
        except urllib.error.HTTPError as error:
            code = error.code
            error.close()
            raise RuntimeError("Test broker/SMTP API failed with status " + str(code)) from None

    def check(ok, name):
        nonlocal count
        if not ok:
            raise AssertionError(name)
        count += 1
        print(json.dumps({"case": name, "result": "PASS"}), flush=True)

    def wait(predicate, description, timeout=65):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            result = predicate()
            if result:
                return result
            time.sleep(1)
        raise RuntimeError("Timed out: " + description)

    def queue(name):
        try:
            return api("/api/queues/" + vhost + "/" + name)
        except RuntimeError:
            return {}

    def start_worker(fail):
        name = vhost + ("-failure" if fail else "-success")
        h.run(h.COMPOSE + ["run", "--no-deps", "-d", "--name", name,
                           "-e", "SPRING_RABBITMQ_VIRTUALHOST=" + vhost,
                           "-e", "SPRING_MAIL_PORT=" + ("1" if fail else "1025"),
                           "-e", "SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT=2000",
                           "-e", "SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT=2000",
                           "-e", "SPRING_BOOT_ADMIN_CLIENT_ENABLED=false", "notification-service",
                           "--spring.rabbitmq.virtual-host=" + vhost])
        workers.append(name)
        wait(lambda: queue("notification.payment.queue").get("consumers", 0) == 1
             and queue("notification.otp.queue").get("consumers", 0) == 1, "worker consumers ready")
        return name

    def publish(exchange, key, body, headers=None):
        payload = body if isinstance(body, str) else json.dumps(body)
        response = api("/api/exchanges/" + vhost + "/" + exchange + "/publish", {
            "properties": {"content_type": "application/json", "headers": headers or {"private": marker}},
            "routing_key": key, "payload": payload, "payload_encoding": "string"})
        assert response["routed"], "Synthetic message must be routed"
        return payload

    def payment(status):
        return {"bookingId": marker, "userEmail": email, "userName": marker, "hotelName": marker,
                "bookingStatus": status, "checkInDate": "2027-01-10", "checkOutDate": "2027-01-11",
                "nights": 1, "rooms": 1, "numberOfGuests": 1, "amount": 10, "paymentMethod": "VNPAY"}

    def private_logs(name):
        # Docker captures stdout and stderr separately; check both, never print them.
        import subprocess
        result = subprocess.run(["docker", "logs", name], capture_output=True, text=True, encoding="utf-8", timeout=20)
        assert result.returncode == 0
        return result.stdout + result.stderr

    try:
        api("/api/vhosts/" + vhost, {}, "PUT")
        api("/api/permissions/" + vhost + "/" + urllib.parse.quote(user, safe=""),
            {"configure": ".*", "write": ".*", "read": ".*"}, "PUT")
        failed_worker = start_worker(True)
        payment_body = publish("hotel.events", "payment.completed", payment("PENDING_PAYMENT"))
        otp_body = publish("user.events", "otp.verification",
                           {"email": email, "otp_code": proof, "event_type": "otp_verification"})
        wait(lambda: "NOTIFICATION_RETRY_EXHAUSTED" in private_logs(failed_worker)
             and private_logs(failed_worker).count("NOTIFICATION_RETRY_EXHAUSTED") == 2, "bounded SMTP retries")
        output = private_logs(failed_worker)
        check(output.count("[NOTIFICATION_PAYMENT_EVENT_ERROR]") == 3, "payment SMTP failure retried exactly three times")
        check(output.count("[NOTIFICATION_OTP_EVENT_ERROR]") == 3, "OTP SMTP failure retried exactly three times")
        check("EVENT_PROCESSED" not in output and "EMAIL_SENT" not in output, "failed SMTP never acknowledged as successful send")

        def take(queue_name):
            return api("/api/queues/" + vhost + "/" + queue_name + "/get",
                       {"count": 10, "ackmode": "ack_requeue_false", "encoding": "auto"})

        for queue_name, body in [("notification.payment.dlq", payment_body), ("notification.otp.dlq", otp_body)]:
            messages = wait(lambda: take(queue_name), "dead-letter arrival")
            check(len(messages) == 1 and messages[0]["payload"] == body,
                  queue_name + " retains exact failed synthetic event")
            death = messages[0]["properties"]["headers"]["x-death"][0]
            check(death["reason"] == "rejected" and death["count"] == 1, queue_name + " records broker rejection")

        # A malformed JSON body fails before application listeners, including a fatal replay.
        malformed = '{"private":"' + marker + '"'
        publish("user.events", "otp.verification", malformed)
        messages = wait(lambda: take("notification.otp.dlq"), "conversion failure dead-letter")
        check(len(messages) == 1 and messages[0]["payload"] == malformed, "malformed JSON is quarantined intact")
        publish("user.events", "otp.verification", malformed, messages[0]["properties"]["headers"])
        replay = wait(lambda: take("notification.otp.dlq"), "fatal replay quarantine")
        check(len(replay) == 1 and replay[0]["payload"] == malformed, "fatal x-death replay remains quarantined")
        output = private_logs(failed_worker)
        check(all(value not in output for value in (marker, email, proof)), "SMTP retry and conversion logs exclude body header identity and proof")
        h.run(["docker", "stop", failed_worker])

        successful_worker = start_worker(False)
        publish("hotel.events", "payment.completed", payment("PENDING_PAYMENT"))
        publish("hotel.events", "payment.completed", payment("CONFIRMED"))
        publish("user.events", "otp.verification", {"email": email, "otp_code": proof, "event_type": "otp_verification"})

        def received():
            found = api("/api/v1/search?query=" + urllib.parse.quote("to:" + email), mail=True)
            # Mailpit's total is the global mailbox count, even for filtered searches.
            return found.get("messages", []) if len(found.get("messages", [])) == 3 else None

        received_messages = wait(received, "three successful SMTP deliveries")
        mail_ids.extend(message["ID"] for message in received_messages)
        contents = [api("/api/v1/message/" + value, mail=True)["HTML"] for value in mail_ids]
        check(sum("đặt phòng chưa được xác nhận" in body for body in contents) == 1, "paid pending email does not promise a room")
        check(sum("Phòng đã được xác nhận." in body for body in contents) == 1, "confirmed email retains confirmed-room wording")
        check(sum(proof in body for body in contents) == 1, "OTP reaches Mailpit without losing its proof")
        wait(lambda: private_logs(successful_worker).count("EVENT_PROCESSED") == 3, "success acknowledgements")
        output = private_logs(successful_worker)
        check(all(value not in output for value in (marker, email, proof)), "successful listener and SMTP logs exclude private payload")
        check(not take("notification.payment.dlq") and not take("notification.otp.dlq"), "successful SMTP does not dead-letter")
        wait(lambda: all(queue(name).get("messages_ready") == 0 and queue(name).get("messages_unacknowledged") == 0
                         for name in ("notification.payment.queue", "notification.otp.queue")), "successful broker acknowledgements")
        check(True, "successful deliveries leave no ready or unacknowledged messages")
    finally:
        for worker in workers:
            h.run(["docker", "rm", "-f", worker])
        # Discover only this run's exact recipient, including partially completed failures.
        found = api("/api/v1/search?query=" + urllib.parse.quote("to:" + email), mail=True)
        ids = list({message["ID"] for message in found.get("messages", [])})
        # Mailpit DELETE without IDs deletes ALL mail: never issue an empty request.
        if ids:
            api("/api/v1/messages", {"IDs": ids}, "DELETE", mail=True)
        api("/api/vhosts/" + vhost, method="DELETE")
        remaining = api("/api/v1/search?query=" + urllib.parse.quote("to:" + email), mail=True)
        check(not remaining.get("messages"), "exact synthetic mail cleanup verified")
    print(json.dumps({"verified_cases": count, "failures": 0, "synthetic_resources_cleaned": True}), flush=True)


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Some library exceptions carry raw bodies/URLs. Print the type only.
        detail = str(error) if isinstance(error, RuntimeError) and str(error).startswith(
            ("Test broker/SMTP API failed", "Timed out:", "Test stack command failed")) else None
        print(json.dumps({"result": "FAIL", "error_type": type(error).__name__, "safe_detail": detail}), flush=True)
        raise SystemExit(1)
