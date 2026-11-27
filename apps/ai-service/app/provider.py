from typing import Any

import httpx

from app.config import Settings
from app.models import Intent, QueryPlan

PROVIDER_SAFE_INTENTS = {
    Intent.COUNT_USERS,
    Intent.MATCHING_STATS,
}
PROVIDER_SAFE_DISTRIBUTION_FIELDS = {"sex", "campus", "grade"}


def is_provider_safe_plan(plan: QueryPlan) -> bool:
    if plan.intent in PROVIDER_SAFE_INTENTS:
        return True
    return (
        plan.intent == Intent.DISTRIBUTION
        and plan.parameters.get("field") in PROVIDER_SAFE_DISTRIBUTION_FIELDS
    )


class ProviderAdapter:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    @property
    def enabled(self) -> bool:
        return bool(
            self._settings.provider_base_url
            and self._settings.provider_api_key
        )

    async def summarize(
        self,
        question: str,
        result: dict[str, Any],
        fallback: str,
    ) -> str:
        if not self.enabled:
            return fallback
        prompt = (
            "你是数据分析助手。只能根据给定的脱敏查询结果，用中文简洁回答；"
            "不得补充邮箱、联系方式、照片或未提供的信息。\n"
            f"问题：{question}\n查询结果：{result}"
        )
        async with httpx.AsyncClient(
            timeout=self._settings.provider_timeout_seconds
        ) as client:
            for attempt in range(2):
                try:
                    response = await client.post(
                        f"{self._settings.provider_base_url}/chat/completions",
                        headers={
                            "Authorization":
                                f"Bearer {self._settings.provider_api_key}"
                        },
                        json={
                            "model": self._settings.provider_model,
                            "messages": [{"role": "user", "content": prompt}],
                            "temperature": 0.2,
                            "max_tokens": 800,
                        },
                    )
                    response.raise_for_status()
                    content = response.json()["choices"][0]["message"]["content"]
                    if not isinstance(content, str) or not content.strip():
                        return fallback
                    return content
                except httpx.TransportError:
                    if attempt == 0:
                        continue
                    return fallback
                except (
                    httpx.HTTPStatusError,
                    KeyError,
                    IndexError,
                    TypeError,
                ):
                    return fallback
        return fallback
