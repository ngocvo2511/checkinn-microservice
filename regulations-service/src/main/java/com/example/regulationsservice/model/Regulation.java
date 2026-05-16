package com.example.regulationsservice.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "regulations", uniqueConstraints = @UniqueConstraint(columnNames = "regulation_key"))
public class Regulation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "regulation_key", nullable = false, unique = true)
    private String regulationKey;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String value;

    @Column(length = 2000)
    private String description;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private int version = 1;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    public Regulation() {
    }

    public Regulation(String regulationKey, String name, String value, String description, boolean active, int version) {
        this.regulationKey = regulationKey;
        this.name = name;
        this.value = value;
        this.description = description;
        this.active = active;
        this.version = version;
    }

    @PrePersist
    protected void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
