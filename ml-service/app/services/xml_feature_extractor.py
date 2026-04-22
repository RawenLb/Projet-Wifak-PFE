"""
xml_feature_extractor.py  — Version PRODUCTION
══════════════════════════════════════════════════════════════
Extraction de features RÉELLES depuis le contenu XML/CSV/TXT
des déclarations BCT.

Améliorations v3 :
  - Parser XML plus robuste (namespace-aware, fallback regex)
  - Extraction CSV/TXT pour formats non-XML
  - Stats de référence persistées et rechargées efficacement
  - Gestion des XML encodés en latin-1 / utf-8 / utf-16
  - Détection automatique du type depuis le contenu si absent
"""

import re
import xml.etree.ElementTree as ET
import io
from typing import Optional
import pandas as pd
import numpy as np
from loguru import logger

# ── Champs numériques par type BCT ────────────────────────────
BCT_NUMERIC_FIELDS = {
    "BCT_01": ["MontantExposition", "TauxApplique", "LimiteExposition"],
    "BCT_02": ["PositionAchat", "PositionVente", "PositionNette", "LimiteAuthorisee"],
    "BCT_03": ["MontantExposition", "PourcentageFP", "LimiteConcentration"],
    "BCT_04": ["MontantOperation", "MontantGarantie"],
    "BCT_05": ["MontantCredit", "MontantImpaye", "Provision", "DureeRetard"],
}

# Tous les champs numériques connus (pour matching insensible à la casse)
ALL_KNOWN_FIELDS = set()
for fields in BCT_NUMERIC_FIELDS.values():
    ALL_KNOWN_FIELDS.update(f.lower() for f in fields)


def _parse_xml_robust(xml_content: str) -> Optional[ET.Element]:
    """
    Parse XML en gérant les cas courants :
    - Encodages mixtes
    - Namespaces
    - BOM
    - Erreurs de caractères
    """
    if not xml_content:
        return None

    # Nettoyer le BOM et espaces
    content = xml_content.strip()
    if content.startswith('\ufeff'):
        content = content[1:]

    # Tentative 1 : parse direct
    try:
        return ET.fromstring(content.encode("utf-8"))
    except Exception:
        pass

    # Tentative 2 : avec BytesIO (gère certains encodages)
    try:
        return ET.parse(io.BytesIO(content.encode("utf-8", errors="replace"))).getroot()
    except Exception:
        pass

    # Tentative 3 : supprimer les namespaces et réessayer
    try:
        cleaned = re.sub(r'\s+xmlns[^"]*"[^"]*"', '', content)
        cleaned = re.sub(r'<(\w+:)', '<', cleaned)
        cleaned = re.sub(r'</(\w+:)', '</', cleaned)
        return ET.fromstring(cleaned.encode("utf-8"))
    except Exception:
        pass

    # Tentative 4 : wrapper root
    try:
        return ET.fromstring(f"<_root_>{content}</_root_>".encode("utf-8"))
    except Exception:
        return None


def _extract_all_numeric(root: ET.Element, tag: str) -> list:
    """Extrait toutes les valeurs numériques d'un tag (insensible à la casse)."""
    values = []
    tag_lower = tag.lower()
    for elem in root.iter():
        # Normaliser le tag (supprimer namespace)
        elem_tag = elem.tag.split("}")[-1] if "}" in elem.tag else elem.tag
        if elem_tag.lower() == tag_lower and elem.text:
            text = elem.text.strip().replace(",", ".").replace(" ", "")
            try:
                values.append(float(text))
            except ValueError:
                pass
    return values


def _count_data_lines(root: ET.Element) -> int:
    """Compte les lignes de données dans le XML."""
    # Essayer plusieurs noms de balises possibles
    for tag in ["Ligne", "ligne", "Row", "row", "Record", "record",
                "Entry", "entry", "Item", "item", "Data"]:
        items = list(root.iter(tag))
        if items:
            return len(items)

    # Fallback : chercher les éléments répétés au niveau 2
    if len(list(root)) > 0:
        first_child = list(root)[0]
        children = list(first_child)
        if children:
            # Compter les éléments du même tag que le premier enfant
            first_tag = children[0].tag
            return sum(1 for c in first_child if c.tag == first_tag)

    return 0


