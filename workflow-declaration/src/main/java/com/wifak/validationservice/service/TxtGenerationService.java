package com.wifak.validationservice.service;

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

    public String generateTxtFromSql(String sqlQuery, LocalDate dateDebut, LocalDate dateFin,
                                     String typeCode, String periode) {
        log.info("📄 Génération TXT — Type: {}, Période: {}", typeCode, periode);

        String executableSql = sqlQuery
                .replace(":dateDebut", "'" + dateDebut.toString() + "'")
                .replace(":dateFin",   "'" + dateFin.toString()   + "'");

        List<Map<String, Object>> rows;
        try {
            rows = jdbcTemplate.queryForList(executableSql);
        } catch (Exception e) {
            log.error("❌ Erreur SQL TXT: {}", e.getMessage());
            throw new RuntimeException("Erreur SQL: " + e.getMessage());
        }

        StringBuilder txt = new StringBuilder();
        String delimiter = "|";
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));

        txt.append("DECLARATION BCT - TYPE: ").append(typeCode)
                .append(" - PERIODE: ").append(periode).append("\r\n");
        txt.append("GENERE_LE=").append(timestamp).append("\r\n");
        txt.append("DATE_DEBUT=").append(dateDebut).append("\r\n");
        txt.append("DATE_FIN=").append(dateFin).append("\r\n");
        txt.append("=".repeat(80)).append("\r\n");

        if (rows.isEmpty()) {
            txt.append("AUCUNE_DONNEE\r\n");
            txt.append("=".repeat(80)).append("\r\n");
            txt.append("FIN_DECLARATION\r\n");
            return txt.toString();
        }

        Set<String> columns = rows.get(0).keySet();
        txt.append(String.join(delimiter, columns)).append("\r\n");
        txt.append("-".repeat(80)).append("\r\n");

        int lineNum = 1;
        for (Map<String, Object> row : rows) {
            String line = columns.stream()
                    .map(col -> { Object val = row.get(col); return val != null ? val.toString() : ""; })
                    .collect(Collectors.joining(delimiter));
            txt.append(lineNum++).append(delimiter).append(line).append("\r\n");
        }

        txt.append("=".repeat(80)).append("\r\n");
        txt.append("TOTAL_LIGNES=").append(rows.size()).append("\r\n");
        txt.append("FIN_DECLARATION=").append(LocalDate.now()).append("\r\n");

        log.info("✅ TXT généré — {} colonnes, {} lignes", columns.size(), rows.size());
        return txt.toString();
    }
}
