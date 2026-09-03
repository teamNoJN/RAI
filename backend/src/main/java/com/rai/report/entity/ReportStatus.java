package com.rai.report.entity;

/** report.status 허용값. init-db/01_schema.sql 의 CHECK 제약과 일치해야 한다. */
public final class ReportStatus {

    public static final String PENDING = "pending";
    public static final String COMPLETED = "completed";
    public static final String FAILED = "failed";

    private ReportStatus() {
    }
}
