package com.example.regulationsservice.dto;

import java.math.BigDecimal;

public class CommissionRateDto {

    private BigDecimal commissionRate;

    public CommissionRateDto() {
    }

    public CommissionRateDto(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }

    public BigDecimal getCommissionRate() {
        return commissionRate;
    }

    public void setCommissionRate(BigDecimal commissionRate) {
        this.commissionRate = commissionRate;
    }
}
