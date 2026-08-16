import math
import numpy as np
from typing import List
from uuid import UUID
from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.future import select
from sqlalchemy.orm import selectinload

from app.db.session import get_db
from app.models.place import Place, PlaceEmbedding
from app.schemas.place import PlaceCreate, PlaceResponse, PlaceRecognizeQuery, RecognizedPlace
from app.config import settings

router = APIRouter(prefix="/places", tags=["places"])

def cosine_similarity(vec1: List[float], vec2: List[float]) -> tuple[float, float]:
    """Computes cosine distance and confidence score between two vectors."""
    a = np.array(vec1, dtype=np.float32)
    b = np.array(vec2, dtype=np.float32)
    norm_a = np.linalg.norm(a)
    norm_b = np.linalg.norm(b)
    if norm_a == 0 or norm_b == 0:
        return 1.0, 0.0
    
    dot = np.dot(a, b)
    cos_sim = float(dot / (norm_a * norm_b))
    cos_sim = max(-1.0, min(1.0, cos_sim))
    distance = 1.0 - cos_sim
    confidence = (cos_sim + 1.0) / 2.0
    return distance, confidence

def haversine_distance_meters(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    """Calculates outdoor GPS distance in meters between two lat/lng coordinates."""
    R = 6371000.0  # Earth radius in meters
    phi1, phi2 = math.radians(lat1), math.radians(lat2)
    dphi = math.radians(lat2 - lat1)
    dlambda = math.radians(lon2 - lon1)
    a = math.sin(dphi / 2)**2 + math.cos(phi1) * math.cos(phi2) * math.sin(dlambda / 2)**2
    c = 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))
    return R * c

@router.post("/save", response_model=PlaceResponse, status_code=status.HTTP_201_CREATED)
async def save_place(place_in: PlaceCreate, db: AsyncSession = Depends(get_db)):
    """Saves location with multi-angle embeddings (3-5 vectors) and Wi-Fi/GPS geofence tags."""
    if not place_in.embeddings or len(place_in.embeddings) == 0:
        raise HTTPException(status_code=400, detail="At least one embedding vector is required")
        
    for emb in place_in.embeddings:
        if len(emb) != settings.EMBEDDING_DIMENSION:
            raise HTTPException(
                status_code=400,
                detail=f"Each embedding vector must have dimension {settings.EMBEDDING_DIMENSION}"
            )
    
    new_place = Place(
        name=place_in.name,
        wifi_ssid=place_in.wifi_ssid,
        gps_lat=place_in.gps_lat,
        gps_lng=place_in.gps_lng,
        metadata_json=place_in.metadata or {}
    )
    db.add(new_place)
    await db.flush()

    # Save each multi-angle vector
    for idx, emb in enumerate(place_in.embeddings):
        angle_label = f"angle_{idx + 1}"
        place_emb = PlaceEmbedding(
            place_id=new_place.id,
            angle_label=angle_label,
            embedding=emb
        )
        db.add(place_emb)

    await db.commit()
    await db.refresh(new_place)

    return PlaceResponse(
        id=new_place.id,
        name=new_place.name,
        embedding_count=len(place_in.embeddings),
        wifi_ssid=new_place.wifi_ssid,
        gps_lat=new_place.gps_lat,
        gps_lng=new_place.gps_lng,
        created_at=new_place.created_at,
        metadata=new_place.metadata_json
    )

@router.post("/recognize", response_model=List[RecognizedPlace])
async def recognize_place(query: PlaceRecognizeQuery, db: AsyncSession = Depends(get_db)):
    """Matches candidate scene embedding against saved multi-angle vectors with dual Wi-Fi/GPS geofencing."""
    if len(query.embedding) != settings.EMBEDDING_DIMENSION:
        raise HTTPException(
            status_code=400,
            detail=f"Query embedding dimension must be {settings.EMBEDDING_DIMENSION}"
        )
    
    stmt = select(Place).options(selectinload(Place.embeddings))
    
    # 1. Wi-Fi SSID indoor filtering
    if query.wifi_ssid:
        stmt = stmt.where((Place.wifi_ssid == query.wifi_ssid) | (Place.wifi_ssid == None))

    result = await db.execute(stmt)
    places = result.scalars().all()

    if not places:
        return []

    recognized = []
    for p in places:
        # 2. GPS Outdoor Filtering (filter out places > 500m away if GPS available)
        if query.gps_lat is not None and query.gps_lng is not None and p.gps_lat is not None and p.gps_lng is not None:
            dist_m = haversine_distance_meters(query.gps_lat, query.gps_lng, p.gps_lat, p.gps_lng)
            if dist_m > 500.0:
                continue

        # Match candidate against nearest of multi-angle set
        best_dist = 999.0
        best_conf = 0.0
        best_angle = None

        for pe in p.embeddings:
            emb_list = list(pe.embedding) if hasattr(pe.embedding, '__iter__') else pe.embedding
            dist, conf = cosine_similarity(query.embedding, emb_list)
            if dist < best_dist:
                best_dist = dist
                best_conf = conf
                best_angle = pe.angle_label

        if best_dist <= settings.VECTOR_SIMILARITY_THRESHOLD:
            recognized.append(
                RecognizedPlace(
                    id=p.id,
                    name=p.name,
                    confidence=round(best_conf, 4),
                    distance=round(best_dist, 4),
                    matched_angle=best_angle,
                    wifi_ssid=p.wifi_ssid,
                    created_at=p.created_at,
                    metadata=p.metadata_json
                )
            )

    recognized.sort(key=lambda x: x.distance)
    return recognized[:query.top_k]

@router.get("", response_model=List[PlaceResponse])
async def list_places(db: AsyncSession = Depends(get_db)):
    stmt = select(Place).options(selectinload(Place.embeddings)).order_by(Place.created_at.desc())
    result = await db.execute(stmt)
    places = result.scalars().all()
    
    return [
        PlaceResponse(
            id=p.id,
            name=p.name,
            embedding_count=len(p.embeddings),
            wifi_ssid=p.wifi_ssid,
            gps_lat=p.gps_lat,
            gps_lng=p.gps_lng,
            created_at=p.created_at,
            metadata=p.metadata_json
        )
        for p in places
    ]
