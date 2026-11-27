import asyncio
from unittest.mock import patch

import httpx

from app.config import Settings
from app.models import Intent, QueryPlan
from app.provider import ProviderAdapter, is_provider_safe_plan


class _Response:
    def raise_for_status(self) -> None:
        return None

    def json(self) -> dict:
        return {"choices": [{"message": {"content": "重试后的回答"}}]}


class _FlakyClient:
    attempts = 0

    def __init__(self, **_kwargs) -> None:
        pass

    async def __aenter__(self):
        return self

    async def __aexit__(self, *_args) -> None:
        return None

    async def post(self, *_args, **_kwargs):
        type(self).attempts += 1
        if type(self).attempts == 1:
            raise httpx.ConnectError("temporary connection failure")
        return _Response()


class _InvalidResponse(_Response):
    def json(self) -> dict:
        return {"choices": [{"message": {"content": {"unexpected": True}}}]}


class _InvalidContentClient(_FlakyClient):
    async def post(self, *_args, **_kwargs):
        return _InvalidResponse()


def test_provider_retries_one_transient_failure() -> None:
    settings = Settings(
        service_jwt_secret="test-secret-that-is-at-least-32-characters",
        provider_base_url="https://provider.example/v1",
        provider_api_key="test-key",
    )
    adapter = ProviderAdapter(settings)
    _FlakyClient.attempts = 0

    with patch("app.provider.httpx.AsyncClient", _FlakyClient):
        answer = asyncio.run(
            adapter.summarize("总共有多少用户", {"total": 1}, "降级回答")
        )

    assert answer == "重试后的回答"
    assert _FlakyClient.attempts == 2


def test_provider_falls_back_for_non_string_content() -> None:
    settings = Settings(
        service_jwt_secret="test-secret-that-is-at-least-32-characters",
        provider_base_url="https://provider.example/v1",
        provider_api_key="test-key",
    )
    adapter = ProviderAdapter(settings)

    with patch("app.provider.httpx.AsyncClient", _InvalidContentClient):
        answer = asyncio.run(
            adapter.summarize("总共有多少用户", {"total": 1}, "降级回答")
        )

    assert answer == "降级回答"


def test_provider_only_receives_aggregate_intents() -> None:
    assert is_provider_safe_plan(QueryPlan(intent=Intent.COUNT_USERS))
    assert is_provider_safe_plan(QueryPlan(intent=Intent.MATCHING_STATS))
    for field in ("sex", "campus", "grade"):
        assert is_provider_safe_plan(
            QueryPlan(
                intent=Intent.DISTRIBUTION,
                parameters={"field": field},
            )
        )
    for field in ("hometown", "interests", "experience", "future_plan"):
        assert not is_provider_safe_plan(
            QueryPlan(
                intent=Intent.DISTRIBUTION,
                parameters={"field": field},
            )
        )
    for intent in (
        Intent.SEARCH_USERS,
        Intent.RECENT_USERS,
        Intent.USER_DETAIL,
    ):
        assert not is_provider_safe_plan(QueryPlan(intent=intent))
