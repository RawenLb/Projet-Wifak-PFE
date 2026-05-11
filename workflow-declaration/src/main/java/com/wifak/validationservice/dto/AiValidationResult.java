package com.wifak.validationservice.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class AiValidationResult {

    // 🔥 accepte valid OU isValid
    @JsonAlias({"isValid", "valid"})
    private boolean valid;

    private int score;
    private List<String> anomalies;
    private String recommendation;

    public AiValidationResult() {}

    public boolean isValid() { return valid; }
    public void setValid(boolean valid) { this.valid = valid; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public List<String> getAnomalies() { return anomalies; }
    public void setAnomalies(List<String> anomalies) { this.anomalies = anomalies; }

    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }
}