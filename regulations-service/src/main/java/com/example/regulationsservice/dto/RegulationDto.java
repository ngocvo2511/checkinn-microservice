package com.example.regulationsservice.dto;

import jakarta.validation.constraints.NotBlank;

public class RegulationDto {

    @NotBlank
    private String regulationKey;

    @NotBlank
    private String name;

    @NotBlank
    @ValidRegulationValue
    private String value;

    private String description;
    private boolean active = true;
    private int version;

    public RegulationDto() {
    }

    public RegulationDto(String regulationKey, String name, String value, String description, boolean active, int version) {
        this.regulationKey = regulationKey;
        this.name = name;
        this.value = value;
        this.description = description;
        this.active = active;
        this.version = version;
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
}
