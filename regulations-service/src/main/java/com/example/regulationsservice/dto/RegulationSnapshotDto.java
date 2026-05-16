package com.example.regulationsservice.dto;

import java.time.LocalDateTime;

public class RegulationSnapshotDto {

    private String regulationKey;
    private String regulationName;
    private int version;
    private String sourceUser;
    private LocalDateTime appliedAt;
    private String snapshotData;

    public RegulationSnapshotDto() {
    }

    public RegulationSnapshotDto(String regulationKey, String regulationName, int version, String sourceUser, LocalDateTime appliedAt, String snapshotData) {
        this.regulationKey = regulationKey;
        this.regulationName = regulationName;
        this.version = version;
        this.sourceUser = sourceUser;
        this.appliedAt = appliedAt;
        this.snapshotData = snapshotData;
    }

    public String getRegulationKey() {
        return regulationKey;
    }

    public void setRegulationKey(String regulationKey) {
        this.regulationKey = regulationKey;
    }

    public String getRegulationName() {
        return regulationName;
    }

    public void setRegulationName(String regulationName) {
        this.regulationName = regulationName;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getSourceUser() {
        return sourceUser;
    }

    public void setSourceUser(String sourceUser) {
        this.sourceUser = sourceUser;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getSnapshotData() {
        return snapshotData;
    }

    public void setSnapshotData(String snapshotData) {
        this.snapshotData = snapshotData;
    }
}
