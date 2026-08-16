import uuid
from datetime import datetime
from sqlalchemy import Column, String, DateTime, JSON, Float, ForeignKey
from sqlalchemy.dialects.postgresql import UUID
from sqlalchemy.orm import relationship
from app.db.session import Base
from app.config import settings

try:
    from pgvector.sqlalchemy import Vector
    HAS_PGVECTOR = True
except ImportError:
    HAS_PGVECTOR = False

class Place(Base):
    __tablename__ = "places"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    name = Column(String(255), nullable=False, index=True)
    wifi_ssid = Column(String(255), nullable=True, index=True)
    gps_lat = Column(Float, nullable=True)
    gps_lng = Column(Float, nullable=True)
    metadata_json = Column(JSON, nullable=True, default=dict)
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    # 1-to-many relationship for multi-angle scene vector embeddings
    embeddings = relationship("PlaceEmbedding", back_populates="place", cascade="all, delete-orphan")

class PlaceEmbedding(Base):
    __tablename__ = "place_embeddings"

    id = Column(UUID(as_uuid=True), primary_key=True, default=uuid.uuid4)
    place_id = Column(UUID(as_uuid=True), ForeignKey("places.id", ondelete="CASCADE"), nullable=False)
    angle_label = Column(String(50), nullable=True)
    
    if HAS_PGVECTOR:
        embedding = Column(Vector(settings.EMBEDDING_DIMENSION), nullable=False)
    else:
        embedding = Column(JSON, nullable=False)
        
    created_at = Column(DateTime, default=datetime.utcnow, nullable=False)

    place = relationship("Place", back_populates="embeddings")
