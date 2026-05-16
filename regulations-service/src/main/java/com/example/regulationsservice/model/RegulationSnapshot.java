package com.example.regulationsservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "regulation_snapshots")
public class RegulationSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "regulation_key", nullable = false)
    private String regulationKey;

    @Column(name = "regulation_name", nullable = false)
    private String regulationName;

    @Column(nullable = false)
    private int version;

    @Lob
    @Column(name = "snapshot_data", nullable = false)
    private String snapshotData;

    @Column(nullable = false)
    private LocalDateTime appliedAt;

    @Column(name = "source_user")
    private String sourceUser;

    public RegulationSnapshot() {
    }

    public RegulationSnapshot(String regulationKey, String regulationName, int version, String snapshotData, LocalDateTime appliedAt, String sourceUser) {
        this.regulationKey = regulationKey;
        this.regulationName = regulationName;
        this.version = version;
        this.snapshotData = snapshotData;
        this.appliedAt = appliedAt;
        this.sourceUser = sourceUser;
    }

    public Long getId() {
        return id;
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

    public String getSnapshotData() {
        return snapshotData;
    }

    public void setSnapshotData(String snapshotData) {
        this.snapshotData = snapshotData;
    }

    public LocalDateTime getAppliedAt() {
        return appliedAt;
    }

    public void setAppliedAt(LocalDateTime appliedAt) {
        this.appliedAt = appliedAt;
    }

    public String getSourceUser() {
        return sourceUser;
    }

    public void setSourceUser(String sourceUser) {
        this.sourceUser = sourceUser;
    }
}
