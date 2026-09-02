from contextlib import asynccontextmanager

from fastapi import BackgroundTasks, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.schemas import ChatRequest, ChatResponse, ChatThread, ChatThreadPreview
from app.service import run_chat
from graph import compile_graph
from graph.model import has_server_api_key
from memory import delete_conversation
from memory.threads import delete_chat_thread, list_chat_threads, load_chat_thread, save_chat_thread


@asynccontextmanager
async def lifespan(app: FastAPI):
    app.state.graph = compile_graph()
    yield


def create_app() -> FastAPI:
    app = FastAPI(title="Narrative Engine Agent", lifespan=lifespan)
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["http://localhost:4200"],
        allow_methods=["*"],
        allow_headers=["*", "X-OpenAI-Api-Key"],
    )

    @app.exception_handler(Exception)
    async def unhandled_error(_request: Request, exc: Exception) -> JSONResponse:
        if isinstance(exc, HTTPException):
            return JSONResponse(status_code=exc.status_code, content={"detail": exc.detail})
        return JSONResponse(
            status_code=503,
            content={"detail": "Something went wrong. Try again in a moment."},
        )

    @app.get("/health")
    def health() -> dict[str, str]:
        return {"status": "ok"}

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
        return run_chat(
            app.state.graph,
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
