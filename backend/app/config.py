import os
from pydantic_settings import BaseSettings

class Settings(BaseSettings):
    PROJECT_NAME: str = "Spatial Eye API"
    VERSION: str = "1.0.0"
    API_V1_STR: str = "/api/v1"
    
    # Database connection string (PostgreSQL with pgvector)
    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        "postgresql+asyncpg://spatial:eye_secret@localhost:5432/spatial_eye_db"
    )
    
    # Vector matching threshold (Cosine distance <= threshold considered match)
    VECTOR_SIMILARITY_THRESHOLD: float = 0.35
    EMBEDDING_DIMENSION: int = 512

    class Config:
        case_sensitive = True

settings = Settings()
