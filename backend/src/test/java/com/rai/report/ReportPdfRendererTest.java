package com.rai.report;

import com.rai.report.entity.Report;
import com.rai.report.entity.ReportStatus;
import com.rai.report.pdf.PdfFontProvider;
import com.rai.report.pdf.ReportPdf;
import com.rai.report.pdf.ReportPdfRenderer;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PDF 내보내기 — 한글이 깨지지 않고 배지·생성시각이 들어가는지. */
class ReportPdfRendererTest {

    private PdfFontProvider fontProvider;
    private ReportPdfRenderer renderer;

    @BeforeEach
    void setUp() {
        fontProvider = new PdfFontProvider("");
        renderer = new ReportPdfRenderer(fontProvider);
        // 한글 폰트가 없는 환경(폰트 미설치 CI)에서는 내보내기 자체가 불가능하다.
        Assumptions.assumeTrue(fontProvider.isAvailable(), "한글 폰트가 없어 PDF 렌더링을 건너뜁니다");
    }

    @Test
    void rendersKoreanBodyWithBadgeAndVersion() throws IOException {
        Report report = Report.builder()
                .reportId(UUID.randomUUID())
                .conversationId(UUID.randomUUID())
                .requestId("req_001")
                .status(ReportStatus.COMPLETED)
                .version(3)
                .draftContent("""
                        # 수출 적합성 검토 보고서

                        ## 1. 요약
                        베트남 수출은 **조건부 가능**합니다.

                        ## 2. 성분별 검토

                        | 성분 | 판정 |
                        |---|---|
                        | Amoxicillin | CONDITIONAL |

                        - 등록 서류 보완이 필요합니다
                        """)
                .build();

        ReportPdf pdf = renderer.render(report);

        assertThat(pdf.filename()).isEqualTo("report-" + report.getReportId() + "-v3.pdf");
        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            String text = new PDFTextStripper().getText(document);
            assertThat(text)
                    .contains("수출 적합성 검토 보고서")
                    .contains("베트남 수출은 조건부 가능합니다")   // ** 강조 기호는 제거된다
                    .contains("Amoxicillin")
                    .contains("AI 생성 초안")                      // 배지 + 꼬리말
                    .contains("생성 시각")
                    .contains("버전 3");
            assertThat(text).doesNotContain("|---|");             // 표 구분선은 렌더하지 않는다
        }
    }

    @Test
    void paginatesLongContentAndKeepsBadgeOnEveryPage() throws IOException {
        StringBuilder body = new StringBuilder("# 긴 보고서\n\n");
        for (int i = 0; i < 200; i++) {
            body.append("- 항목 ").append(i).append(" 규제 요구사항에 대한 설명 문장입니다.\n");
        }

        ReportPdf pdf = renderer.render(Report.builder()
                .reportId(UUID.randomUUID()).conversationId(UUID.randomUUID())
                .requestId("req_002").status(ReportStatus.COMPLETED).version(1)
                .draftContent(body.toString()).build());

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
            PDFTextStripper stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                assertThat(stripper.getText(document)).contains("AI 생성 초안");
            }
        }
    }

    @Test
    void replacesGlyphsMissingFromTheFontInsteadOfFailing() throws IOException {
        ReportPdf pdf = renderer.render(Report.builder()
                .reportId(UUID.randomUUID()).conversationId(UUID.randomUUID())
                .requestId("req_003").status(ReportStatus.COMPLETED).version(1)
                .draftContent("판정 결과 🚀 확인").build());   // 이모지

        try (PDDocument document = Loader.loadPDF(pdf.content())) {
            assertThat(new PDFTextStripper().getText(document)).contains("판정 결과").contains("확인");
        }
    }
}
