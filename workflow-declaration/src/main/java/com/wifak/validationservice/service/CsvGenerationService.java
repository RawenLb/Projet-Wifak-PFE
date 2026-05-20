package com.wifak.validationservice.service;

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

    public String generateCsvFromSql(String sqlQuery, LocalDate dateDebut, LocalDate dateFin,
                                     String typeCode, String periode) {
        log.info("📊 Génération CSV — Type: {}, Période: {}", typeCode, periode);

        String executableSql = sqlQuery
                .replace(":dateDebut", "'" + dateDebut.toString() + "'")
                .replace(":dateFin",   "'" + dateFin.toString()   + "'");

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(executableSql);
        } catch (Exception e) {
            log.error("❌ Erreur SQL CSV: {}", e.getMessage());
            throw new RuntimeException("Erreur SQL: " + e.getMessage());
        }

        StringBuilder csv = new StringBuilder();
        String separator = ";";

        if (rows.isEmpty()) {
            csv.append("# Aucune donnée pour la période ").append(periode).append("\n");
            return csv.toString();
        }

        Set<String> columns = rows.get(0).keySet();
        csv.append(columns.stream().collect(Collectors.joining(separator))).append("\n");

        for (Map<String, Object> row : rows) {
            String line = columns.stream().map(col -> {
                Object val = row.get(col);
                if (val == null) return "";
                String strVal = val.toString();
                if (strVal.contains(separator) || strVal.contains("\"") || strVal.contains("\n")) {
                    strVal = "\"" + strVal.replace("\"", "\"\"") + "\"";
                }
                return strVal;
            }).collect(Collectors.joining(separator));
            csv.append(line).append("\n");
        }

        log.info("✅ CSV généré — {} colonnes, {} lignes", columns.size(), rows.size());
        return csv.toString();
    }

    public List<String> extractColumnsFromSql(String sqlQuery, LocalDate dateDebut, LocalDate dateFin) {
        String executableSql = sqlQuery
                .replace(":dateDebut", "'" + dateDebut.toString() + "'")
                .replace(":dateFin",   "'" + dateFin.toString()   + "'");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(executableSql + " LIMIT 1");
        if (rows.isEmpty()) return List.of();
        return List.copyOf(rows.get(0).keySet());
    }
}
