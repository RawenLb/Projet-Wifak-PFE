package com.wifak.validationservice.dto;

/**
 * Statistiques du workflow de validation.
 */
public class ValidationStatsDTO {

    private long total;
    private long generees;
    private long enValidation;
    private long validees;
    private long rejetees;
    private long envoyees;

    public ValidationStatsDTO() {}

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }

    public long getGenerees() { return generees; }
    public void setGenerees(long generees) { this.generees = generees; }

    public long getEnValidation() { return enValidation; }
    public void setEnValidation(long enValidation) { this.enValidation = enValidation; }

    public long getValidees() { return validees; }
    public void setValidees(long validees) { this.validees = validees; }

    public long getRejetees() { return rejetees; }
    public void setRejetees(long rejetees) { this.rejetees = rejetees; }

    public long getEnvoyees() { return envoyees; }
    public void setEnvoyees(long envoyees) { this.envoyees = envoyees; }
}