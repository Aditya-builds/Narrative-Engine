import config  # noqa: F401  loads .env

from app.main import create_app

app = create_app()
