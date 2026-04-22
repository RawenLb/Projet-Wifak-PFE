package com.example.bctbackend.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

/**
 * DTO pour le mapping XSD ↔ SQL.
 * Utilisé par :
 *  - POST /api/declarations/generate-with-mapping
 *  - stockage JSON dans Declaration.mappingJson
 */
public class XsdSqlMappingRequest {

    // ══════════════════════════════════════════════════════════════
    // ENUM — source du champ (SQL dynamique, valeur statique, ignoré)
    // ✅ @JsonCreator insensible à la casse pour la désérialisation
    // ══════════════════════════════════════════════════════════════

    public enum MappingSource {
        SQL,
        STATIC,
        NONE;

        /**
         * Désérialise "SQL", "sql", "Sql" → MappingSource.SQL
         * Désérialise "STATIC", "static" → MappingSource.STATIC
         * Désérialise "NONE", "none"   → MappingSource.NONE
         * null ou valeur inconnue      → NONE (défaut sûr)
         */
        @JsonCreator
        public static MappingSource fromJson(String value) {
            if (value == null || value.trim().isEmpty()) {
                return NONE;
            }
            try {
                return MappingSource.valueOf(value.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                return NONE; // Valeur inconnue → NONE plutôt que NPE
            }
        }

        @JsonValue
        public String toJson() {
            return this.name(); // Sérialise en "SQL", "STATIC", "NONE"
        }
    }

    // ══════════════════════════════════════════════════════════════
    // DTO FIELDMAPPING — un champ XSD avec sa source de valeur
    // ══════════════════════════════════════════════════════════════

    public static class FieldMapping {

        @JsonProperty("xsdFieldName")
        private String xsdFieldName;

        @JsonProperty("xsdFieldPath")
        private String xsdFieldPath;

        @JsonProperty("xsdType")
        private String xsdType;

        @JsonProperty("required")
        private boolean required;

        @JsonProperty("source")
        private MappingSource source = MappingSource.NONE; // ✅ Valeur par défaut sûre

        @JsonProperty("sqlColumn")
        private String sqlColumn;

        @JsonProperty("staticValue")
        private String staticValue;

        // ── Constructeur no-arg obligatoire pour Jackson ──────────
        public FieldMapping() {}

        // ── Constructeur complet ───────────────────────────────────
        public FieldMapping(String xsdFieldName, String xsdFieldPath, String xsdType,
                            boolean required, MappingSource source,
                            String sqlColumn, String staticValue) {
            this.xsdFieldName = xsdFieldName;
            this.xsdFieldPath = xsdFieldPath;
            this.xsdType      = xsdType;
            this.required     = required;
            this.source       = source != null ? source : MappingSource.NONE;
            this.sqlColumn    = sqlColumn;
            this.staticValue  = staticValue;
        }

        // ── Getters ───────────────────────────────────────────────

        public String getXsdFieldName()  { return xsdFieldName; }
        public String getXsdFieldPath()  { return xsdFieldPath; }
        public String getXsdType()       { return xsdType; }
        public boolean isRequired()      { return required; }

        /**
         * ✅ Ne retourne jamais null — garantit l'absence de NPE
         * dans les comparaisons getSource() == MappingSource.SQL
         */
        public MappingSource getSource() {
            return source != null ? source : MappingSource.NONE;
        }

        public String getSqlColumn()    { return sqlColumn    != null ? sqlColumn    : ""; }
        public String getStaticValue()  { return staticValue  != null ? staticValue  : ""; }

        // ── Setters ───────────────────────────────────────────────

        public void setXsdFieldName(String v)  { this.xsdFieldName = v; }
        public void setXsdFieldPath(String v)  { this.xsdFieldPath = v; }
        public void setXsdType(String v)       { this.xsdType = v; }
        public void setRequired(boolean v)     { this.required = v; }
        public void setSource(MappingSource v) { this.source = v != null ? v : MappingSource.NONE; }
        public void setSqlColumn(String v)     { this.sqlColumn = v; }
        public void setStaticValue(String v)   { this.staticValue = v; }

        @Override
        public String toString() {
            return String.format("FieldMapping{name='%s', source=%s, sqlCol='%s', static='%s', required=%s}",
                    xsdFieldName, source, sqlColumn, staticValue, required);
        }
    }

    // ══════════════════════════════════════════════════════════════
    // RACINE DU DTO (si utilisé comme corps de requête complet)
    // ══════════════════════════════════════════════════════════════

    private List<FieldMapping> mappings;

    public List<FieldMapping> getMappings()          { return mappings; }
    public void setMappings(List<FieldMapping> v)    { this.mappings = v; }
}