import json
from typing import Any

import httpx

from app.config import Settings
from app.models import Intent, QueryPlan

PROVIDER_SAFE_INTENTS = {
    Intent.COUNT_USERS,
    Intent.MATCHING_STATS,
}
PROVIDER_BUCKETS = {
    "sex": {"男", "女"},
    "campus": {
        "东校园",
        "中山校区",
        "云浮校区",
        "佛山南海校区",
        "北校园",
        "南校园",
        "广州增城广园东",
        "广州天河东校",
        "广州天河五山",
        "广州天河校区",
        "广州天河沙河",
        "广州天河石牌",
        "广州天河龙洞",
        "广州海珠南校",
        "广州海珠昌岗",
        "广州番禺大学城",
        "广州番禺校区",
        "广州白云三元里",
        "广州白云校区",
        "广州花都国际",
        "广州越秀东风东",
        "广州越秀北校",
        "广州越秀校区",
        "广州越秀桂花岗",
        "揭阳校区",
        "深圳",
        "深圳校区",
        "珠海",
        "珠海校区",
    },
    "grade": {
        "大一",
        "大二",
        "大三",
        "大四",
        "大五",
        "大五（医学）",
        "研0",
        "研一",
        "研二",
        "研三",
        "博士",
    },
}
PROVIDER_DISTRIBUTION_LABELS = {"sex": "性别", "campus": "校区", "grade": "年级"}


def is_provider_safe_plan(plan: QueryPlan) -> bool:
    if plan.intent in PROVIDER_SAFE_INTENTS:
        return True
    field = plan.parameters.get("field")
    return (
        plan.intent == Intent.DISTRIBUTION
        and isinstance(field, str)
        and field in PROVIDER_BUCKETS
    )


def _counts(data: Any, keys: tuple[str, ...]) -> dict | None:
    if not isinstance(data, dict):
        return None
    counts = {key: data.get(key) for key in keys}
    if any(type(count) is not int or count < 0 for count in counts.values()):
        return None
    return counts


def _buckets(data: Any, field: str) -> list[dict]:
    if not isinstance(data, list):
        return []
    buckets = []
    for row in data:
        if not isinstance(row, dict):
            continue
        label = row.get("value")
        count = _counts(row, ("count",))
        if (
            count is not None
            and isinstance(label, str)
            and label in PROVIDER_BUCKETS[field]
        ):
            buckets.append({"value": label, **count})
    return buckets


def _provider_aggregate(
    plan: QueryPlan, result: dict
) -> tuple[str, dict | list] | None:
    data = result.get("data")
    if plan.intent == Intent.COUNT_USERS and result.get("type") == "count":
        counts = _counts(data, ("total", "male", "female"))
        if counts is not None:
            return "用户总数统计", counts
    if plan.intent == Intent.MATCHING_STATS and result.get("type") == "stats":
        counts = _counts(data, ("totalAccounts", "totalMatching"))
        if counts is not None and isinstance(data.get("byCampus"), list):
            return "匹配参与统计", {
                **counts,
                "byCampus": _buckets(data["byCampus"], "campus"),
            }
    if plan.intent == Intent.DISTRIBUTION and result.get("type") == "stats":
        field = plan.parameters["field"]
        buckets = _buckets(data, field)
        if buckets:
            return f"{PROVIDER_DISTRIBUTION_LABELS[field]}分布", buckets
    return None


class ProviderAdapter:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    @property
    def enabled(self) -> bool:
        return bool(
            self._settings.provider_base_url and self._settings.provider_api_key
        )

    async def summarize(
        self,
        plan: QueryPlan,
        result: dict[str, Any],
        fallback: str,
    ) -> str:
        if not self.enabled or not is_provider_safe_plan(plan):
            return fallback
        aggregate = _provider_aggregate(plan, result)
        if aggregate is None:
            return fallback
        question, data = aggregate
        prompt = (
            "你是数据分析助手。只能根据给定的脱敏查询结果，用中文简洁回答；"
            "不得补充邮箱、联系方式、照片或未提供的信息。\n"
            f"问题：{question}\n查询结果：{json.dumps(data, ensure_ascii=False)}"
        )
        async with httpx.AsyncClient(
            timeout=self._settings.provider_timeout_seconds
        ) as client:
            for attempt in range(2):
                try:
                    response = await client.post(
                        f"{self._settings.provider_base_url}/chat/completions",
                        headers={
                            "Authorization": f"Bearer {self._settings.provider_api_key}"
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
