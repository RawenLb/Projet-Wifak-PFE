package com.example.bctbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CsvGenerationService {

    private static final Logger log = LoggerFactory.getLogger(CsvGenerationService.class);

    private final JdbcTemplate jdbcTemplate;

    public CsvGenerationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Génère un fichier CSV à partir d'une requête SQL et d'une plage de dates.
     *
     * @param sqlQuery   La requête SQL contenant :dateDebut et :dateFin
     * @param dateDebut  Date de début de la période
     * @param dateFin    Date de fin de la période
     * @param typeCode   Code du type de déclaration (pour les métadonnées)
     * @param periode    Période au format "yyyy-MM" (pour les métadonnées)
     * @return Contenu CSV sous forme de String
     */
    public String generateCsvFromSql(
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin,
            String typeCode,
            String periode
    ) {
        log.info("📊 Génération CSV — Type: {}, Période: {}, Du: {} Au: {}", typeCode, periode, dateDebut, dateFin);

        // 1. Remplacer les paramètres nommés par les vraies valeurs
        String executableSql = sqlQuery
                .replace(":dateDebut", "'" + dateDebut.toString() + "'")
                .replace(":dateFin",   "'" + dateFin.toString()   + "'");

        // 2. Exécuter la requête
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(executableSql);
        } catch (Exception e) {
            log.error("❌ Erreur exécution SQL pour CSV: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'exécution de la requête SQL: " + e.getMessage());
        }

        log.info("✅ {} ligne(s) récupérée(s) pour le CSV", rows.size());

        // 3. Construire le CSV
        StringBuilder csv = new StringBuilder();
        String separator = ";";

        if (rows.isEmpty()) {
            // Pas de données — retourner un CSV vide avec juste l'en-tête si possible
            log.warn("⚠️ Aucune donnée trouvée pour la période {} → {} ", dateDebut, dateFin);
            csv.append("# Aucune donnée pour la période ").append(periode).append("\n");
            return csv.toString();
        }

        // 4. Ligne d'en-tête (noms des colonnes)
        Set<String> columns = rows.get(0).keySet();
        String header = columns.stream().collect(Collectors.joining(separator));
        csv.append(header).append("\n");

        // 5. Lignes de données
        for (Map<String, Object> row : rows) {
            String line = columns.stream()
                    .map(col -> {
                        Object val = row.get(col);
                        if (val == null) return "";
                        String strVal = val.toString();
                        // Echapper les guillemets et entourer de guillemets si nécessaire
                        if (strVal.contains(separator) || strVal.contains("\"") || strVal.contains("\n")) {
                            strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                        }
                        return strVal;
                    })
                    .collect(Collectors.joining(separator));
            csv.append(line).append("\n");
        }

        log.info("✅ CSV généré — {} colonnes, {} lignes de données", columns.size(), rows.size());
        return csv.toString();
    }

    /**
     * Extrait les colonnes disponibles d'une requête SQL (pour le test).
     */
    public List<String> extractColumnsFromSql(String sqlQuery, LocalDate dateDebut, LocalDate dateFin) {
        String executableSql = sqlQuery
                .replace(":dateDebut", "'" + dateDebut.toString() + "'")
                .replace(":dateFin",   "'" + dateFin.toString()   + "'");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(executableSql + " LIMIT 1");
        if (rows.isEmpty()) return List.of();
        return List.copyOf(rows.get(0).keySet());
    }
}