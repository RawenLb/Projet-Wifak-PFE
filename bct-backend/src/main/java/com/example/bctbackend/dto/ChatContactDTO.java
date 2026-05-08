package com.example.bctbackend.dto;

import java.time.LocalDateTime;

public class ChatContactDTO {

    private String        id;
    private String        username;
    private String        fullName;
    private String        role;
    private boolean       online;
    private long          unread;
    private String        lastMsg;
    private LocalDateTime lastTime;

    // ── Constructors ──────────────────────────────────────────────

    public ChatContactDTO() {}

    // ── Builder ───────────────────────────────────────────────────

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private String        id;
        private String        username;
        private String        fullName;
        private String        role;
        private boolean       online;
        private long          unread;
        private String        lastMsg;
        private LocalDateTime lastTime;

        public Builder id(String v)              { this.id = v; return this; }
        public Builder username(String v)        { this.username = v; return this; }
        public Builder fullName(String v)        { this.fullName = v; return this; }
        public Builder role(String v)            { this.role = v; return this; }
        public Builder online(boolean v)         { this.online = v; return this; }
        public Builder unread(long v)            { this.unread = v; return this; }
        public Builder lastMsg(String v)         { this.lastMsg = v; return this; }
        public Builder lastTime(LocalDateTime v) { this.lastTime = v; return this; }

        public ChatContactDTO build() {
            ChatContactDTO dto = new ChatContactDTO();
            dto.id       = this.id;
            dto.username = this.username;
            dto.fullName = this.fullName;
            dto.role     = this.role;
            dto.online   = this.online;
            dto.unread   = this.unread;
            dto.lastMsg  = this.lastMsg;
            dto.lastTime = this.lastTime;
            return dto;
        }
    }

    // ── Getters / Setters ─────────────────────────────────────────

    public String getId()                        { return id; }
    public void setId(String v)                  { this.id = v; }

    public String getUsername()                  { return username; }
    public void setUsername(String v)            { this.username = v; }

    public String getFullName()                  { return fullName; }
    public void setFullName(String v)            { this.fullName = v; }

    public String getRole()                      { return role; }
    public void setRole(String v)                { this.role = v; }

    public boolean isOnline()                    { return online; }
    public void setOnline(boolean v)             { this.online = v; }

    public long getUnread()                      { return unread; }
    public void setUnread(long v)                { this.unread = v; }

    public String getLastMsg()                   { return lastMsg; }
    public void setLastMsg(String v)             { this.lastMsg = v; }

    public LocalDateTime getLastTime()           { return lastTime; }
    public void setLastTime(LocalDateTime v)     { this.lastTime = v; }
}
