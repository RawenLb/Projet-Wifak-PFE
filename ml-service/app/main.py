"""
main.py — ML Service FastAPI — BF17
══════════════════════════════════════════════════════════════════════
Banque Wifak — Plateforme de gestion des déclarations BCT

BF17 : Analyse intelligente des erreurs + aide à la correction
        basée sur l'historique interne réel

Endpoints exposés :
  GET  /health
  GET  /diagnostics
  POST /bf17/analyze        → analyse un commentaire de rejet
  POST /bf17/analyze-comment → alias frontend
  GET  /bf17/clusters       → tous les clusters avec statistiques
  GET  /bf17/stats          → statistiques globales
  POST /bf17/train          → ré-entraînement (background task)
  POST /train-all           → alias (compatibilité frontend)
  POST /bf17/analyze-content → analyse le contenu XML/CSV d'une déclaration (Z-Score)
══════════════════════════════════════════════════════════════════════
"""

import os
import json
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional
from loguru import logger
from apscheduler.schedulers.asyncio import AsyncIOScheduler

load_dotenv()

from app.database import diagnose_databases
from app.services.bf17_error_clustering import get_clustering_service
from app.services.xml_feature_extractor import (
    extract_features_from_xml,
    build_reference_stats,
    compute_zscore_alerts,
)
from app.schemas.schemas import (
    AnalyzeCommentRequest,
    ErrorAnalysisResponse,
    ClusterSummary,
    Bf17StatsResponse,
    TrainResponse,
    HealthResponse,
)

# ── Référence stats (chargées au démarrage, rechargées après train) ────
_ref_stats: dict = {}
_REF_STATS_PATH = "models/saved/bf15_ref_stats.json"

# ── Scheduler ──────────────────────────────────────────────────────────
scheduler = AsyncIOScheduler()


def _load_ref_stats() -> dict:
    """Charge les stats de référence depuis le fichier persisté."""
    global _ref_stats
    try:
        import os
        if os.path.exists(_REF_STATS_PATH):
            with open(_REF_STATS_PATH, "r", encoding="utf-8") as f:
                _ref_stats = json.load(f)
            logger.info(f"✅ [XML] Stats de référence chargées — {len(_ref_stats)} groupes")
        else:
            logger.info("ℹ️ [XML] Pas de stats de référence persistées — seront calculées au 1er train")
    except Exception as e:
        logger.warning(f"⚠️ [XML] Impossible de charger les stats de référence : {e}")
    return _ref_stats


def _rebuild_ref_stats():
    """Reconstruit les stats de référence depuis la BD après un ré-entraînement."""
    global _ref_stats
    try:
        from app.database import get_bct_engine
        import pandas as pd
        from sqlalchemy import text

        engine = get_bct_engine()
        with engine.connect() as conn:
            df = pd.read_sql(text("""
                SELECT
                    dt.code        AS type_code,
                    dt.format      AS file_format,
                    d.contenu_fichier,
                    TIMESTAMPDIFF(HOUR, d.date_generation, d.date_validation) AS validation_delay_hours
                FROM declarations d
                JOIN declaration_types dt ON d.declaration_type_id = dt.id
                WHERE d.statut IN ('VALIDEE', 'ENVOYEE')
                  AND d.contenu_fichier IS NOT NULL
                  AND LENGTH(d.contenu_fichier) > 10
                LIMIT 2000
            """), conn)

        if not df.empty:
            _ref_stats = build_reference_stats(df)
            # Persister
            import os
            os.makedirs(os.path.dirname(_REF_STATS_PATH), exist_ok=True)
            with open(_REF_STATS_PATH, "w", encoding="utf-8") as f:
                json.dump(_ref_stats, f, ensure_ascii=False, indent=2)
            logger.info(f"✅ [XML] Stats de référence reconstruites — {len(_ref_stats)} groupes")
        else:
            logger.info("ℹ️ [XML] Aucune déclaration validée pour les stats de référence")
    except Exception as e:
        logger.warning(f"⚠️ [XML] Reconstruction stats de référence échouée : {e}")


def _retrain_job():
    """Job planifié : ré-entraînement périodique BF17 + rebuild ref stats."""
    logger.info("🔄 [BF17] Ré-entraînement planifié démarré...")
    try:
        result = get_clustering_service().train()
        logger.info(f"✅ [BF17] Ré-entraînement terminé : {result}")
        _rebuild_ref_stats()
    except Exception as e:
        logger.error(f"❌ [BF17] Erreur ré-entraînement planifié : {e}")


