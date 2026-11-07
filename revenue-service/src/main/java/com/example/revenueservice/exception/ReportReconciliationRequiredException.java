package com.example.revenueservice.exception;

public class ReportReconciliationRequiredException extends RuntimeException {
    public ReportReconciliationRequiredException() { super("Historical or conflicting cash transactions require reconciliation"); }
}
