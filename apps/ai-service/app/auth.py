import base64
import hashlib
import hmac
import json
import time
from dataclasses import dataclass

from fastapi import Header, HTTPException

from app.config import get_settings


@dataclass(frozen=True)
class ServicePrincipal:
    subject: str
    role: str


def require_service(
    authorization: str | None = Header(default=None),
) -> ServicePrincipal:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status_code=401, detail="Missing service token")
    principal = verify_token(
        authorization.removeprefix("Bearer ").strip(),
        get_settings().service_jwt_secret,
    )
    if principal is None or principal.role != "SERVICE":
        raise HTTPException(status_code=403, detail="Invalid service token")
    return principal


def verify_token(token: str, secret: str) -> ServicePrincipal | None:
    try:
        header, payload, signature = token.split(".")
        header_data = json.loads(_decode(header))
        if not isinstance(header_data, dict):
            return None
        if header_data.get("alg") != "HS256":
            return None
        expected = hmac.new(
            secret.encode(),
            f"{header}.{payload}".encode(),
            hashlib.sha256,
        ).digest()
        if not hmac.compare_digest(expected, _decode(signature)):
            return None
        claims = json.loads(_decode(payload))
        if int(claims["exp"]) <= int(time.time()):
            return None
        if claims.get("aud") != "sysudate-ai":
            return None
        return ServicePrincipal(
            subject=str(claims["username"]),
            role=str(claims["role"]),
        )
    except (KeyError, TypeError, ValueError, json.JSONDecodeError):
        return None


def _decode(value: str) -> bytes:
    return base64.urlsafe_b64decode(value + "=" * (-len(value) % 4))