def extract_features_from_xml(content: str, type_code: str) -> dict:
    """
    Extrait les features numériques depuis le contenu d'une déclaration.
    Supporte XML, CSV et TXT.
    """
    features = {
        "nb_lignes":       0,
        "file_size_chars": len(content) if content else 0,
    }

    if not content or not content.strip():
        return features

    content_stripped = content.strip()

    # ── CSV / TXT ─────────────────────────────────────────────
    if not content_stripped.startswith("<"):
        lines = [l for l in content_stripped.split("\n") if l.strip()]
        features["nb_lignes"] = max(0, len(lines) - 1)  # -1 en-tête

        # Parser le CSV pour extraire les montants
        if "," in content_stripped or ";" in content_stripped:
            sep = ";" if content_stripped.count(";") > content_stripped.count(",") else ","
            try:
                csv_df = pd.read_csv(io.StringIO(content_stripped), sep=sep,
                                     nrows=1000, on_bad_lines="skip")
                features["nb_lignes"] = len(csv_df)

                # Extraire les colonnes numériques
                for col in csv_df.select_dtypes(include=[np.number]).columns:
                    col_lower = col.lower().replace(" ", "").replace("_", "")
                    for field in BCT_NUMERIC_FIELDS.get(type_code, []):
                        if field.lower() == col_lower or col_lower in field.lower():
                            vals = csv_df[col].dropna().values
                            if len(vals) > 0:
                                features[f"{field}_sum"]  = float(np.sum(vals))
                                features[f"{field}_mean"] = float(np.mean(vals))
                                features[f"{field}_std"]  = float(np.std(vals)) if len(vals) > 1 else 0.0
                                features[f"{field}_min"]  = float(np.min(vals))
                                features[f"{field}_max"]  = float(np.max(vals))
                                features[f"{field}_neg_count"] = int(np.sum(vals < 0))
                                break
            except Exception as e:
                logger.debug(f"[XML_EXTRACTOR] CSV parse échoué: {e}")

        return features

    # ── XML ───────────────────────────────────────────────────
    root = _parse_xml_robust(content_stripped)
    if root is None:
        logger.debug(f"[XML_EXTRACTOR] XML invalide pour {type_code}")
        return features

    # Nombre de lignes
    features["nb_lignes"] = _count_data_lines(root)

    # Champs numériques spécifiques au type
    numeric_fields = BCT_NUMERIC_FIELDS.get(type_code, [])
    for field in numeric_fields:
        values = _extract_all_numeric(root, field)
        if values:
            arr = np.array(values)
            features[f"{field}_sum"]       = float(arr.sum())
            features[f"{field}_mean"]      = float(arr.mean())
            features[f"{field}_std"]       = float(arr.std()) if len(arr) > 1 else 0.0
            features[f"{field}_min"]       = float(arr.min())
            features[f"{field}_max"]       = float(arr.max())
            features[f"{field}_nb"]        = int(len(arr))
            features[f"{field}_neg_count"] = int((arr < 0).sum())
            features[f"{field}_zero_count"]= int((arr == 0).sum())

    # ── Features dérivées BCT_05 ──────────────────────────────
    if type_code == "BCT_05":
        credits  = _extract_all_numeric(root, "MontantCredit")
        impayes  = _extract_all_numeric(root, "MontantImpaye")
        provs    = _extract_all_numeric(root, "Provision")
        retards  = _extract_all_numeric(root, "DureeRetard")

        if credits and impayes and len(credits) == len(impayes):
            c_arr = np.array(credits)
            i_arr = np.array(impayes)
            with np.errstate(divide="ignore", invalid="ignore"):
                taux = np.where(c_arr > 0, i_arr / c_arr, 0.0)
            features["taux_impaye_mean"]       = float(taux.mean())
            features["taux_impaye_max"]        = float(taux.max())
            features["impaye_gt_credit_count"] = int((i_arr > c_arr).sum())

        if impayes and provs and len(impayes) == len(provs):
            i_arr = np.array(impayes)
            p_arr = np.array(provs)
            with np.errstate(divide="ignore", invalid="ignore"):
                ratio = np.where(i_arr > 0, p_arr / i_arr, 1.0)
            features["ratio_provision_impaye_mean"] = float(ratio.mean())
            features["sous_provisionnees_count"]    = int((ratio < 0.05).sum())

        if retards:
            r_arr = np.array(retards)
            features["retard_moyen"]  = float(r_arr.mean())
            features["retard_max"]    = float(r_arr.max())
            features["retard_positif_count"] = int((r_arr > 0).sum())

    # ── Features dérivées BCT_02 ──────────────────────────────
    elif type_code == "BCT_02":
        achats  = _extract_all_numeric(root, "PositionAchat")
        ventes  = _extract_all_numeric(root, "PositionVente")
        nettes  = _extract_all_numeric(root, "PositionNette")
        limites = _extract_all_numeric(root, "LimiteAuthorisee")

        if achats and ventes and nettes and len(achats) == len(nettes) == len(ventes):
            a = np.array(achats)
            v = np.array(ventes)
            n = np.array(nettes)
            expected = a - v
            errors = np.abs(n - expected)
            features["nette_calc_error_max"]   = float(errors.max())
            features["nette_calc_error_count"] = int((errors > 0.01).sum())

        if nettes and limites and len(nettes) == len(limites):
            n = np.array(nettes)
            l = np.array(limites)
            with np.errstate(divide="ignore", invalid="ignore"):
                ratio = np.where(l > 0, np.abs(n) / l, 0.0)
            features["ratio_position_limite_max"] = float(ratio.max())
            features["depassement_limite_count"]  = int((ratio > 1.0).sum())

    # ── Features dérivées BCT_01 ──────────────────────────────
    elif type_code == "BCT_01":
        expositions = _extract_all_numeric(root, "MontantExposition")
        taux_vals   = _extract_all_numeric(root, "TauxApplique")
        if expositions and taux_vals:
            t_arr = np.array(taux_vals)
            features["taux_hors_marche_count"] = int(((t_arr < 0) | (t_arr > 50)).sum())

    # ── Features dérivées BCT_03 ──────────────────────────────
    elif type_code == "BCT_03":
        pct_vals = _extract_all_numeric(root, "PourcentageFP")
        if pct_vals:
            p_arr = np.array(pct_vals)
            features["depassement_fp_count"] = int((p_arr > 25).sum())

    return features


