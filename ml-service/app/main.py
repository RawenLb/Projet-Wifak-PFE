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
  GET  /bf17/clusters       → tous les clusters avec statistiques
  GET  /bf17/stats          → statistiques globales
  POST /bf17/train          → ré-entraînement (background task)
  POST /train-all           → alias (compatibilité frontend)
══════════════════════════════════════════════════════════════════════
"""

import os
from contextlib import asynccontextmanager
from dotenv import load_dotenv
from fastapi import FastAPI, HTTPException, BackgroundTasks
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger
from apscheduler.schedulers.asyncio import AsyncIOScheduler

load_dotenv()

from app.database import diagnose_databases
from app.services.bf17_error_clustering import get_clustering_service
from app.schemas.schemas import (
    AnalyzeCommentRequest,
    ErrorAnalysisResponse,
    ClusterSummary,
    Bf17StatsResponse,
    TrainResponse,
    HealthResponse,
)

# ── Scheduler ──────────────────────────────────────────────────────────
scheduler = AsyncIOScheduler()


def _retrain_job():
    """Job planifié : ré-entraînement périodique BF17."""
    logger.info("🔄 [BF17] Ré-entraînement planifié démarré...")
    try:
        result = get_clustering_service().train()
        logger.info(f"✅ [BF17] Ré-entraînement terminé : {result}")
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
    description=(
        "Analyse un commentaire de rejet, identifie le cluster d'erreur "
        "et retourne les corrections déjà appliquées avec succès dans des cas similaires.\n\n"
        "Exemple de message retourné : "
        "\"Dans 80% des cas similaires, la correction appliquée a été : ajustement du champ X\""
    ),
)
def analyze_error(req: AnalyzeCommentRequest):
    """
    Point d'entrée principal BF17.

    Corps :
      - reject_comment        : commentaire de rejet à analyser (obligatoire)
      - declaration_type_code : code du type BCT (optionnel, pour enrichissement futur)
      - top_k                 : nombre de suggestions (1–10, défaut 5)
    """
    try:
        svc = get_clustering_service()

        if svc.vectorizer is None:
            raise HTTPException(
                status_code=503,
                detail=(
                    "Modèle BF17 non encore entraîné. "
                    "Vérifiez que validation_logs contient des rejets, "
                    "puis lancez POST /bf17/train."
                ),
            )

        result = svc.analyze(req.reject_comment, req.top_k)
        return result.to_dict()

    except HTTPException:
        raise
    except RuntimeError as e:
        raise HTTPException(status_code=503, detail=str(e))
    except Exception as e:
        logger.error(f"❌ [BF17] /bf17/analyze : {e}")
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
    background_tasks.add_task(get_clustering_service().train)
    return TrainResponse(
        status  = "training_started",
        details = {"models": ["TF-IDF+KMeans (BF17)"], "source": "real_data_only"},
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