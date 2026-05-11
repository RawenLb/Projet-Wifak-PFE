package com.wifak.validationservice.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.List;

public class XsdSqlMappingRequest {

    public enum MappingSource {
        SQL, STATIC, NONE;

        @JsonCreator
        public static MappingSource fromJson(String value) {
            if (value == null || value.trim().isEmpty()) return NONE;
            try { return MappingSource.valueOf(value.trim().toUpperCase()); }
            catch (IllegalArgumentException e) { return NONE; }
        }

        @JsonValue
        public String toJson() { return this.name(); }
    }

    public static class FieldMapping {

        @JsonProperty("xsdFieldName")  private String xsdFieldName;
        @JsonProperty("xsdFieldPath")  private String xsdFieldPath;
        @JsonProperty("xsdType")       private String xsdType;
        @JsonProperty("required")      private boolean required;
        @JsonProperty("source")        private MappingSource source = MappingSource.NONE;
        @JsonProperty("sqlColumn")     private String sqlColumn;
        @JsonProperty("staticValue")   private String staticValue;

        public FieldMapping() {}

        public FieldMapping(String xsdFieldName, String xsdFieldPath, String xsdType,
                            boolean required, MappingSource source,
                            String sqlColumn, String staticValue) {
            this.xsdFieldName = xsdFieldName; this.xsdFieldPath = xsdFieldPath;
            this.xsdType = xsdType; this.required = required;
            this.source = source != null ? source : MappingSource.NONE;
            this.sqlColumn = sqlColumn; this.staticValue = staticValue;
        }

        public String getXsdFieldName()  { return xsdFieldName; }
        public String getXsdFieldPath()  { return xsdFieldPath; }
        public String getXsdType()       { return xsdType; }
        public boolean isRequired()      { return required; }
        public MappingSource getSource() { return source != null ? source : MappingSource.NONE; }
        public String getSqlColumn()     { return sqlColumn != null ? sqlColumn : ""; }
        public String getStaticValue()   { return staticValue != null ? staticValue : ""; }

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

    private List<FieldMapping> mappings;
    public List<FieldMapping> getMappings()       { return mappings; }
    public void setMappings(List<FieldMapping> v) { this.mappings = v; }
}