def build_reference_stats(declarations_df: pd.DataFrame) -> dict:
    """
    Calcule les statistiques de référence PAR (type_code, file_format).
    Seules les déclarations avec contenu sont utilisées.
    """
    stats = {}

    if declarations_df.empty:
        return stats

    required = ["type_code", "contenu_fichier"]
    for col in required:
        if col not in declarations_df.columns:
            logger.warning(f"[STATS] Colonne manquante: {col}")
            return stats

    # Ajouter file_format si absent
    if "file_format" not in declarations_df.columns:
        declarations_df = declarations_df.copy()
        declarations_df["file_format"] = "XML"

    total_processed = 0
    for (type_code, file_format), group in declarations_df.groupby(["type_code", "file_format"]):
        key = f"{type_code}__{file_format}"
        feature_rows = []

        # Filtrer les lignes avec du contenu
        group_with_content = group[group["contenu_fichier"].notna() &
                                   (group["contenu_fichier"].astype(str).str.strip() != "")]

        if len(group_with_content) < 2:
            # Utiliser file_size_chars comme feature minimale
            if "file_size_chars" in group.columns and len(group) >= 2:
                vals = pd.to_numeric(group["file_size_chars"], errors="coerce").dropna()
                if len(vals) >= 2:
                    stats[key] = {
                        "file_size_chars": {
                            "mean": float(vals.mean()),
                            "std":  float(vals.std()) if len(vals) > 1 else 1.0,
                            "q25":  float(vals.quantile(0.25)),
                            "q75":  float(vals.quantile(0.75)),
                            "count": int(len(vals)),
                        }
                    }
            logger.debug(f"[STATS] {key} : contenu insuffisant ({len(group_with_content)} avec XML)")
            continue

        for _, row in group_with_content.iterrows():
            feats = extract_features_from_xml(str(row["contenu_fichier"]), str(type_code))
            if "validation_delay_hours" in row and pd.notna(row["validation_delay_hours"]):
                feats["validation_delay_hours"] = float(row["validation_delay_hours"])
            feature_rows.append(feats)
            total_processed += 1

        if not feature_rows:
            continue

        feat_df = pd.DataFrame(feature_rows).fillna(0)
        group_stats = {}

        for col in feat_df.select_dtypes(include=[np.number]).columns:
            vals = feat_df[col].astype(float)
            if vals.std() == 0 and vals.mean() == 0:
                continue
            group_stats[col] = {
                "mean":  float(vals.mean()),
                "std":   float(vals.std())   if len(vals) > 1 else 1.0,
                "q25":   float(vals.quantile(0.25)),
                "q75":   float(vals.quantile(0.75)),
                "count": int(len(vals)),
            }

        if group_stats:
            stats[key] = group_stats
            logger.info(f"[STATS] {key} → {len(group_stats)} features, {len(group_with_content)} décl. XML")

    logger.info(f"[STATS] Stats calculées pour {len(stats)} groupes ({total_processed} déclarations avec XML)")
    return stats


