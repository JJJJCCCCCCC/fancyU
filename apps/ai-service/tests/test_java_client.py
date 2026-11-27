import asyncio
from unittest.mock import patch

from app.config import Settings
from app.java_client import JavaQueryClient
from app.models import Intent, QueryPlan


class _Response:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return {"total": 7}


class _RecordingClient:
    timeout: float | None = None
    request: dict | None = None

    def __init__(self, *, timeout: float) -> None:
        type(self).timeout = timeout

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args) -> None:
        return None

    async def post(self, url: str, **kwargs):
        type(self).request = {"url": url, **kwargs}
        return _Response()


def test_query_forwards_typed_plan_and_tracing_headers() -> None:
    settings = Settings(
        service_jwt_secret="test-secret-that-is-at-least-32-characters",
        java_api_url="http://java.example",
        java_api_timeout_seconds=5,
    )
    client = JavaQueryClient(settings)

    with patch("app.java_client.httpx.AsyncClient", _RecordingClient):
        result = asyncio.run(
            client.query(
                QueryPlan(intent=Intent.COUNT_USERS),
                token="service-token",
                request_id="request-123",
            )
        )

    assert result == {"total": 7}
    assert _RecordingClient.timeout == 5
    assert _RecordingClient.request == {
        "url": "http://java.example/internal/ai/query",
        "headers": {
            "Authorization": "Bearer service-token",
            "X-Request-ID": "request-123",
        },
        "json": {"intent": "count_users", "parameters": {}},
    }
