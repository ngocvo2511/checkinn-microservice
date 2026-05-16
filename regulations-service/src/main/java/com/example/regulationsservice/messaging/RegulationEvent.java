package com.example.regulationsservice.messaging;

import java.time.LocalDateTime;

public class RegulationEvent {

    private String regulationKey;
    private String name;
    private String value;
    private boolean active;
    private int version;
    private String changedBy;
    private LocalDateTime changedAt;

    public RegulationEvent() {
    }

    public RegulationEvent(String regulationKey, String name, String value, boolean active, int version, String changedBy, LocalDateTime changedAt) {
        this.regulationKey = regulationKey;
        this.name = name;
        this.value = value;
        this.active = active;
        this.version = version;
        this.changedBy = changedBy;
        this.changedAt = changedAt;
    }

    public String getRegulationKey() {
        return regulationKey;
    }

    public void setRegulationKey(String regulationKey) {
        this.regulationKey = regulationKey;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(LocalDateTime changedAt) {
        this.changedAt = changedAt;
    }
}