def compute_zscore_alerts(
    features: dict,
    ref_stats: dict,
    type_code: str,
    file_format: str,
    threshold: float = 3.0,
) -> list:
    """
    Calcule les alertes Z-Score pour une déclaration.
    Utilise les stats de référence du MÊME (type, format).
    """
    key = f"{type_code}__{file_format}"
    ref = ref_stats.get(key, {})

    if not ref:
        logger.debug(f"[ZSCORE] Pas de référence pour {key}")
        return []

    alerts = []
    for field_name, value in features.items():
        if field_name not in ref:
            continue
        if not isinstance(value, (int, float)) or np.isnan(value) or np.isinf(value):
            continue

        stat  = ref[field_name]
        mu    = stat["mean"]
        std   = max(stat["std"], 1e-10)  # éviter division par zéro

        z = abs((value - mu) / std)
        if z < threshold:
            continue

        pct_change = ((value - mu) / abs(mu) * 100) if abs(mu) > 1e-10 else 0

        # Sévérité graduée
        if z >= 6.0:
            severity = "critique"
        elif z >= 4.5:
            severity = "élevé"
        else:
            severity = "moyen"

        alerts.append({
            "field":      field_name,
            "value":      round(float(value), 4),
            "mean":       round(mu, 4),
            "std":        round(std, 4),
            "z_score":    round(z, 2),
            "pct_change": round(pct_change, 1),
            "severity":   severity,
            "n_ref":      stat["count"],
            "message":    _build_alert_message(field_name, value, mu, pct_change, stat["count"]),
        })

    alerts.sort(key=lambda a: a["z_score"], reverse=True)
    return alerts


def _build_alert_message(field: str, value: float, mean: float,
                         pct: float, n_ref: int) -> str:
    LABELS = {
        "nb_lignes":                 "Nombre de lignes",
        "file_size_chars":           "Taille du fichier (chars)",
        "MontantCredit_sum":         "Total montant crédit",
        "MontantImpaye_sum":         "Total montant impayé",
        "Provision_sum":             "Total provisions",
        "MontantExposition_sum":     "Total montant exposition",
        "PositionAchat_sum":         "Total position achat",
        "PositionVente_sum":         "Total position vente",
        "PositionNette_sum":         "Total position nette",
        "impaye_gt_credit_count":    "Lignes impayé > crédit",
        "sous_provisionnees_count":  "Lignes sous-provisionnées",
        "taux_impaye_mean":          "Taux d'impayé moyen",
        "nette_calc_error_count":    "Lignes erreur calcul PositionNette",
        "depassement_limite_count":  "Lignes dépassant limite autorisée",
        "MontantCredit_neg_count":   "Lignes montant crédit négatif",
        "MontantImpaye_neg_count":   "Lignes montant impayé négatif",
        "validation_delay_hours":    "Délai de validation",
        "retard_moyen":              "Durée moyenne de retard",
        "ratio_provision_impaye_mean": "Ratio provision/impayé moyen",
    }
    label = LABELS.get(field, field.replace("_", " ").title())
    direction = "supérieur" if value > mean else "inférieur"
    return (
        f"{label} ({value:,.2f}) est {direction} de {abs(pct):.1f}% "
        f"par rapport à la moyenne historique ({mean:,.2f}) "
        f"[{n_ref} décl. de référence, même type et format]."
    )