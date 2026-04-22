"""
train_models.py — Script d'entraînement BF17
══════════════════════════════════════════════════════════════════════
Usage :
  python scripts/train_models.py             # Entraîner BF17
  python scripts/train_models.py --diagnose  # Diagnostic BD uniquement
  python scripts/train_models.py --test      # Entraînement + tests
══════════════════════════════════════════════════════════════════════
"""

import sys
import argparse
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from dotenv import load_dotenv
load_dotenv()

from loguru import logger

logger.remove()
logger.add(
    sys.stderr,
    format="<green>{time:HH:mm:ss}</green> | <level>{level:<8}</level> | {message}",
    level="INFO",
    colorize=True,
)


# ══════════════════════════════════════════════════════════════════════
# DIAGNOSTIC BD
# ══════════════════════════════════════════════════════════════════════

def run_diagnostics():
    logger.info("═" * 65)
    logger.info("  DIAGNOSTIC — Connexions BD")
    logger.info("═" * 65)

    from app.database import diagnose_databases
    report = diagnose_databases()

    if report["bct"].get("connected"):
        logger.success("  ✅ wifak_PFE       connectée")
    else:
        logger.error(f"  ❌ wifak_PFE       : {report['bct'].get('error', '?')}")

    if report["validation"].get("connected"):
        count   = report["validation"].get("validation_logs_count", "?")
        actions = report["validation"].get("actions", {})
        logger.success(f"  ✅ wifak_validation connectée")
        logger.info(f"     validation_logs : {count} lignes")
        logger.info(f"     Actions         : {actions}")

        # Vérifier qu'il y a des REJECT
        nb_reject = actions.get("REJECT", 0)
        if nb_reject == 0:
            logger.warning(
                "  ⚠️  Aucun REJECT dans validation_logs — "
                "BF17 ne pourra pas s'entraîner. "
                "Les données apparaîtront après les premières validations."
            )
        else:
            logger.info(f"  ✅ {nb_reject} rejet(s) disponibles pour l'entraînement")
    else:
        logger.error(f"  ❌ wifak_validation : {report['validation'].get('error', '?')}")

    return report


# ══════════════════════════════════════════════════════════════════════
# ENTRAÎNEMENT BF17
# ══════════════════════════════════════════════════════════════════════

def train_bf17(run_tests: bool = False):
    logger.info("\n" + "═" * 65)
    logger.info("  BF17 — Clustering erreurs (TF-IDF + KMeans + Cosine Sim)")
    logger.info("═" * 65)

    from app.database import load_reject_logs_df, load_correction_history_df
    from app.services.bf17_error_clustering import ErrorClusteringService

    reject_df  = load_reject_logs_df()
    history_df = load_correction_history_df()

    if len(reject_df) < 3:
        logger.error(
            f"  ❌ Seulement {len(reject_df)} commentaire(s) de rejet — "
            f"minimum 3 requis pour l'entraînement."
        )
        logger.info(
            "  💡 Le modèle s'entraînera automatiquement dès que "
            "des déclarations auront été rejetées avec un commentaire."
        )
        return {"status": "insufficient_data"}

    logger.success(f"  ✅ {len(reject_df)} commentaires de rejet réels")
    logger.info(f"  📋 {len(history_df)} corrections historiques")

    svc    = ErrorClusteringService.__new__(ErrorClusteringService)
    svc.vectorizer         = None
    svc.kmeans             = None
    svc.tfidf_matrix       = None
    svc.corpus             = []
    svc.correction_history = []
    svc.cluster_labels     = {}
    svc.cluster_keywords   = {}
    svc._train_metadata    = {}

    result = svc.train(reject_df, history_df)

    if result.get("status") != "trained":
        logger.error(f"  ❌ Entraînement échoué : {result}")
        return result

    # Affichage des clusters
    n_clusters  = result.get("clusters", 0)
    silhouette  = result.get("silhouette", 0.0)
    corrections = result.get("corrections", 0)

    logger.success(
        f"  ✅ {n_clusters} clusters | "
        f"silhouette={silhouette:.3f} | "
        f"{corrections} corrections réelles"
    )

    clusters = svc.get_all_clusters()
    logger.info("\n  📊 Clusters identifiés :")
    for c in clusters:
        rate_str = f"{min(c['success_rate'],1.0):.0%}" if c['success_rate'] > 0 else "—"
        delay_str = f"{c['avg_delay_hours']:.0f}h" if c.get('avg_delay_hours') else "—"
        logger.info(
            f"     [{c['cluster_id']}] {c['label']:50s} "
            f"| {c['count']:3d} cas "
            f"| {rate_str:6} résolus "
            f"| délai moy. {delay_str}"
        )

    # ── Tests sur commentaires réels ────────────────────────────────
    if run_tests:
        logger.info("\n  🔍 Tests d'analyse (commentaires de test) :")
        test_comments = [
            "Le montant brut renseigné est négatif pour plusieurs lignes de la classe 3.",
            "Le format de la date d'échéance est incorrect (attendu YYYY-MM-DD).",
            "Le champ RIB est vide pour 12 enregistrements — ce champ est obligatoire.",
            "La structure XML ne correspond pas au schéma XSD attendu pour BCT_01.",
            "La provision est insuffisante pour les crédits de classe D.",
            "Le montant impayé dépasse le montant du crédit accordé pour 3 lignes.",
        ]
        for comment in test_comments:
            try:
                analysis = svc.analyze(comment, top_k=3)
                n_sug    = len(analysis.suggestions)
                pct      = round(analysis.success_rate * 100)
                sugg_str = (
                    f"'{analysis.suggestions[0].correction_applied[:60]}...'"
                    if n_sug > 0 else "Aucune correction historique"
                )
                logger.info(
                    f"\n     «{comment[:55]}»\n"
                    f"       → Cluster    : {analysis.cluster_label}\n"
                    f"       → Suggestions: {n_sug} | Taux résolution: {pct}%\n"
                    f"       → 1ère       : {sugg_str}"
                )
            except Exception as e:
                logger.warning(f"     ⚠️ Erreur test : {e}")

    return result


