package com.wifak.chatservice.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class WsEnvelope {

    private String type;
    private Object payload;

    public WsEnvelope() {}

    public WsEnvelope(String type, Object payload) {
        this.type = type;
        this.payload = payload;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Object getPayload() { return payload; }
    public void setPayload(Object payload) { this.payload = payload; }

    @Override
    public String toString() {
        return "WsEnvelope{type='" + type + "', payload=" + payload + '}';
    }
}
