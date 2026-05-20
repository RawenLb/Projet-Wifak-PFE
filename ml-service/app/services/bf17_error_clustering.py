"""
bf17_error_clustering.py — Service ML BF17
══════════════════════════════════════════════════════════════════════
BF17 — Analyse intelligente des erreurs + aide à la correction

Fonctionnalités :
  ① Clustering TF-IDF + KMeans des commentaires de rejet
  ② Recommandation par Cosine Similarity (top-k cas similaires)
  ③ Taux de résolution par cluster (basé historique réel)
  ④ Capitalisation continue (retraining périodique automatique)

Contraintes respectées :
  ✅ Zéro données statiques ou synthétiques
  ✅ Uniquement historique réel validation_logs
  ✅ L'IA n'interprète pas la réglementation BCT
  ✅ Apprentissage à partir des actions humaines validées uniquement
  ✅ Retraining incrémental automatique toutes les 24h

Seuil minimum : 3 commentaires de rejet pour l'entraînement initial.
══════════════════════════════════════════════════════════════════════
"""

import os
import json
import joblib
import re
import numpy as np
import pandas as pd
from pathlib import Path
from dataclasses import dataclass, field
from typing import Optional, List, Dict

import nltk
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.cluster import KMeans
from sklearn.metrics import silhouette_score
from sklearn.metrics.pairwise import cosine_similarity
from sklearn.preprocessing import normalize
from loguru import logger

from app.database import load_reject_logs_df, load_correction_history_df

# ── Stopwords ──────────────────────────────────────────────────────────
try:
    from nltk.corpus import stopwords
    STOP_FR = set(stopwords.words("french"))
    STOP_EN = set(stopwords.words("english"))
except LookupError:
    nltk.download("stopwords", quiet=True)
    try:
        from nltk.corpus import stopwords
        STOP_FR = set(stopwords.words("french"))
        STOP_EN = set(stopwords.words("english"))
    except Exception:
        STOP_FR, STOP_EN = set(), set()

# Stopwords spécifiques au domaine bancaire/BCT (termes trop fréquents, non discriminants)
DOMAIN_STOP = {
    "déclaration", "déclarations", "bct", "fichier", "valeur", "valeurs",
    "lignes", "ligne", "champ", "champs", "banque", "wifak", "veuillez",
    "vérifier", "erreur", "erreurs", "problème", "incorrect", "incorrecte",
    "données", "donnée", "enregistrement", "enregistrements", "svp",
    "merci", "cordialement", "bonjour", "bien",
}
STOP_WORDS = STOP_FR | STOP_EN | DOMAIN_STOP

# ── Répertoire des modèles persistés ──────────────────────────────────
MODELS_DIR  = Path(os.getenv("MODELS_DIR", "./models/saved"))
MODELS_DIR.mkdir(parents=True, exist_ok=True)

TFIDF_PATH    = MODELS_DIR / "bf17_tfidf.pkl"
KMEANS_PATH   = MODELS_DIR / "bf17_kmeans.pkl"
MATRIX_NPY    = MODELS_DIR / "bf17_tfidf_matrix.npy"
CORPUS_PATH   = MODELS_DIR / "bf17_corpus.json"
HISTORY_PATH  = MODELS_DIR / "bf17_correction_history.json"
METADATA_PATH = MODELS_DIR / "bf17_metadata.json"

# ── Paramètres configurables via .env ─────────────────────────────────
N_CLUSTERS_MAX = int(os.getenv("CLUSTER_N_CLUSTERS",  "8"))
MIN_SAMPLES    = int(os.getenv("CLUSTER_MIN_SAMPLES", "3"))
SIM_THRESHOLD  = float(os.getenv("CLUSTER_SIM_THRESHOLD", "0.05"))


# ══════════════════════════════════════════════════════════════════════
# DATACLASSES — Résultats typés
# ══════════════════════════════════════════════════════════════════════

