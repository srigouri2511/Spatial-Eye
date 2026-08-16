from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
from uuid import UUID
from datetime import datetime

class PlaceCreate(BaseModel):
    name: str = Field(..., description="Voice-spoken name of the saved place")
    embeddings: List[List[float]] = Field(..., description="List of 3-5 multi-angle 512-d scene feature vectors")
    wifi_ssid: Optional[str] = Field(None, description="Indoor Wi-Fi SSID tag for indoor filtering")
    gps_lat: Optional[float] = Field(None, description="Outdoor latitude")
    gps_lng: Optional[float] = Field(None, description="Outdoor longitude")
    metadata: Optional[Dict[str, Any]] = Field(default_factory=dict, description="Location metadata")

class PlaceRecognizeQuery(BaseModel):
    embedding: List[float] = Field(..., description="Candidate scene vector embedding from camera stream")
    wifi_ssid: Optional[str] = Field(None, description="Current connected Wi-Fi SSID")
    gps_lat: Optional[float] = Field(None, description="Current GPS latitude")
    gps_lng: Optional[float] = Field(None, description="Current GPS longitude")
    top_k: int = Field(default=1, ge=1, le=5)

class RecognizedPlace(BaseModel):
    id: UUID
    name: str
    confidence: float
    distance: float
    matched_angle: Optional[str] = None
    wifi_ssid: Optional[str] = None
    created_at: datetime
    metadata: Optional[Dict[str, Any]] = None

class PlaceResponse(BaseModel):
    id: UUID
    name: str
    embedding_count: int
    wifi_ssid: Optional[str] = None
    gps_lat: Optional[float] = None
    gps_lng: Optional[float] = None
    created_at: datetime
    metadata: Optional[Dict[str, Any]] = None

    class Config:
        from_attributes = True
