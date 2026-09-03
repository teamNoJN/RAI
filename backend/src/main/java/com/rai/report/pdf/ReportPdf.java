package com.rai.report.pdf;

/** 내보내기 결과 — 파일명과 바이트. */
public record ReportPdf(String filename, byte[] content) {
}
