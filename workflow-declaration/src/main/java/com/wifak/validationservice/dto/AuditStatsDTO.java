package com.wifak.validationservice.dto;

import java.util.List;
import java.util.Map;

/**
 * Statistiques complètes pour le tableau de bord auditeur.
 */
public class AuditStatsDTO {

    // ── Déclarations ──────────────────────────────────────────────
    private long totalDeclarations;
    private long generees;
    private long enValidation;
    private long validees;
    private long rejetees;
    private long envoyees;

    // ── Logs ──────────────────────────────────────────────────────
    private long totalLogs;
    private long totalSoumissions;
    private long totalValidations;
    private long totalRejets;
    private long totalEnvois;

    // ── Taux ──────────────────────────────────────────────────────
    private double tauxValidation;   // (validees + envoyees) / total * 100
    private double tauxRejet;        // rejetees / total * 100

    // ── Top utilisateurs ──────────────────────────────────────────
    private List<UserActionCount> topAgents;    // agents les plus actifs
    private List<UserActionCount> topManagers;  // managers les plus actifs

    // ── Répartition par action ────────────────────────────────────
    private Map<String, Long> actionCounts;     // { SUBMIT: 12, VALIDATE: 8, ... }

    public AuditStatsDTO() {}

    // ─── Getters / Setters ───────────────────────────────────────

    public long getTotalDeclarations() { return totalDeclarations; }
    public void setTotalDeclarations(long v) { this.totalDeclarations = v; }

    public long getGenerees() { return generees; }
    public void setGenerees(long v) { this.generees = v; }

    public long getEnValidation() { return enValidation; }
    public void setEnValidation(long v) { this.enValidation = v; }

    public long getValidees() { return validees; }
    public void setValidees(long v) { this.validees = v; }

    public long getRejetees() { return rejetees; }
    public void setRejetees(long v) { this.rejetees = v; }

    public long getEnvoyees() { return envoyees; }
    public void setEnvoyees(long v) { this.envoyees = v; }

    public long getTotalLogs() { return totalLogs; }
    public void setTotalLogs(long v) { this.totalLogs = v; }

    public long getTotalSoumissions() { return totalSoumissions; }
    public void setTotalSoumissions(long v) { this.totalSoumissions = v; }

    public long getTotalValidations() { return totalValidations; }
    public void setTotalValidations(long v) { this.totalValidations = v; }

    public long getTotalRejets() { return totalRejets; }
    public void setTotalRejets(long v) { this.totalRejets = v; }

    public long getTotalEnvois() { return totalEnvois; }
    public void setTotalEnvois(long v) { this.totalEnvois = v; }

    public double getTauxValidation() { return tauxValidation; }
    public void setTauxValidation(double v) { this.tauxValidation = v; }

    public double getTauxRejet() { return tauxRejet; }
    public void setTauxRejet(double v) { this.tauxRejet = v; }

    public List<UserActionCount> getTopAgents() { return topAgents; }
    public void setTopAgents(List<UserActionCount> v) { this.topAgents = v; }

    public List<UserActionCount> getTopManagers() { return topManagers; }
    public void setTopManagers(List<UserActionCount> v) { this.topManagers = v; }

    public Map<String, Long> getActionCounts() { return actionCounts; }
    public void setActionCounts(Map<String, Long> v) { this.actionCounts = v; }

    // ─── Nested DTO ──────────────────────────────────────────────

    public static class UserActionCount {
        private String username;
        private long   count;

        public UserActionCount(String username, long count) {
            this.username = username;
            this.count    = count;
        }

        public String getUsername() { return username; }
        public void setUsername(String v) { this.username = v; }

        public long getCount() { return count; }
        public void setCount(long v) { this.count = v; }
    }
}