# ══════════════════════════════════════════════════════════════════════
# MAIN
# ══════════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="Script d'entraînement BF17 — Banque Wifak BCT"
    )
    parser.add_argument(
        "--diagnose", action="store_true",
        help="Diagnostic BD uniquement, sans entraînement"
    )
    parser.add_argument(
        "--test", action="store_true",
        help="Lancer les tests d'analyse après entraînement"
    )
    args = parser.parse_args()

    logger.info("╔══════════════════════════════════════════════════════════════╗")
    logger.info("║   BF17 — Banque Wifak BCT — Script d'entraînement           ║")
    logger.info("╚══════════════════════════════════════════════════════════════╝")

    report = run_diagnostics()

    if args.diagnose:
        logger.info("\n  Mode --diagnose : entraînement ignoré.")
        return

    result = train_bf17(run_tests=args.test)

    logger.info("\n" + "═" * 65)
    if result.get("status") == "trained":
        logger.success("  🎉 BF17 entraîné avec succès !")
        logger.info("  Modèles → ./models/saved/bf17_*.pkl")
        logger.info("  Démarrer : uvicorn app.main:app --reload --port 8090")
        logger.info("  Swagger  : http://localhost:8090/docs")
    else:
        logger.warning(f"  ⚠️  Statut : {result.get('status')} — {result.get('message', '')}")
        logger.info("  Le modèle s'entraînera automatiquement dès que")
        logger.info("  des données de rejet seront disponibles.")
    logger.info("═" * 65)


if __name__ == "__main__":
    main()


# ══════════════════════════════════════════════════════════════════════
# requirements.txt (contenu à copier dans requirements.txt)
# ══════════════════════════════════════════════════════════════════════
REQUIREMENTS = """
# ═══════════════════════════════════════════════════════
# requirements.txt — ML Service BF17 — Banque Wifak BCT
# ═══════════════════════════════════════════════════════

# API Framework
fastapi==0.111.0
uvicorn[standard]==0.29.0
pydantic==2.7.1

# Base de données
SQLAlchemy==2.0.30
pymysql==1.1.0
cryptography==42.0.7

# Machine Learning
scikit-learn==1.4.2
numpy==1.26.4
pandas==2.2.2
joblib==1.4.2

# NLP
nltk==3.8.1

# Scheduling
apscheduler==3.10.4

# HTTP
httpx==0.27.0

# Config & Logs
python-dotenv==1.0.1
loguru==0.7.2

# Tests
pytest==8.2.0
pytest-asyncio==0.23.6
"""