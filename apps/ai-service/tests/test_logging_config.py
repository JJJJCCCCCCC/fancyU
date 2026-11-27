import json

from app.logging_config import access_log_record


def test_access_log_contains_operational_fields_only() -> None:
    record = json.loads(
        access_log_record(
            request_id="req-1",
            method="POST",
            path="/v1/chat",
            status=200,
            duration_ms=12.5,
        )
    )

    assert record == {
        "event": "http_request",
        "request_id": "req-1",
        "method": "POST",
        "path": "/v1/chat",
        "status": 200,
        "duration_ms": 12.5,
    }