# ── Cycle de vie de l'application ──────────────────────────────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("🚀 [BF17] ML Service Banque Wifak — Démarrage...")

    # Diagnostic BD
    try:
        db_report = diagnose_databases()
        if db_report["ok"]:
            logger.info("✅ Connexions BD OK")
        else:
            logger.warning("⚠️ Une ou plusieurs BD inaccessibles — BF17 sera limité")
    except Exception as e:
        logger.error(f"❌ Diagnostic BD échoué : {e}")

    # Chargement/entraînement initial
    get_clustering_service()

    # Charger les stats de référence XML (pour Z-Score)
    _load_ref_stats()

    # Ré-entraînement périodique automatique
    interval_h = int(os.getenv("RETRAIN_INTERVAL_HOURS", "24"))
    scheduler.add_job(_retrain_job, "interval", hours=interval_h, id="retrain_bf17")
    scheduler.start()
    logger.info(f"⏰ Ré-entraînement automatique configuré toutes les {interval_h}h")

    yield

    # Shutdown
    scheduler.shutdown(wait=False)
    logger.info("👋 ML Service arrêté")


# ── Application ────────────────────────────────────────────────────────
app = FastAPI(
    title       = "ML Service — Plateforme BCT Banque Wifak",
    description = (
        "**BF17** — Analyse intelligente des erreurs de déclaration BCT.\n\n"
        "Clustering TF-IDF + KMeans + Cosine Similarity.\n"
        "Suggestions basées uniquement sur l'historique réel validé."
    ),
    version     = "4.0.0",
    lifespan    = lifespan,
    docs_url    = "/docs",
    redoc_url   = "/redoc",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins  = ["*"],
    allow_methods  = ["*"],
    allow_headers  = ["*"],
)

# ── Prometheus metrics ─────────────────────────────────────────────────
from prometheus_fastapi_instrumentator import Instrumentator
Instrumentator().instrument(app).expose(app, endpoint="/metrics")


# ══════════════════════════════════════════════════════════════════════
# HEALTH & DIAGNOSTICS
# ══════════════════════════════════════════════════════════════════════

@app.get(
    "/health",
    response_model=HealthResponse,
    tags=["Système"],
    summary="Vérification de l'état du service",
)
def health():
    svc = get_clustering_service()
    bf17_status = (
        f"OK — {svc.kmeans.n_clusters} clusters | "
        f"{len(svc.corpus)} docs | "
        f"{len(svc.correction_history)} corrections"
        if svc.vectorizer
        else "NOT_TRAINED"
    )
    return HealthResponse(status="UP", bf17=bf17_status)


