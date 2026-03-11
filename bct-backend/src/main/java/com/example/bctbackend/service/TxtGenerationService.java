package com.example.bctbackend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TxtGenerationService {

    private static final Logger log = LoggerFactory.getLogger(TxtGenerationService.class);

    private final JdbcTemplate jdbcTemplate;

    public TxtGenerationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Génère un fichier TXT délimité par pipe (|) à partir d'une requête SQL.
     *
     * @param sqlQuery   La requête SQL contenant :dateDebut et :dateFin
     * @param dateDebut  Date de début de la période
     * @param dateFin    Date de fin de la période
     * @param typeCode   Code du type de déclaration
     * @param periode    Période au format "yyyy-MM"
     * @return Contenu TXT sous forme de String
     */
    public String generateTxtFromSql(
            String sqlQuery,
            LocalDate dateDebut,
            LocalDate dateFin,
            String typeCode,
            String periode
    ) {
        log.info("📄 Génération TXT — Type: {}, Période: {}, Du: {} Au: {}", typeCode, periode, dateDebut, dateFin);

        // 1. Remplacer les paramètres nommés
        String executableSql = sqlQuery
                .replace(":dateDebut", "'" + dateDebut.toString() + "'")
                .replace(":dateFin",   "'" + dateFin.toString()   + "'");

        // 2. Exécuter la requête
        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(executableSql);
        } catch (Exception e) {
            log.error("❌ Erreur exécution SQL pour TXT: {}", e.getMessage());
            throw new RuntimeException("Erreur lors de l'exécution de la requête SQL: " + e.getMessage());
        }

        log.info("✅ {} ligne(s) récupérée(s) pour le TXT", rows.size());

        // 3. Construire le TXT
        StringBuilder txt = new StringBuilder();
        String delimiter = "|";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        // En-tête du fichier
        txt.append("DECLARATION BCT - TYPE: ").append(typeCode)
                .append(" - PERIODE: ").append(periode).append("\r\n");
        txt.append("GENERE_LE=").append(timestamp).append("\r\n");
        txt.append("DATE_DEBUT=").append(dateDebut).append("\r\n");
        txt.append("DATE_FIN=").append(dateFin).append("\r\n");
        txt.append("=".repeat(80)).append("\r\n");

        if (rows.isEmpty()) {
            log.warn("⚠️ Aucune donnée trouvée pour la période {} → {}", dateDebut, dateFin);
            txt.append("AUCUNE_DONNEE\r\n");
            txt.append("=".repeat(80)).append("\r\n");
            txt.append("FIN_DECLARATION\r\n");
            return txt.toString();
        }

        // Colonnes
        Set<String> columns = rows.get(0).keySet();

        // Ligne d'en-tête des colonnes
        txt.append(String.join(delimiter, columns)).append("\r\n");
        txt.append("-".repeat(80)).append("\r\n");

        // Lignes de données
        int lineNum = 1;
        for (Map<String, Object> row : rows) {
            String line = columns.stream()
                    .map(col -> {
                        Object val = row.get(col);
                        return val != null ? val.toString() : "";
                    })
                    .collect(Collectors.joining(delimiter));
            txt.append(lineNum++).append(delimiter).append(line).append("\r\n");
        }

        // Pied de page
        txt.append("=".repeat(80)).append("\r\n");
        txt.append("TOTAL_LIGNES=").append(rows.size()).append("\r\n");
        txt.append("FIN_DECLARATION=").append(LocalDate.now()).append("\r\n");

        log.info("✅ TXT généré — {} colonnes, {} lignes de données", columns.size(), rows.size());
        return txt.toString();
    }
}