package com.example.revenueservice.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record RevenuePoint(LocalDate periodStart, BigDecimal amount) {}
