"""
database.py — BF17 : Connexion MySQL réelle
══════════════════════════════════════════════════════════════════════
Fonctions :
  • load_reject_logs_df()        → commentaires de rejet réels
  • load_correction_history_df() → historique REJECT→SUBMIT→VALIDATE
  • diagnose_databases()         → diagnostic connectivité

Zéro données synthétiques — uniquement données réelles Banque Wifak.
══════════════════════════════════════════════════════════════════════
"""

import os
import pandas as pd
from sqlalchemy import create_engine, text, inspect
from loguru import logger
from dotenv import load_dotenv

load_dotenv()


# ══════════════════════════════════════════════════════════════════════
# MOTEURS MySQL
# ══════════════════════════════════════════════════════════════════════

def _engine(db_name: str):
    host     = os.getenv("DB_HOST",     "localhost")
    port     = os.getenv("DB_PORT",     "3306")
    user     = os.getenv("DB_USER",     "root")
    password = os.getenv("DB_PASSWORD", "")
    url = (
        f"mysql+pymysql://{user}:{password}@{host}:{port}/{db_name}"
        f"?charset=utf8mb4&connect_timeout=10"
    )
    return create_engine(
        url,
        pool_pre_ping=True,
        pool_recycle=3600,
        pool_timeout=30,
        max_overflow=5,
    )


def get_bct_engine():
    """Moteur vers la base principale de la plateforme BCT."""
    return _engine(os.getenv("DB_NAME", "wifak_PFE"))


def get_validation_engine():
    """Moteur vers la base du validation-service."""
    return _engine(os.getenv("VALIDATION_DB_NAME", "wifak_validation"))


# ══════════════════════════════════════════════════════════════════════
# DIAGNOSTIC
# ══════════════════════════════════════════════════════════════════════

def diagnose_databases() -> dict:
    """
    Vérifie la connectivité aux deux bases et retourne un rapport complet.
    Appelé au démarrage du ML Service.
    """
    report = {"bct": {}, "validation": {}, "ok": False}

    # ── Base principale ───────────────────────────────────────────────
    try:
        engine = get_bct_engine()
        with engine.connect() as conn:
            tables = inspect(engine).get_table_names()
            report["bct"]["tables"]    = tables
            report["bct"]["connected"] = True
            logger.info(f"✅ [DB] wifak_PFE connectée — tables : {tables}")
    except Exception as e:
        logger.error(f"❌ [DB] wifak_PFE inaccessible : {e}")
        report["bct"]["connected"] = False
        report["bct"]["error"]     = str(e)

    # ── Base validation ───────────────────────────────────────────────
    try:
        engine = get_validation_engine()
        with engine.connect() as conn:
            tables = inspect(engine).get_table_names()
            report["validation"]["tables"]    = tables
            report["validation"]["connected"] = True
            logger.info(f"✅ [DB] wifak_validation connectée — tables : {tables}")

            if "validation_logs" in tables:
                cols  = [c["name"] for c in inspect(engine).get_columns("validation_logs")]
                count = conn.execute(text("SELECT COUNT(*) FROM `validation_logs`")).scalar()

                report["validation"]["validation_logs"]       = cols
                report["validation"]["validation_logs_count"] = count

                actions = conn.execute(
                    text("SELECT action, COUNT(*) FROM validation_logs GROUP BY action")
                ).fetchall()
                report["validation"]["actions"] = {str(r[0]): int(r[1]) for r in actions}

                logger.info(
                    f"   validation_logs : {count} lignes | "
                    f"actions : {report['validation']['actions']}"
                )
    except Exception as e:
        logger.error(f"❌ [DB] wifak_validation inaccessible : {e}")
        report["validation"]["connected"] = False
        report["validation"]["error"]     = str(e)

    report["ok"] = (
        report["bct"].get("connected", False)
        and report["validation"].get("connected", False)
    )
    return report


