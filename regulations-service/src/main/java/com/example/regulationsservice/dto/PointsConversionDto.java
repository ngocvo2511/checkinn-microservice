package com.example.regulationsservice.dto;

import java.math.BigDecimal;

public class PointsConversionDto {

    private BigDecimal earnConversionRate;
    private BigDecimal redemptionConversionRate;

    public PointsConversionDto() {
    }

    public PointsConversionDto(BigDecimal earnConversionRate, BigDecimal redemptionConversionRate) {
        this.earnConversionRate = earnConversionRate;
        this.redemptionConversionRate = redemptionConversionRate;
    }

    public BigDecimal getEarnConversionRate() {
        return earnConversionRate;
    }

    public void setEarnConversionRate(BigDecimal earnConversionRate) {
        this.earnConversionRate = earnConversionRate;
    }

    public BigDecimal getRedemptionConversionRate() {
        return redemptionConversionRate;
    }

    public void setRedemptionConversionRate(BigDecimal redemptionConversionRate) {
        this.redemptionConversionRate = redemptionConversionRate;
    }
}
