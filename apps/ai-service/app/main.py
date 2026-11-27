import time
import uuid

from fastapi import Depends, FastAPI, Header, HTTPException, Request, Response
from prometheus_client import CONTENT_TYPE_LATEST, Counter, Histogram, generate_latest

from app.auth import ServicePrincipal, require_service
from app.config import get_settings
from app.formatter import format_answer
from app.java_client import JavaQueryClient
from app.logging_config import access_log_record, access_logger
from app.models import ChatRequest
from app.planner import UnsafeRequest, plan
from app.provider import ProviderAdapter, is_provider_safe_plan

REQUESTS = Counter(
    "sysudate_ai_requests_total",
    "AI requests",
    ["status"],
)
LATENCY = Histogram(
    "sysudate_ai_request_duration_seconds",
    "AI request latency",
)
FALLBACKS = Counter(
    "sysudate_ai_fallback_total",
    "Deterministic formatter usage",
    ["reason"],
)

settings = get_settings()
queries = JavaQueryClient(settings)
provider = ProviderAdapter(settings)
app = FastAPI(title="SYSUDate AI Service", version="0.1.0")
SERVICE_AUTH = Depends(require_service)
ACCESS_LOGGER = access_logger()


@app.middleware("http")
async def request_context(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
    started = time.perf_counter()
    status = 500
    try:
        response = await call_next(request)
        status = response.status_code
        REQUESTS.labels(str(status)).inc()
    except Exception:
        REQUESTS.labels("500").inc()
        raise
    finally:
        duration = time.perf_counter() - started
        LATENCY.observe(duration)
        ACCESS_LOGGER.info(
            access_log_record(
                request_id=request_id,
                method=request.method,
                path=request.url.path,
                status=status,
                duration_ms=round(duration * 1000, 2),
            )
        )
    response.headers["X-Request-ID"] = request_id
    return response


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "UP"}


@app.get("/metrics")
async def metrics() -> Response:
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)


@app.post("/v1/chat")
async def chat(
    body: ChatRequest,
    request: Request,
    authorization: str = Header(),
    principal: ServicePrincipal = SERVICE_AUTH,
) -> dict:
    del principal
    user_messages = [
        message.content for message in body.messages if message.role == "user"
    ]
    if not user_messages:
        raise HTTPException(status_code=400, detail="Missing user message")
    try:
        query_plan = plan(user_messages[-1])
    except UnsafeRequest as exception:
        raise HTTPException(status_code=400, detail=str(exception)) from exception
    token = authorization.removeprefix("Bearer ").strip()
    result = await queries.query(
        query_plan,
        token,
        request.headers.get("X-Request-ID", str(uuid.uuid4())),
    )
    fallback = format_answer(result)
    if not provider.enabled:
        FALLBACKS.labels("provider_disabled").inc()
        answer = fallback
    elif not is_provider_safe_plan(query_plan):
        FALLBACKS.labels("privacy_policy").inc()
        answer = fallback
    else:
        answer = await provider.summarize(query_plan, result, fallback)
        if answer == fallback:
            FALLBACKS.labels("provider_error").inc()
    return {
        "choices": [
            {
                "index": 0,
                "message": {"role": "assistant", "content": answer},
                "finish_reason": "stop",
            }
        ],
        "usage": {"prompt_tokens": 0, "completion_tokens": 0},
        "query": {
            "intent": query_plan.intent.value,
            "parameters": query_plan.parameters,
        },
    }
