package com.example.bctbackend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Generic WebSocket envelope exchanged between client and server.
 * type: MESSAGE | TYPING | PRESENCE | READ | HISTORY | HISTORY_REQUEST
 *       CALL_OFFER | CALL_ANSWER | CALL_REJECT | CALL_END | CALL_BUSY | ICE_CANDIDATE
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class WsEnvelope {

    private String type;
    private Object payload;

    // ── Constructors ──────────────────────────────────────────────

    public WsEnvelope() {}

    public WsEnvelope(String type, Object payload) {
        this.type    = type;
        this.payload = payload;
    }

    // ── Getters / Setters ─────────────────────────────────────────

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public Object getPayload() {
        return payload;
    }

    public void setPayload(Object payload) {
        this.payload = payload;
    }

    // ── toString ──────────────────────────────────────────────────

    @Override
    public String toString() {
        return "WsEnvelope{type='" + type + "', payload=" + payload + '}';
    }
}