# ══════════════════════════════════════════════════════════════════════
# BF17 — Chargement des commentaires de rejet
# ══════════════════════════════════════════════════════════════════════

def load_reject_logs_df() -> pd.DataFrame:
    """
    Charge tous les commentaires de rejet réels depuis validation_logs.

    Colonnes retournées :
      id | declaration_id | commentaire | effectue_par | date_action

    Retourne un DataFrame vide si aucune donnée ou erreur BD.
    """
    try:
        engine = get_validation_engine()
        with engine.connect() as conn:
            cols = [c["name"] for c in inspect(engine).get_columns("validation_logs")]
            logger.debug(f"[DB] validation_logs colonnes détectées : {cols}")

            # Détection automatique des noms de colonnes
            comment_col = next(
                (c for c in ["commentaire", "comment", "motif", "description"] if c in cols),
                None
            )
            if not comment_col:
                logger.warning("[DB] Aucune colonne de commentaire trouvée dans validation_logs")
                return pd.DataFrame()

            action_col = "action" if "action" in cols else None
            user_col   = next((c for c in ["effectue_par", "user", "username"] if c in cols), None)
            date_col   = next((c for c in ["date_action", "created_at", "date"] if c in cols), None)
            decl_col   = next((c for c in ["declaration_id", "declarationId", "decl_id"] if c in cols), None)

            selects = ["vl.id"]
            selects.append(f"vl.{decl_col} AS declaration_id"  if decl_col   else "NULL AS declaration_id")
            selects.append(f"vl.{comment_col} AS commentaire")
            selects.append(f"vl.{user_col} AS effectue_par"    if user_col   else "'system' AS effectue_par")
            selects.append(f"vl.{date_col} AS date_action"     if date_col   else "NOW() AS date_action")

            where = f"WHERE vl.{comment_col} IS NOT NULL AND TRIM(vl.{comment_col}) != ''"
            if action_col:
                where += f" AND vl.{action_col} = 'REJECT'"

            query = f"""
                SELECT {', '.join(selects)}
                FROM validation_logs vl
                {where}
                ORDER BY vl.id DESC
                LIMIT 5000
            """
            df = pd.read_sql(text(query), conn)

        if "date_action" in df.columns:
            df["date_action"] = pd.to_datetime(df["date_action"], errors="coerce")

        # Nettoyage : supprimer les commentaires trop courts
        df = df[df["commentaire"].str.strip().str.len() >= 10].reset_index(drop=True)

        logger.info(f"✅ [DB] {len(df)} commentaires de rejet chargés")
        return df

    except Exception as e:
        logger.error(f"❌ [DB] Erreur chargement reject logs : {e}")
        return pd.DataFrame()


# ══════════════════════════════════════════════════════════════════════
# BF17 — Reconstruction de l'historique des corrections
# ══════════════════════════════════════════════════════════════════════