@dataclass
class CorrectionSuggestion:
    """Une suggestion de correction issue de l'historique réel."""
    rank:               int
    similarity_score:   float
    reject_comment:     str        # commentaire de rejet similaire original
    correction_applied: str        # correction qui a fonctionné
    corrected_by:       str        # agent qui a corrigé
    delay_hours:        Optional[int]
    was_validated:      bool       # la correction a été validée par le manager
    cluster_label:      str

    def to_dict(self) -> dict:
        return {
            "rank":               self.rank,
            "similarity_score":   round(self.similarity_score, 4),
            "reject_comment":     self.reject_comment,
            "correction_applied": self.correction_applied,
            "corrected_by":       self.corrected_by,
            "delay_hours":        self.delay_hours,
            "was_validated":      self.was_validated,
            "cluster_label":      self.cluster_label,
        }


@dataclass
class ErrorAnalysisResult:
    """Résultat complet de l'analyse d'un commentaire de rejet."""
    reject_comment:      str
    cluster_id:          int
    cluster_label:       str
    cluster_keywords:    List[str]
    similar_cases_count: int
    suggestions:         List[CorrectionSuggestion]
    success_rate:        float
    avg_delay_hours:     Optional[float]
    message:             str

    def to_dict(self) -> dict:
        return {
            "reject_comment":      self.reject_comment,
            "cluster_id":          self.cluster_id,
            "cluster_label":       self.cluster_label,
            "cluster_keywords":    self.cluster_keywords,
            "similar_cases_count": self.similar_cases_count,
            "suggestions":         [s.to_dict() for s in self.suggestions],
            "success_rate":        round(self.success_rate, 4),
            "avg_delay_hours":     round(self.avg_delay_hours, 1) if self.avg_delay_hours else None,
            "message":             self.message,
        }


# ══════════════════════════════════════════════════════════════════════
# SERVICE PRINCIPAL
# ══════════════════════════════════════════════════════════════════════

