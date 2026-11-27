from typing import Any

import httpx

from app.config import Settings
from app.models import QueryPlan


class JavaQueryClient:
    def __init__(self, settings: Settings) -> None:
        self._base_url = settings.java_api_url
        self._timeout_seconds = settings.java_api_timeout_seconds

    async def query(
        self,
        query_plan: QueryPlan,
        token: str,
        request_id: str,
    ) -> dict[str, Any]:
        async with httpx.AsyncClient(timeout=self._timeout_seconds) as client:
            response = await client.post(
                f"{self._base_url}/internal/ai/query",
                headers={
                    "Authorization": f"Bearer {token}",
                    "X-Request-ID": request_id,
                },
                json={
                    "intent": query_plan.intent.value,
                    "parameters": query_plan.parameters,
                },
            )
            response.raise_for_status()
            return response.json()