def load_correction_history_df() -> pd.DataFrame:
    """
    Reconstruit l'historique REJECT → SUBMIT → VALIDATE depuis validation_logs.

    Pour chaque rejet :
      - Trouve le SUBMIT suivant → sa description = correction appliquée
      - Trouve le VALIDATE suivant → indique si la correction a été acceptée
      - Calcule le délai en heures

    Colonnes retournées :
      declaration_id | reject_comment | correction_applied | corrected_by |
      validated_by   | validated_at   | correction_delay_hours

    Retourne un DataFrame vide si aucune donnée.
    """
    try:
        engine = get_validation_engine()
        with engine.connect() as conn:
            cols = [c["name"] for c in inspect(engine).get_columns("validation_logs")]

            action_col  = "action" if "action" in cols else None
            comment_col = next((c for c in ["commentaire", "comment", "motif"] if c in cols), None)
            user_col    = next((c for c in ["effectue_par", "user", "username"] if c in cols), None)
            date_col    = next((c for c in ["date_action", "created_at", "date"] if c in cols), None)
            decl_col    = next((c for c in ["declaration_id", "declarationId", "decl_id"] if c in cols), None)

            if not action_col or not decl_col:
                logger.warning("[DB] Colonnes action/declaration_id manquantes")
                return pd.DataFrame()

            query = f"""
                SELECT
                    vl.id,
                    vl.{decl_col}   AS declaration_id,
                    vl.{action_col} AS action,
                    {'vl.' + comment_col + ' AS commentaire' if comment_col else 'NULL AS commentaire'},
                    {'vl.' + user_col + ' AS effectue_par'  if user_col    else "'system' AS effectue_par"},
                    {'vl.' + date_col + ' AS date_action'   if date_col    else 'NOW() AS date_action'}
                FROM validation_logs vl
                WHERE vl.{action_col} IN ('REJECT', 'SUBMIT', 'VALIDATE')
                ORDER BY vl.{decl_col}, {('vl.' + date_col) if date_col else 'vl.id'}
            """
            df = pd.read_sql(text(query), conn)

        if df.empty:
            logger.info("[DB] Aucun historique de corrections disponible")
            return pd.DataFrame()

        if "date_action" in df.columns:
            df["date_action"] = pd.to_datetime(df["date_action"], errors="coerce")

        # ── Reconstruction séquentielle REJECT → SUBMIT → VALIDATE ────
        history_rows = []
        for decl_id, grp in df.groupby("declaration_id"):
            grp       = grp.sort_values("date_action").reset_index(drop=True)
            rejects   = grp[grp["action"] == "REJECT"].to_dict("records")
            submits   = grp[grp["action"] == "SUBMIT"].to_dict("records")
            validates = grp[grp["action"] == "VALIDATE"].to_dict("records")

            for rej in rejects:
                # Premier SUBMIT après ce rejet
                next_submit = next(
                    (s for s in submits
                     if pd.notna(s["date_action"])
                     and pd.notna(rej["date_action"])
                     and s["date_action"] > rej["date_action"]),
                    None,
                )
                # Premier VALIDATE après ce SUBMIT
                next_validate = None
                if next_submit:
                    next_validate = next(
                        (v for v in validates
                         if pd.notna(v["date_action"])
                         and v["date_action"] > next_submit["date_action"]),
                        None,
                    )

                # La correction = commentaire du SUBMIT suivant (si renseigné)
                # Sinon on réutilise le commentaire du rejet lui-même
                correction_text = ""
                if next_submit and next_submit.get("commentaire"):
                    correction_text = str(next_submit["commentaire"]).strip()
                elif rej.get("commentaire"):
                    correction_text = str(rej["commentaire"]).strip()

                # Délai rejet → resoumission
                delay = None
                if (
                    next_submit
                    and pd.notna(rej["date_action"])
                    and pd.notna(next_submit["date_action"])
                ):
                    delta = next_submit["date_action"] - rej["date_action"]
                    delay = max(0, int(delta.total_seconds() / 3600))

                history_rows.append({
                    "declaration_id":         int(decl_id),
                    "reject_comment":         str(rej.get("commentaire") or ""),
                    "correction_applied":     correction_text,
                    "corrected_by":           str(next_submit["effectue_par"]) if next_submit   else "",
                    "validated_by":           str(next_validate["effectue_par"]) if next_validate else "",
                    "validated_at":           next_validate["date_action"] if next_validate else None,
                    "correction_delay_hours": delay,
                    "was_validated":          next_validate is not None,
                })

        result_df = pd.DataFrame(history_rows) if history_rows else pd.DataFrame()

        # Ne garder que les lignes avec une vraie correction
        if not result_df.empty:
            result_df = result_df[
                result_df["correction_applied"].str.strip().str.len() >= 5
            ].reset_index(drop=True)

        logger.info(f"✅ [DB] {len(result_df)} corrections historiques reconstruites")
        return result_df

    except Exception as e:
        logger.error(f"❌ [DB] Erreur chargement historique corrections : {e}")
        return pd.DataFrame()