@app.get(
    "/diagnostics",
    tags=["Système"],
    summary="Diagnostic complet BD + modèle",
)
def diagnostics():
    try:
        db_report = diagnose_databases()
        svc       = get_clustering_service()
        return {
            "databases": db_report,
            "bf17": {
                "trained":         svc.vectorizer is not None,
                "corpus_size":     len(svc.corpus),
                "history_size":    len(svc.correction_history),
                "n_clusters":      svc.kmeans.n_clusters if svc.kmeans else 0,
                "cluster_labels":  svc.cluster_labels,
                "trained_at":      svc._train_metadata.get("trained_at", ""),
                "silhouette":      svc._train_metadata.get("silhouette", 0.0),
            },
        }
    except Exception as e:
        logger.error(f"❌ /diagnostics : {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ══════════════════════════════════════════════════════════════════════
# BF17 — ANALYSE D'UN COMMENTAIRE DE REJET
# ══════════════════════════════════════════════════════════════════════

@app.post(
    "/bf17/analyze",
    tags=["BF17 — Clustering Erreurs"],
    summary="Analyser un commentaire de rejet BCT",
)
def analyze_error(req: AnalyzeCommentRequest):
    try:
        svc = get_clustering_service()
        if svc.vectorizer is None:
            raise HTTPException(status_code=503, detail="Modèle BF17 non encore entraîné.")
        result = svc.analyze(req.reject_comment, req.top_k)
        return result.to_dict()
    except HTTPException:
        raise
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        logger.error(f"❌ [BF17] /bf17/analyze : {e}")
        raise HTTPException(status_code=500, detail=str(e))


@app.post(
    "/bf17/analyze-comment",
    tags=["BF17 — Clustering Erreurs"],
    summary="Alias /bf17/analyze (compatibilité frontend)",
)
def analyze_comment(req: AnalyzeCommentRequest):
    """Alias vers /bf17/analyze pour compatibilité avec le frontend Angular."""
    return analyze_error(req)


@app.get(
    "/bf17/declaration/{declaration_id}/suggestions",
    tags=["BF17 — Clustering Erreurs"],
    summary="Suggestions pour une déclaration rejetée",
)
def get_suggestions_for_declaration(declaration_id: int, top_k: int = 5):
    """
    Récupère le commentaire de rejet d'une déclaration depuis la BD
    et retourne les suggestions de correction BF17.
    """
    try:
        from app.database import get_reject_comment_for_declaration
        comment = get_reject_comment_for_declaration(declaration_id)
        if not comment:
            raise HTTPException(
                status_code=404,
                detail=f"Aucun commentaire de rejet trouvé pour la déclaration {declaration_id}"
            )
        svc = get_clustering_service()
        if svc.vectorizer is None:
            raise HTTPException(status_code=503, detail="Modèle BF17 non encore entraîné.")
        result = svc.analyze(comment, top_k)
        response = result.to_dict()
        response["declaration_id"] = declaration_id
        return response
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"❌ [BF17] /bf17/declaration/{declaration_id}/suggestions : {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ══════════════════════════════════════════════════════════════════════
# BF17 — CLUSTERS
# ══════════════════════════════════════════════════════════════════════

@app.get(
    "/bf17/clusters",
    tags=["BF17 — Clustering Erreurs"],
    summary="Tous les clusters d'erreurs avec statistiques",
)
def get_clusters():
    """
    Retourne la liste de tous les clusters identifiés, triés par fréquence.
    Utilisé par le tableau de bord manager.
    """
    try:
        return get_clustering_service().get_all_clusters()
    except Exception as e:
        logger.error(f"❌ [BF17] /bf17/clusters : {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ══════════════════════════════════════════════════════════════════════
# BF17 — STATISTIQUES GLOBALES
# ══════════════════════════════════════════════════════════════════════

@app.get(
    "/bf17/stats",
    tags=["BF17 — Clustering Erreurs"],
    summary="Statistiques globales BF17",
)
def bf17_stats():
    """
    Retourne les statistiques globales :
      - Nombre de commentaires analysés
      - Nombre de clusters
      - Taille de l'historique corrections
      - Taux de résolution global
      - Date du dernier entraînement
    """
    try:
        return get_clustering_service().get_stats()
    except Exception as e:
        logger.error(f"❌ [BF17] /bf17/stats : {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ══════════════════════════════════════════════════════════════════════
# BF17 — ENTRAÎNEMENT MANUEL
# ══════════════════════════════════════════════════════════════════════

@app.post(
    "/bf17/train",
    response_model=TrainResponse,
    tags=["BF17 — Clustering Erreurs"],
    summary="Lancer le ré-entraînement BF17 (background)",
)
def train_clustering(background_tasks: BackgroundTasks):
    """
    Lance le ré-entraînement en arrière-plan sur les données fraîches.
    Retourne immédiatement — le résultat sera visible dans /bf17/stats.
    """
    background_tasks.add_task(get_clustering_service().train)
    return TrainResponse(
        status  = "training_started",
        details = {"source": "real_data_only", "note": "Résultat dans /bf17/stats"},
    )


# ══════════════════════════════════════════════════════════════════════
# XML CONTENT ANALYSIS — Z-Score via xml_feature_extractor
# ══════════════════════════════════════════════════════════════════════

class ContentAnalyzeRequest(BaseModel):
    """Corps de la requête POST /bf17/analyze-content."""
    content:     str
    type_code:   str
    file_format: Optional[str] = "XML"


@app.post(
    "/bf17/analyze-content",
    tags=["BF17 — Clustering Erreurs"],
    summary="Analyse Z-Score du contenu XML/CSV d'une déclaration",
)
def analyze_content(req: ContentAnalyzeRequest):
    """
    Extrait les features numériques du contenu d'une déclaration
    et calcule les alertes Z-Score par rapport aux déclarations historiques
    du même type et format.

    Utilisé pour détecter les anomalies statistiques AVANT soumission.
    """
    try:
        if not req.content or not req.content.strip():
            raise HTTPException(status_code=400, detail="Le contenu est vide")

        # Extraction des features
        features = extract_features_from_xml(req.content, req.type_code)

        # Calcul des alertes Z-Score
        alerts = compute_zscore_alerts(
            features    = features,
            ref_stats   = _ref_stats,
            type_code   = req.type_code,
            file_format = req.file_format or "XML",
            threshold   = 3.0,
        )

        return {
            "type_code":    req.type_code,
            "file_format":  req.file_format,
            "features":     features,
            "alerts":       alerts,
            "alert_count":  len(alerts),
            "has_critical": any(a["severity"] == "critique" for a in alerts),
            "ref_available": bool(_ref_stats.get(f"{req.type_code}__{req.file_format}")),
        }

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"❌ [XML] /bf17/analyze-content : {e}")
        raise HTTPException(status_code=500, detail=str(e))


# ══════════════════════════════════════════════════════════════════════
# GLOBAL — Alias frontend
# ══════════════════════════════════════════════════════════════════════

@app.post(
    "/train-all",
    response_model=TrainResponse,
    tags=["Système"],
    summary="Alias /bf17/train (compatibilité frontend)",
)
def train_all(background_tasks: BackgroundTasks):
    """Alias vers /bf17/train pour compatibilité avec le bouton global du dashboard."""
    def _train_and_rebuild():
        get_clustering_service().train()
        _rebuild_ref_stats()

    background_tasks.add_task(_train_and_rebuild)
    return TrainResponse(
        status  = "training_started",
        details = {"models": ["TF-IDF+KMeans (BF17)", "XML Z-Score ref stats"], "source": "real_data_only"},
    )


# ══════════════════════════════════════════════════════════════════════
# POINT D'ENTRÉE
# ══════════════════════════════════════════════════════════════════════

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "app.main:app",
        host    = os.getenv("ML_SERVICE_HOST", "0.0.0.0"),
        port    = int(os.getenv("ML_SERVICE_PORT", "8090")),
        reload  = True,
        workers = 1,
    )