from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.config import settings
from app.api.places import router as places_router
from app.db.session import engine, Base

app = FastAPI(
    title=settings.PROJECT_NAME,
    version=settings.VERSION,
    openapi_url=f"{settings.API_V1_STR}/openapi.json"
)

# Enable CORS for Flutter mobile client & Web preview
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
async def startup():
    try:
        async with engine.begin() as conn:
            try:
                await conn.execute("CREATE EXTENSION IF NOT EXISTS vector;")
            except Exception:
                pass
            await conn.run_sync(Base.metadata.create_all)
        print("[SUCCESS] Database tables initialized successfully.")
    except Exception as e:
        print(f"[NOTICE] PostgreSQL not connected. Starting API in fallback mode.")



@app.get("/")
async def root():
    return {
        "service": "Spatial Eye Backend API",
        "status": "online",
        "version": settings.VERSION,
        "docs": "/docs"
    }

@app.get("/health")
async def health_check():
    return {"status": "healthy"}

app.include_router(places_router, prefix=settings.API_V1_STR)
