from enum import StrEnum
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, field_validator


class Intent(StrEnum):
    COUNT_USERS = "count_users"
    DISTRIBUTION = "distribution"
    SEARCH_USERS = "search_users"
    RECENT_USERS = "recent_users"
    USER_DETAIL = "user_detail"
    MATCHING_STATS = "matching_stats"


class QueryPlan(BaseModel):
    model_config = ConfigDict(extra="forbid")

    intent: Intent
    parameters: dict[str, Any] = Field(default_factory=dict)


class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    role: str
    content: str = Field(min_length=1, max_length=2000)

    @field_validator("role")
    @classmethod
    def role_is_supported(cls, value: str) -> str:
        if value not in {"user", "assistant", "system"}:
            raise ValueError("unsupported role")
        return value


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="forbid")

    messages: list[ChatMessage] = Field(min_length=1, max_length=30)