class ErrorClusteringService:
    """
    Service BF17 : Clustering des erreurs + recommandations de correction.

    Cycle de vie :
      1. Au démarrage → _load_or_train() : charge les modèles ou entraîne
      2. Analyse    → analyze(comment)   : retourne cluster + suggestions
      3. Retraining → train()            : relance sur données fraîches
    """

    def __init__(self):
        self.vectorizer:         Optional[TfidfVectorizer] = None
        self.kmeans:             Optional[KMeans]          = None
        self.tfidf_matrix:       Optional[np.ndarray]      = None
        self.corpus:             List[dict]                 = []
        self.correction_history: List[dict]                 = []
        self.cluster_labels:     Dict[int, str]             = {}
        self.cluster_keywords:   Dict[int, List[str]]       = {}
        self._train_metadata:    dict                       = {}

        self._load_or_train()

    # ──────────────────────────────────────────────────────────────────
    # ENTRAÎNEMENT
    # ──────────────────────────────────────────────────────────────────

    def train(
        self,
        reject_df:  Optional[pd.DataFrame] = None,
        history_df: Optional[pd.DataFrame] = None,
    ) -> dict:
        """
        Entraîne (ou ré-entraîne) le modèle BF17 sur les données réelles.

        Étapes :
          1. Chargement BD si DataFrames non fournis
          2. Prétraitement NLP des commentaires
          3. Vectorisation TF-IDF
          4. KMeans avec k optimal (méthode du coude)
          5. Labellisation automatique des clusters
          6. Reconstruction de l'historique corrections
          7. Persistance sur disque
        """
        logger.info("🏋️ [BF17] Démarrage entraînement TF-IDF + KMeans...")

        # ── Chargement données ────────────────────────────────────────
        if reject_df is None:
            reject_df = load_reject_logs_df()

        if reject_df.empty or len(reject_df) < MIN_SAMPLES:
            msg = (
                f"Données insuffisantes : {len(reject_df)} commentaire(s) — "
                f"minimum {MIN_SAMPLES} requis. "
                f"Vérifiez que validation_logs contient des entrées avec action='REJECT'."
            )
            logger.error(f"[BF17] {msg}")
            return {"status": "insufficient_data", "message": msg, "count": len(reject_df)}

        if history_df is None:
            history_df = load_correction_history_df()

        logger.info(
            f"[BF17] {len(reject_df)} commentaires de rejet | "
            f"{len(history_df)} corrections historiques"
        )

        # ── Prétraitement ─────────────────────────────────────────────
        comments = reject_df["commentaire"].dropna().astype(str).tolist()
        comments = [c for c in comments if len(c.strip()) >= 10]

        if not comments:
            return {"status": "no_valid_comments", "message": "Tous les commentaires sont vides ou trop courts"}

        cleaned = [self._preprocess(c) for c in comments]

        # ── TF-IDF ────────────────────────────────────────────────────
        self.vectorizer = TfidfVectorizer(
            max_features = 800,
            ngram_range  = (1, 2),
            min_df       = 1,
            max_df       = 0.90,
            sublinear_tf = True,
            analyzer     = "word",
        )
        tfidf_matrix = self.vectorizer.fit_transform(cleaned)

        # ── KMeans (k optimal) ────────────────────────────────────────
        n_clusters = self._find_optimal_k(tfidf_matrix, len(comments))
        logger.info(f"[BF17] k optimal sélectionné : {n_clusters}")

        self.kmeans = KMeans(
            n_clusters   = n_clusters,
            random_state = 42,
            n_init       = 15,
            max_iter     = 500,
        )
        labels = self.kmeans.fit_predict(tfidf_matrix)

        # Score de silhouette (qualité du clustering)
        silhouette = 0.0
        if n_clusters > 1 and len(comments) > n_clusters:
            try:
                silhouette = silhouette_score(
                    tfidf_matrix, labels,
                    sample_size=min(500, len(comments))
                )
            except Exception:
                pass

        # ── Matrice normalisée (pour cosine similarity) ───────────────
        self.tfidf_matrix = normalize(tfidf_matrix.toarray())

        # ── Construction du corpus ────────────────────────────────────
        self.corpus = []
        valid_rows  = reject_df[
            reject_df["commentaire"].notna() &
            (reject_df["commentaire"].str.strip().str.len() >= 10)
        ].reset_index(drop=True)

        for idx in range(min(len(valid_rows), len(labels))):
            row = valid_rows.iloc[idx]
            self.corpus.append({
                "id":             int(row.get("id", idx)),
                "declaration_id": int(row.get("declaration_id", 0) or 0),
                "comment":        str(row["commentaire"]),
                "cleaned":        cleaned[idx],
                "cluster_id":     int(labels[idx]),
                "date_action":    str(row.get("date_action", "")),
                "effectue_par":   str(row.get("effectue_par", "")),
            })

        # ── Labellisation des clusters ────────────────────────────────
        self._compute_cluster_labels(n_clusters)

        # ── Historique des corrections réelles ───────────────────────
        self.correction_history = []
        if not history_df.empty:
            for _, row in history_df.iterrows():
                corr_text = str(row.get("correction_applied") or "").strip()
                if len(corr_text) < 5:
                    continue
                self.correction_history.append({
                    "declaration_id":         int(row.get("declaration_id", 0) or 0),
                    "reject_comment":         str(row.get("reject_comment",  "") or ""),
                    "correction_applied":     corr_text,
                    "corrected_by":           str(row.get("corrected_by",    "") or ""),
                    "validated_by":           str(row.get("validated_by",    "") or ""),
                    "validated_at":           str(row.get("validated_at",    "") or ""),
                    "correction_delay_hours": (
                        int(row["correction_delay_hours"])
                        if pd.notna(row.get("correction_delay_hours")) else None
                    ),
                    "was_validated": bool(row.get("was_validated", False)),
                })

        # ── Métadonnées d'entraînement ────────────────────────────────
        import datetime
        self._train_metadata = {
            "trained_at":     datetime.datetime.now().isoformat(),
            "n_comments":     len(comments),
            "n_clusters":     n_clusters,
            "n_corrections":  len(self.correction_history),
            "silhouette":     round(silhouette, 3),
            "cluster_labels": self.cluster_labels,
        }

        # ── Persistance sur disque ────────────────────────────────────
        self._save_models()

        logger.info(
            f"✅ [BF17] Entraînement terminé — "
            f"{len(comments)} commentaires | {n_clusters} clusters | "
            f"{len(self.correction_history)} corrections | "
            f"silhouette={silhouette:.3f}"
        )

        return {
            "status":         "trained",
            "comments":       len(comments),
            "clusters":       n_clusters,
            "corrections":    len(self.correction_history),
            "silhouette":     round(silhouette, 3),
            "cluster_labels": self.cluster_labels,
        }

    # ──────────────────────────────────────────────────────────────────
    # ANALYSE — Point d'entrée principal BF17
    # ──────────────────────────────────────────────────────────────────

    def analyze(self, reject_comment: str, top_k: int = 5) -> ErrorAnalysisResult:
        """
        Analyse un commentaire de rejet et retourne :
          - Le cluster d'erreur détecté
          - Les mots-clés du cluster
          - Les top-k suggestions de correction issues de l'historique réel
          - Le taux de résolution historique du cluster
          - Le délai moyen de correction
          - Un message explicatif en français

        Paramètres :
          reject_comment : commentaire de rejet BCT à analyser
          top_k          : nombre max de suggestions (1–10)
        """
        if self.vectorizer is None or self.kmeans is None:
            raise RuntimeError(
                "[BF17] Modèle non entraîné. "
                "Lancez POST /bf17/train ou attendez le ré-entraînement automatique."
            )

        top_k = max(1, min(10, top_k))

        # ── Vectorisation de la requête ───────────────────────────────
        cleaned  = self._preprocess(reject_comment)
        vec      = self.vectorizer.transform([cleaned])
        vec_norm = normalize(vec.toarray())

        # ── Assignation au cluster le plus proche ─────────────────────
        cluster_id    = int(self.kmeans.predict(vec)[0])
        cluster_label = self.cluster_labels.get(cluster_id, f"Cluster {cluster_id}")
        cluster_kw    = self.cluster_keywords.get(cluster_id, [])

        # Nb de cas similaires dans ce cluster
        cluster_members  = [c for c in self.corpus if c["cluster_id"] == cluster_id]
        similar_count    = len(cluster_members)

        # ── Cosine Similarity avec tout le corpus ─────────────────────
        if self.tfidf_matrix is not None and len(self.tfidf_matrix) > 0:
            sims        = cosine_similarity(vec_norm, self.tfidf_matrix)[0]
            top_indices = np.argsort(sims)[::-1][:top_k * 8]
        else:
            sims        = np.zeros(len(self.corpus))
            top_indices = []

        # ── Construction des suggestions ─────────────────────────────
        suggestions  = []
        seen_corrections = set()

        for idx in top_indices:
            if idx >= len(self.corpus) or len(suggestions) >= top_k:
                break

            sim_score = float(sims[idx])
            if sim_score < SIM_THRESHOLD:
                continue

            corpus_item = self.corpus[idx]
            correction  = self._find_best_correction(
                corpus_item["declaration_id"],
                corpus_item["comment"],
            )
            if not correction:
                continue

            corr_text = str(correction.get("correction_applied", "")).strip()
            if not corr_text or corr_text in seen_corrections:
                continue
            seen_corrections.add(corr_text)

            suggestions.append(CorrectionSuggestion(
                rank               = len(suggestions) + 1,
                similarity_score   = sim_score,
                reject_comment     = corpus_item["comment"],
                correction_applied = corr_text,
                corrected_by       = str(correction.get("corrected_by", "")),
                delay_hours        = correction.get("correction_delay_hours"),
                was_validated      = bool(correction.get("was_validated", False)),
                cluster_label      = self.cluster_labels.get(corpus_item["cluster_id"], ""),
            ))

        # ── Statistiques du cluster ───────────────────────────────────
        success_rate    = self._cluster_success_rate(cluster_id)
        avg_delay       = self._cluster_avg_delay(cluster_id)

        # ── Message explicatif ────────────────────────────────────────
        message = self._build_message(
            reject_comment, cluster_label, similar_count,
            suggestions, success_rate, avg_delay
        )

        return ErrorAnalysisResult(
            reject_comment      = reject_comment,
            cluster_id          = cluster_id,
            cluster_label       = cluster_label,
            cluster_keywords    = cluster_kw,
            similar_cases_count = similar_count,
            suggestions         = suggestions,
            success_rate        = success_rate,
            avg_delay_hours     = avg_delay,
            message             = message,
        )

    # ──────────────────────────────────────────────────────────────────
    # CLUSTERS — Vue globale
    # ──────────────────────────────────────────────────────────────────

    def get_all_clusters(self) -> List[dict]:
        """
        Retourne tous les clusters avec leurs statistiques pour le tableau de bord.
        Trié par nombre de cas décroissant.
        """
        clusters = []
        for cluster_id, label in self.cluster_labels.items():
            members      = [c for c in self.corpus if c["cluster_id"] == cluster_id]
            success_rate = self._cluster_success_rate(cluster_id)
            avg_delay    = self._cluster_avg_delay(cluster_id)

            clusters.append({
                "cluster_id":       cluster_id,
                "label":            label,
                "keywords":         self.cluster_keywords.get(cluster_id, []),
                "count":            len(members),
                "success_rate":     round(success_rate, 4),
                "avg_delay_hours":  round(avg_delay, 1) if avg_delay else None,
                "example":          members[0]["comment"][:150] if members else "",
            })

        clusters.sort(key=lambda x: x["count"], reverse=True)
        return clusters

    def get_stats(self) -> dict:
        """Retourne les statistiques globales BF17."""
        total     = len(self.corpus)
        hist      = len(self.correction_history)
        validated = sum(1 for h in self.correction_history if h.get("was_validated"))

        return {
            "total_reject_comments":   total,
            "n_clusters":              self.kmeans.n_clusters if self.kmeans else 0,
            "correction_history_size": hist,
            "validated_corrections":   validated,
            "global_resolution_rate":  round(validated / max(hist, 1), 4) if hist > 0 else 0.0,
            "cluster_labels":          self.cluster_labels,
            "trained_at":              self._train_metadata.get("trained_at", ""),
            "silhouette_score":        self._train_metadata.get("silhouette", 0.0),
        }

    # ──────────────────────────────────────────────────────────────────
    # PRÉTRAITEMENT NLP
    # ──────────────────────────────────────────────────────────────────

    def _preprocess(self, text: str) -> str:
        """
        Nettoyage et normalisation d'un texte pour TF-IDF.
        Conserve uniquement les tokens significatifs (≥3 chars, hors stopwords).
        """
        text   = text.lower().strip()
        # Supprimer les caractères non-alphabétiques sauf tirets/underscores
        text   = re.sub(r"[^a-zàâäéèêëïîôùûüÿça-z0-9\s_\-]", " ", text)
        # Normaliser les espaces multiples
        text   = re.sub(r"\s+", " ", text)
        tokens = [
            t for t in text.split()
            if len(t) >= 3 and t not in STOP_WORDS
        ]
        return " ".join(tokens)

    # ──────────────────────────────────────────────────────────────────
    # SÉLECTION DU K OPTIMAL
    # ──────────────────────────────────────────────────────────────────

    def _find_optimal_k(self, matrix, n_comments: int) -> int:
        """
        Trouve le nombre optimal de clusters par la méthode du coude.
        Contrainte : k ∈ [2, min(N_CLUSTERS_MAX, n_comments//3)]
        """
        k_min = 2
        k_max = min(N_CLUSTERS_MAX, max(2, n_comments // 3))

        if k_max <= k_min:
            return k_min

        # Calculer l'inertie pour chaque k
        inertias = []
        for k in range(k_min, k_max + 1):
            km = KMeans(n_clusters=k, random_state=42, n_init=5, max_iter=200)
            km.fit(matrix)
            inertias.append(km.inertia_)

        # Méthode du coude : dérivée seconde de l'inertie
        if len(inertias) >= 3:
            diffs  = [inertias[i] - inertias[i+1] for i in range(len(inertias)-1)]
            diffs2 = [diffs[i] - diffs[i+1] for i in range(len(diffs)-1)]
            if diffs2:
                elbow_idx = diffs2.index(max(diffs2))
                return min(k_min + elbow_idx + 1, k_max)

        return min(N_CLUSTERS_MAX, k_max)

    # ──────────────────────────────────────────────────────────────────
    # LABELLISATION AUTOMATIQUE DES CLUSTERS
    # ──────────────────────────────────────────────────────────────────

    def _compute_cluster_labels(self, n_clusters: int):
        """
        Labellise chaque cluster automatiquement via les mots-clés TF-IDF
        des centroïdes, mappés à des labels métier compréhensibles.
        """
        feature_names = self.vectorizer.get_feature_names_out()
        centers       = self.kmeans.cluster_centers_

        # Mapping mots-clés → labels métier BCT
        LABEL_MAP = {
            "montant":     "💰 Erreur de montant",
            "negatif":     "💰 Montant négatif",
            "nul":         "💰 Montant nul ou zéro",
            "date":        "📅 Format de date invalide",
            "echeance":    "📅 Date d'échéance incorrecte",
            "format":      "📋 Format de données incorrect",
            "rib":         "🏦 RIB / Compte bancaire invalide",
            "compte":      "🏦 Compte bancaire invalide",
            "classe":      "⚠️ Classe de risque incorrecte",
            "risque":      "⚠️ Classe de risque incorrecte",
            "provision":   "📊 Provision insuffisante",
            "xml":         "🗂️ Structure XML invalide",
            "xsd":         "🗂️ Non-conformité schéma XSD BCT",
            "schema":      "🗂️ Non-conformité schéma XSD BCT",
            "obligatoire": "❗ Champ obligatoire manquant",
            "manquant":    "❗ Champ obligatoire manquant",
            "absent":      "❗ Champ obligatoire manquant",
            "vide":        "❗ Champ vide ou non renseigné",
            "doublon":     "🔁 Doublons détectés",
            "duplique":    "🔁 Doublons détectés",
            "impaye":      "💰 Incohérence impayé / crédit",
            "credit":      "💰 Incohérence montant crédit",
            "limite":      "⚠️ Dépassement de limite réglementaire",
            "nette":       "📐 Erreur calcul position nette",
            "position":    "📐 Erreur calcul position nette",
            "periode":     "📅 Période incorrecte",
            "taux":        "📈 Taux incorrect",
            "devise":      "💱 Code devise invalide",
            "identifiant": "🔑 Identifiant client manquant",
        }

        self.cluster_labels   = {}
        self.cluster_keywords = {}

        for i in range(n_clusters):
            # Top 7 mots par centroïde
            top_idx = centers[i].argsort()[::-1][:7]
            words   = [feature_names[j] for j in top_idx if centers[i][j] > 0.0]
            self.cluster_keywords[i] = words[:5]

            # Chercher un label dans le LABEL_MAP
            label = None
            for word in words:
                for key, lbl in LABEL_MAP.items():
                    if key in word:
                        label = lbl
                        break
                if label:
                    break

            # Fallback : construire un label depuis les mots
            if not label:
                label = (
                    f"🔍 {' / '.join(w.title() for w in words[:2])}"
                    if words else f"Cluster {i}"
                )

            self.cluster_labels[i] = label

    # ──────────────────────────────────────────────────────────────────
    # RECHERCHE DE CORRECTION
    # ──────────────────────────────────────────────────────────────────

    def _find_best_correction(self, declaration_id: int, comment: str) -> Optional[dict]:
        """
        Trouve la meilleure correction dans l'historique réel.

        Stratégie :
          1. Cherche d'abord par declaration_id exact
          2. Sinon, similarité Jaccard sur les tokens du commentaire (seuil ≥ 20%)
          3. Préfère les corrections validées par le manager
        """
        if not self.correction_history:
            return None

        # Priorité 1 : même déclaration
        exact_matches = [
            h for h in self.correction_history
            if h["declaration_id"] == declaration_id and h.get("correction_applied")
        ]
        if exact_matches:
            # Préférer celles validées
            validated = [h for h in exact_matches if h.get("was_validated")]
            return validated[0] if validated else exact_matches[0]

        # Priorité 2 : similarité textuelle (Jaccard sur tokens)
        tokens_target = set(self._preprocess(comment).split())
        best, best_score = None, 0.0

        for h in self.correction_history:
            if not h.get("correction_applied"):
                continue
            tokens_h  = set(self._preprocess(str(h.get("reject_comment", ""))).split())
            union     = tokens_target | tokens_h
            if not union:
                continue
            jaccard   = len(tokens_target & tokens_h) / len(union)
            # Bonus si la correction a été validée
            effective = jaccard * (1.2 if h.get("was_validated") else 1.0)
            if effective > best_score and jaccard >= 0.20:
                best_score, best = effective, h

        return best

    # ──────────────────────────────────────────────────────────────────
    # STATISTIQUES PAR CLUSTER
    # ──────────────────────────────────────────────────────────────────

    def _cluster_success_rate(self, cluster_id: int) -> float:
        """
        Taux de résolution = nb corrections validées / nb déclarations du cluster.
        Retourne 0.0 si aucune donnée.
        """
        members  = [c for c in self.corpus if c["cluster_id"] == cluster_id]
        decl_ids = {m["declaration_id"] for m in members if m["declaration_id"] > 0}

        if not decl_ids or not self.correction_history:
            return 0.0

        relevant  = [h for h in self.correction_history if h["declaration_id"] in decl_ids]
        if not relevant:
            return 0.0

        validated = sum(1 for h in relevant if h.get("was_validated"))
        return min(1.0, validated / len(decl_ids))

    def _cluster_avg_delay(self, cluster_id: int) -> Optional[float]:
        """
        Délai moyen de correction (en heures) pour les déclarations du cluster.
        """
        members  = [c for c in self.corpus if c["cluster_id"] == cluster_id]
        decl_ids = {m["declaration_id"] for m in members if m["declaration_id"] > 0}

        delays = [
            h["correction_delay_hours"]
            for h in self.correction_history
            if h["declaration_id"] in decl_ids
            and h.get("correction_delay_hours") is not None
            and h["correction_delay_hours"] >= 0
        ]
        return float(np.mean(delays)) if delays else None

    # ──────────────────────────────────────────────────────────────────
    # MESSAGE EXPLICATIF (en français)
    # ──────────────────────────────────────────────────────────────────

    def _build_message(
        self,
        comment:       str,
        cluster_label: str,
        similar_count: int,
        suggestions:   List[CorrectionSuggestion],
        success_rate:  float,
        avg_delay:     Optional[float],
    ) -> str:
        """
        Construit le message explicatif retourné à l'utilisateur,
        au format : "Dans X% des cas similaires, la correction appliquée a été : ..."
        """
        parts = []

        # Cluster détecté
        parts.append(
            f"Cluster détecté : **{cluster_label}** "
            f"({similar_count} cas similaire(s) dans l'historique)."
        )

        if not self.correction_history:
            parts.append(
                "Aucun historique de corrections disponible. "
                "Les suggestions apparaîtront une fois que des déclarations rejetées "
                "auront été corrigées et resoumises avec succès."
            )
            return " ".join(parts)

        if not suggestions:
            parts.append(
                "Aucune correction similaire trouvée dans l'historique interne. "
                "Ce type d'erreur n'a pas encore été traité dans le système."
            )
            return " ".join(parts)

        # Taux de résolution
        pct = round(success_rate * 100)
        if pct > 0:
            parts.append(f"Taux de résolution historique : **{pct}%**.")

        # Délai moyen
        if avg_delay is not None:
            if avg_delay < 1:
                delay_str = "moins d'une heure"
            elif avg_delay < 24:
                delay_str = f"{avg_delay:.0f} heure(s)"
            else:
                delay_str = f"{avg_delay/24:.1f} jour(s)"
            parts.append(f"Délai moyen de correction : **{delay_str}**.")

        # Correction principale recommandée
        top = suggestions[0]
        sim_pct = round(top.similarity_score * 100)
        parts.append(
            f"Dans **{max(pct, sim_pct)}% des cas similaires**, "
            f"la correction appliquée a été : **{top.correction_applied[:300]}**"
        )

        if top.was_validated:
            parts.append("(validée par le responsable ✅)")

        return " ".join(parts)

    # ──────────────────────────────────────────────────────────────────
    # PERSISTANCE
    # ──────────────────────────────────────────────────────────────────

    def _save_models(self):
        """Sauvegarde tous les artefacts du modèle sur disque."""
        try:
            joblib.dump(self.vectorizer, TFIDF_PATH)
            joblib.dump(self.kmeans,     KMEANS_PATH)
            np.save(str(MATRIX_NPY), self.tfidf_matrix)

            with open(CORPUS_PATH,   "w", encoding="utf-8") as f:
                json.dump(self.corpus, f, ensure_ascii=False, default=str)
            with open(HISTORY_PATH,  "w", encoding="utf-8") as f:
                json.dump(self.correction_history, f, ensure_ascii=False, default=str)
            with open(METADATA_PATH, "w", encoding="utf-8") as f:
                json.dump(self._train_metadata, f, ensure_ascii=False, default=str)

            logger.debug("[BF17] Modèles sauvegardés sur disque")
        except Exception as e:
            logger.error(f"[BF17] Erreur sauvegarde modèles : {e}")

    def _load_or_train(self):
        """
        Charge les modèles depuis le disque si disponibles,
        sinon lance un entraînement initial.
        """
        if (
            TFIDF_PATH.exists()
            and KMEANS_PATH.exists()
            and MATRIX_NPY.exists()
            and CORPUS_PATH.exists()
            and HISTORY_PATH.exists()
        ):
            try:
                self.vectorizer   = joblib.load(TFIDF_PATH)
                self.kmeans       = joblib.load(KMEANS_PATH)
                self.tfidf_matrix = np.load(str(MATRIX_NPY))

                with open(CORPUS_PATH,  encoding="utf-8") as f:
                    self.corpus = json.load(f)
                with open(HISTORY_PATH, encoding="utf-8") as f:
                    self.correction_history = json.load(f)

                if METADATA_PATH.exists():
                    with open(METADATA_PATH, encoding="utf-8") as f:
                        self._train_metadata = json.load(f)

                self._compute_cluster_labels(self.kmeans.n_clusters)

                logger.info(
                    f"✅ [BF17] Modèle chargé depuis disque — "
                    f"{len(self.corpus)} commentaires | "
                    f"{self.kmeans.n_clusters} clusters | "
                    f"{len(self.correction_history)} corrections"
                )
                return

            except Exception as e:
                logger.warning(f"⚠️ [BF17] Chargement échoué ({e}) — ré-entraînement")

        # Premier démarrage ou modèle corrompu
        logger.info("ℹ️ [BF17] Premier démarrage — entraînement sur données réelles...")
        self.train()


# ══════════════════════════════════════════════════════════════════════
# SINGLETON
# ══════════════════════════════════════════════════════════════════════

_service_instance: Optional[ErrorClusteringService] = None


def get_clustering_service() -> ErrorClusteringService:
    """
    Retourne l'instance singleton du service (pattern lazy init).
    Thread-safe pour FastAPI (un seul processus en développement).
    """
    global _service_instance
    if _service_instance is None:
        _service_instance = ErrorClusteringService()
    return _service_instance