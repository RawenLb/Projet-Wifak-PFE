package com.example.bctbackend.dto;

import java.util.Map;

/**
 * ✅ DTO pour la génération de déclaration
 */
public class GenerateDeclarationRequest {

    private Long declarationTypeId;
    private String periode;
    private Map<String, String> data;

    // Constructeurs
    public GenerateDeclarationRequest() {}

    public GenerateDeclarationRequest(Long declarationTypeId, String periode, Map<String, String> data) {
        this.declarationTypeId = declarationTypeId;
        this.periode = periode;
        this.data = data;
    }

    // Getters et Setters
    public Long getDeclarationTypeId() {
        return declarationTypeId;
    }

    public void setDeclarationTypeId(Long declarationTypeId) {
        this.declarationTypeId = declarationTypeId;
    }

    public String getPeriode() {
        return periode;
    }

    public void setPeriode(String periode) {
        this.periode = periode;
    }

    public Map<String, String> getData() {
        return data;
    }

    public void setData(Map<String, String> data) {
        this.data = data;
    }

    @Override
    public String toString() {
        return "GenerateDeclarationRequest{" +
                "declarationTypeId=" + declarationTypeId +
                ", periode='" + periode + '\'' +
                ", data=" + data +
                '}';
    }
}