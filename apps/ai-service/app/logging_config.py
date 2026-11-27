import json
import logging


def access_logger() -> logging.Logger:
    logger = logging.getLogger("sysudate.ai.access")
    if not logger.handlers:
        handler = logging.StreamHandler()
        handler.setFormatter(logging.Formatter("%(message)s"))
        logger.addHandler(handler)
        logger.propagate = False
    logger.setLevel(logging.INFO)
    return logger


def access_log_record(
    *,
    request_id: str,
    method: str,
    path: str,
    status: int,
    duration_ms: float,
) -> str:
    return json.dumps(
        {
            "event": "http_request",
            "request_id": request_id,
            "method": method,
            "path": path,
            "status": status,
            "duration_ms": duration_ms,
        },
        ensure_ascii=False,
        separators=(",", ":"),
    )
