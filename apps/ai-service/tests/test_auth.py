import base64
import hashlib
import hmac
import json
import time

from app.auth import ServicePrincipal, verify_token

SECRET = "test-secret-that-is-at-least-32-characters"


def _encode(value: dict) -> str:
    raw = json.dumps(value, separators=(",", ":")).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def _token(
    *,
    algorithm: str = "HS256",
    expires_at: int | None = None,
    secret: str = SECRET,
) -> str:
    header = _encode({"alg": algorithm, "typ": "JWT"})
    payload = _encode(
        {
            "username": "java-api",
            "role": "SERVICE",
            "aud": "sysudate-ai",
            "exp": expires_at or int(time.time()) + 60,
        }
    )
    signature = hmac.new(
        secret.encode(),
        f"{header}.{payload}".encode(),
        hashlib.sha256,
    ).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).decode().rstrip("=")
    return f"{header}.{payload}.{encoded_signature}"


def test_verify_token_accepts_valid_service_token() -> None:
    assert verify_token(_token(), SECRET) == ServicePrincipal(
        subject="java-api",
        role="SERVICE",
    )


def test_verify_token_rejects_expired_token() -> None:
    assert verify_token(_token(expires_at=int(time.time()) - 1), SECRET) is None


def test_verify_token_rejects_unexpected_algorithm() -> None:
    assert verify_token(_token(algorithm="none"), SECRET) is None


def test_verify_token_rejects_non_object_header() -> None:
    header = base64.urlsafe_b64encode(b"[]").decode().rstrip("=")
    payload = _encode(
        {
            "username": "java-api",
            "role": "SERVICE",
            "aud": "sysudate-ai",
            "exp": int(time.time()) + 60,
        }
    )
    signature = hmac.new(
        SECRET.encode(),
        f"{header}.{payload}".encode(),
        hashlib.sha256,
    ).digest()
    encoded_signature = base64.urlsafe_b64encode(signature).decode().rstrip("=")

    assert verify_token(f"{header}.{payload}.{encoded_signature}", SECRET) is None


def test_verify_token_rejects_wrong_signature() -> None:
    token = _token(secret="another-secret-that-is-at-least-32-chars")

    assert verify_token(token, SECRET) is None
