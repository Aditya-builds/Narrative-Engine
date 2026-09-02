from fastapi.testclient import TestClient

from app.main import create_app
from settings import get_settings


def test_health_and_request_id():
    get_settings.cache_clear()
    app = create_app()
    with TestClient(app) as client:
        response = client.get("/health", headers={"X-Request-ID": "req-health-1"})
        assert response.status_code == 200
        assert response.json() == {"status": "ok"}
        assert response.headers.get("x-request-id") == "req-health-1"

        live = client.get("/health/live")
        assert live.status_code == 200
        assert live.headers.get("x-request-id")

        ready = client.get("/health/ready")
        assert ready.status_code == 200
        assert ready.json()["status"] == "ok"

        metrics = client.get("/metrics")
        assert metrics.status_code == 200
        assert "narrative_chat_requests_total" in metrics.text


def test_missing_thread_uses_error_contract():
    get_settings.cache_clear()
    app = create_app()
    with TestClient(app) as client:
        response = client.get("/chat/threads/Nobody")
        assert response.status_code == 404
        body = response.json()
        assert body["errorCode"] == "NOT_FOUND"
        assert body["status"] == 404
        assert body["detail"] == "No saved chat for that character."
        assert body["error"] == body["detail"]
        assert body["message"] == body["detail"]
        assert body["path"] == "/chat/threads/Nobody"
        assert "timestamp" in body
