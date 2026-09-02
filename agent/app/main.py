from contextlib import asynccontextmanager
import logging
import uuid

from fastapi import BackgroundTasks, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, PlainTextResponse

from app.errors import error_message, error_response, json_from_exception, json_from_http_exception
from app.rate_limit import SlidingWindowLimiter
from app.schemas import ChatRequest, ChatResponse, ChatThread, ChatThreadPreview
from app.service import run_chat
from config import CHATS_DIR, CONVERSATIONS_DIR, ROOT
from graph import compile_graph
from graph.model import has_server_api_key, mock_llm_enabled
from memory import delete_conversation
from memory.threads import delete_chat_thread, list_chat_threads, load_chat_thread, save_chat_thread
from metrics import prometheus_text
from observability import reset_request_id, set_request_id
from settings import get_settings

log = logging.getLogger("narrative")


@asynccontextmanager
async def lifespan(app: FastAPI):
    get_settings.cache_clear()
    settings = get_settings()
    app.state.graph = compile_graph()
    app.state.chat_limiter = SlidingWindowLimiter(settings.chat_rate_limit_per_minute)
    if mock_llm_enabled():
        log.warning("ENABLE_MOCK_LLM is on; chat replies are local mocks.")
    elif not has_server_api_key():
        log.warning(
            "OPENAI_API_KEY is missing or a placeholder. Chat needs a UI key, "
            "a real .env key, or ENABLE_MOCK_LLM=true."
        )
    yield


def _storage_ready() -> dict:
    checks: dict[str, str] = {}
    try:
        CHATS_DIR.mkdir(exist_ok=True)
        CONVERSATIONS_DIR.mkdir(exist_ok=True)
        probe = CHATS_DIR / ".ready"
        probe.write_text("ok", encoding="utf-8")
        probe.unlink(missing_ok=True)
        checks["chats"] = "ok"
        checks["conversations"] = "ok"
    except OSError as exc:
        checks["storage"] = str(exc)
        return {"status": "not_ready", "checks": checks}
    world = ROOT.parent / "World" / "Characters"
    checks["world_characters"] = "ok" if world.is_dir() else "missing"
    return {"status": "ok", "checks": checks}


def create_app() -> FastAPI:
    app = FastAPI(title="Narrative Engine Agent", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["http://localhost:4200"],
        allow_methods=["GET", "POST", "PUT", "DELETE", "OPTIONS"],
        allow_headers=["*"],
        expose_headers=["X-Request-ID"],
    )

    @app.middleware("http")
    async def request_id_middleware(request: Request, call_next):
        request_id = request.headers.get("x-request-id") or request.headers.get("X-Request-ID")
        if not request_id:
            request_id = str(uuid.uuid4())
        token = set_request_id(request_id)
        try:
            response = await call_next(request)
            response.headers["X-Request-ID"] = request_id
            return response
        finally:
            reset_request_id(token)

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, exc: HTTPException) -> JSONResponse:
        return json_from_http_exception(request, exc)

    @app.exception_handler(RequestValidationError)
    async def validation_error(request: Request, exc: RequestValidationError) -> JSONResponse:
        return error_response(request, 400, error_message(exc.errors()))

    @app.exception_handler(Exception)
    async def unhandled_error(request: Request, exc: Exception) -> JSONResponse:
        return json_from_exception(request, exc)

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/live")
    def liveness() -> dict[str, str]:
        return {"status": "ok"}

    @app.get("/health/ready")
    def readiness():
        payload = _storage_ready()
        if payload["status"] != "ok":
            return JSONResponse(status_code=503, content=payload)
        return payload

    @app.get("/metrics")
    def metrics() -> PlainTextResponse:
        return PlainTextResponse(prometheus_text(), media_type="text/plain; version=0.0.4")

    @app.get("/llm-config")
    def llm_config() -> dict[str, bool]:
        return {"has_server_api_key": has_server_api_key()}

    @app.get("/chat/config")
    def chat_config() -> dict[str, bool]:
        return {"has_server_api_key": has_server_api_key()}

    @app.get("/chat/threads", response_model=list[ChatThreadPreview])
    def chat_threads() -> list[dict]:
        return list_chat_threads()

    @app.get("/chat/threads/{character}", response_model=ChatThread)
    def get_chat_thread(character: str) -> dict:
        thread = load_chat_thread(character)
        if not thread:
            raise HTTPException(status_code=404, detail="No saved chat for that character.")
        return thread

    @app.put("/chat/threads/{character}", response_model=ChatThread)
    def put_chat_thread(character: str, thread: ChatThread) -> dict:
        name = character.strip()
        if not name:
            raise HTTPException(status_code=400, detail="Choose a character first.")
        payload = thread.model_dump()
        payload["character"] = name
        try:
            return save_chat_thread(name, payload)
        except ValueError:
            raise HTTPException(status_code=400, detail="Choose a character first.") from None

    @app.delete("/chat/threads/{character}")
    def remove_chat_thread(character: str) -> dict[str, str]:
        try:
            delete_chat_thread(character)
        except ValueError:
            pass
        return {"status": "deleted"}

    @app.post("/chat", response_model=ChatResponse)
    def chat(request: ChatRequest, background_tasks: BackgroundTasks, http_request: Request) -> ChatResponse:
        limiter: SlidingWindowLimiter = http_request.app.state.chat_limiter
        client = http_request.client.host if http_request.client else "anon"
        if not limiter.allow(client):
            raise HTTPException(
                status_code=429,
                detail="Too many replies at once. Wait a few seconds and try again.",
                headers={"Retry-After": "60"},
            )
        return run_chat(
            http_request.app.state.graph,
            request,
            background_tasks,
            openai_api_key=http_request.headers.get("x-openai-api-key"),
        )

    @app.delete("/conversations/{conversation_id}")
    def remove_conversation(conversation_id: str) -> dict[str, str]:
        try:
            delete_conversation(conversation_id)
        except ValueError:
            pass
        return {"status": "deleted"}

    return app
