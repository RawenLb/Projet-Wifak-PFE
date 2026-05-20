"""
schemas.py — Modèles Pydantic BF17
══════════════════════════════════════════════════════════════════════
Validation des entrées/sorties de l'API FastAPI.
══════════════════════════════════════════════════════════════════════
"""
from __future__ import annotations

from pydantic import BaseModel, Field, field_validator
from typing import Optional, List


# ══════════════════════════════════════════════════════════════════════
# REQUÊTES
# ══════════════════════════════════════════════════════════════════════

class AnalyzeCommentRequest(BaseModel):
    """Corps de la requête POST /bf17/analyze."""
    reject_comment:        str = Field(
        ...,
        min_length=5,
        description="Commentaire de rejet BCT à analyser",
        example="Le montant brut est négatif pour plusieurs lignes de la classe 3.",
    )
    declaration_type_code: Optional[str] = Field(
        None,
        description="Code du type de déclaration (ex: BCT_01) — optionnel",
        example="BCT_05",
    )
    top_k: int = Field(
        5,
        ge=1,
        le=10,
        description="Nombre de suggestions à retourner (1–10)",
    )

    @field_validator("reject_comment")
    @classmethod
    def strip_comment(cls, v: str) -> str:
        return v.strip()

    model_config = {
        "json_schema_extra": {
            "example": {
                "reject_comment":        "Le montant brut est négatif pour plusieurs lignes de la classe 2.",
                "declaration_type_code": "BCT_05",
                "top_k":                 5,
            }
        }
    }


# ══════════════════════════════════════════════════════════════════════
# SORTIES
# ══════════════════════════════════════════════════════════════════════

class SuggestionItem(BaseModel):
    """Une suggestion de correction issue de l'historique réel."""
    rank:               int
    similarity_score:   float = Field(..., ge=0.0, le=1.0)
    reject_comment:     str
    correction_applied: str
    corrected_by:       str
    delay_hours:        Optional[int]
    was_validated:      bool
    cluster_label:      str


class ErrorAnalysisResponse(BaseModel):
    """Réponse complète de l'analyse BF17."""
    reject_comment:      str
    cluster_id:          int
    cluster_label:       str
    cluster_keywords:    List[str]
    similar_cases_count: int
    suggestions:         List[SuggestionItem]
    success_rate:        float = Field(..., ge=0.0, le=1.0)
    avg_delay_hours:     Optional[float]
    message:             str


class ClusterSummary(BaseModel):
    """Résumé d'un cluster pour le tableau de bord."""
    cluster_id:      int
    label:           str
    keywords:        List[str]
    count:           int
    success_rate:    float
    avg_delay_hours: Optional[float]
    example:         str


class Bf17StatsResponse(BaseModel):
    """Statistiques globales BF17."""
    total_reject_comments:   int
    n_clusters:              int
    correction_history_size: int
    validated_corrections:   int
    global_resolution_rate:  float
    cluster_labels:          dict
    trained_at:              str
    silhouette_score:        float


# ══════════════════════════════════════════════════════════════════════
# TRAIN / HEALTH
# ══════════════════════════════════════════════════════════════════════

class TrainResponse(BaseModel):
    """Réponse au lancement d'un entraînement."""
    status:  str
    details: dict = {}


class HealthResponse(BaseModel):
    """Réponse au endpoint /health."""
    status:  str
    bf17:    str
    version: str = "4.0.0"