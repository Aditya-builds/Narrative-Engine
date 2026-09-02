from contextlib import asynccontextmanager

from fastapi import BackgroundTasks, FastAPI, HTTPException, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse

from app.schemas import ChatRequest, ChatResponse
from app.service import run_chat
from graph import compile_graph
from memory import delete_conversation


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
        allow_headers=["*"],
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

    @app.post("/chat", response_model=ChatResponse)
    def chat(request: ChatRequest, background_tasks: BackgroundTasks) -> ChatResponse:
        return run_chat(app.state.graph, request, background_tasks)

    @app.delete("/conversations/{conversation_id}")
    def remove_conversation(conversation_id: str) -> dict[str, str]:
        try:
            delete_conversation(conversation_id)
        except ValueError:
            pass
        return {"status": "deleted"}

    return app
