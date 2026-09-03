package com.rai.report.pdf;

import com.rai.report.entity.Report;
import com.rai.report.exception.ReportApiException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 보고서 본문(마크다운)을 PDF 로 그린다 — GET /api/reports/{report_id}/export?format=pdf.
 *
 * 명세 요구: 생성 시각과 "AI 초안 / 사람 검토 필요" 문구가 반드시 들어간다.
 * 그래서 배지는 첫 장 상단 박스로, 같은 문구를 모든 페이지 꼬리말에도 반복해 넣는다 —
 * 한 장만 뽑아 돌려도 초안이라는 사실이 붙어 다녀야 하기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReportPdfRenderer {

    private static final PDRectangle PAGE_SIZE = PDRectangle.A4;
    private static final float MARGIN = 50f;
    private static final float BODY_SIZE = 10.5f;
    private static final float LINE_HEIGHT = 15f;
    private static final float FOOTER_SIZE = 8f;
    private static final float FOOTER_BASELINE = 30f;

    /** 명세 5번: 어떤 상태에서도 숨기지 않는다. */
    private static final String BADGE = "AI 생성 초안 · 제출 전 사람 검토 필요";

    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final PdfFontProvider fontProvider;

    public ReportPdf render(Report report) {
        String generatedAt = GENERATED_AT.format(Instant.now());
        try (PDDocument document = new PDDocument();
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            PDType0Font font = fontProvider.load(document);
            Canvas canvas = new Canvas(document, font);

            canvas.badge(BADGE, "생성 시각 " + generatedAt + " · 버전 " + report.getVersion());
            for (String line : safeContent(report).split("\n", -1)) {
                canvas.markdown(line);
            }
            canvas.close();

            String footer = BADGE + "  ·  생성 " + generatedAt + "  ·  버전 " + report.getVersion();
            drawFooters(document, font, footer);

            document.save(out);
            return new ReportPdf(filename(report), out.toByteArray());

        } catch (IOException e) {
            log.error("PDF 생성 실패: {}", report.getReportId(), e);
            throw new ReportApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "PDF 생성에 실패했습니다. 잠시 후 다시 시도해주세요.");
        }
    }

    private String safeContent(Report report) {
        String content = report.getDraftContent();
        return (content == null || content.isBlank()) ? "(본문이 비어 있습니다)" : content;
    }

    private String filename(Report report) {
        // 한글 파일명은 브라우저·프록시마다 처리가 달라 ASCII 로 고정한다.
        return "report-" + report.getReportId() + "-v" + report.getVersion() + ".pdf";
    }

    private void drawFooters(PDDocument document, PDType0Font font, String footer) throws IOException {
        int total = document.getNumberOfPages();
        for (int i = 0; i < total; i++) {
            PDPage page = document.getPage(i);
            try (PDPageContentStream cs = new PDPageContentStream(
                    document, page, PDPageContentStream.AppendMode.APPEND, true, true)) {
                float width = page.getMediaBox().getWidth();

                cs.setStrokingColor(0.8f, 0.8f, 0.8f);
                cs.setLineWidth(0.5f);
                cs.moveTo(MARGIN, FOOTER_BASELINE + 10);
                cs.lineTo(width - MARGIN, FOOTER_BASELINE + 10);
                cs.stroke();

                cs.setNonStrokingColor(0.4f, 0.4f, 0.4f);
                showText(cs, font, footer, FOOTER_SIZE, MARGIN, FOOTER_BASELINE);

                String pageNo = (i + 1) + " / " + total;
                float pageNoWidth = textWidth(font, pageNo, FOOTER_SIZE);
                showText(cs, font, pageNo, FOOTER_SIZE, width - MARGIN - pageNoWidth, FOOTER_BASELINE);
            }
        }
    }

    private static void showText(PDPageContentStream cs, PDType0Font font, String text,
                                 float size, float x, float y) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private static float textWidth(PDType0Font font, String text, float size) throws IOException {
        return font.getStringWidth(text) / 1000f * size;
    }

    /**
     * 페이지 넘김과 줄바꿈을 처리하는 그리기 커서.
     * 마크다운 파서가 아니라, MockReportDrafter 가 만드는 수준(제목·목록·표·구분선)만 다룬다.
     */
    private final class Canvas {

        private final PDDocument document;
        private final PDType0Font font;
        /** 폰트에 글리프가 없는 문자는 showText 가 예외를 던진다. 문자 단위로 판정을 캐시한다. */
        private final Map<String, Boolean> glyphSupport = new HashMap<>();
        private final float contentWidth;
        private final float bottom;

        private PDPageContentStream stream;
        private float y;

        private Canvas(PDDocument document, PDType0Font font) throws IOException {
            this.document = document;
            this.font = font;
            this.contentWidth = PAGE_SIZE.getWidth() - MARGIN * 2;
            this.bottom = FOOTER_BASELINE + 25;
            newPage();
        }

        /** 첫 장 상단 배지 박스. */
        private void badge(String title, String subtitle) throws IOException {
            float height = 40f;
            float top = y;
            stream.setNonStrokingColor(0.95f, 0.95f, 0.92f);
            stream.addRect(MARGIN, top - height, contentWidth, height);
            stream.fill();
            stream.setNonStrokingColor(0f, 0f, 0f);

            showText(stream, font, sanitize(title), 11f, MARGIN + 10, top - 17);
            stream.setNonStrokingColor(0.35f, 0.35f, 0.35f);
            showText(stream, font, sanitize(subtitle), 8.5f, MARGIN + 10, top - 31);
            stream.setNonStrokingColor(0f, 0f, 0f);

            y = top - height - 20;
        }

        private void markdown(String raw) throws IOException {
            String line = raw.stripTrailing();
            if (line.isBlank()) {
                y -= LINE_HEIGHT * 0.5f;
                return;
            }
            if (line.startsWith("### ")) {
                gap(6);
                write(line.substring(4), 11.5f, 0);
            } else if (line.startsWith("## ")) {
                gap(10);
                write(line.substring(3), 13f, 0);
            } else if (line.startsWith("# ")) {
                gap(4);
                write(line.substring(2), 16f, 0);
            } else if (line.startsWith("---")) {
                rule();
            } else if (isTableSeparator(line)) {
                // |---|---| 구분선은 표 서식이 없으므로 버린다
            } else if (line.startsWith("|")) {
                write(tableRow(line), BODY_SIZE, 6);
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                write("• " + line.substring(2), BODY_SIZE, 10);
            } else {
                write(line, BODY_SIZE, 0);
            }
        }

        private void write(String text, float size, float indent) throws IOException {
            String cleaned = sanitize(stripEmphasis(text));
            for (String piece : wrap(cleaned, size, contentWidth - indent)) {
                float lineHeight = Math.max(LINE_HEIGHT, size * 1.45f);
                if (y - lineHeight < bottom) {
                    newPage();
                }
                showText(stream, font, piece, size, MARGIN + indent, y - size);
                y -= lineHeight;
            }
        }

        private void rule() throws IOException {
            gap(4);
            if (y - LINE_HEIGHT < bottom) {
                newPage();
            }
            stream.setStrokingColor(0.85f, 0.85f, 0.85f);
            stream.setLineWidth(0.5f);
            stream.moveTo(MARGIN, y - 6);
            stream.lineTo(MARGIN + contentWidth, y - 6);
            stream.stroke();
            y -= LINE_HEIGHT;
        }

        private void gap(float height) {
            if (y < PAGE_SIZE.getHeight() - MARGIN) {
                y -= height;
            }
        }

        private void newPage() throws IOException {
            if (stream != null) {
                stream.close();
            }
            PDPage page = new PDPage(PAGE_SIZE);
            document.addPage(page);
            stream = new PDPageContentStream(document, page);
            y = PAGE_SIZE.getHeight() - MARGIN;
        }

        private void close() throws IOException {
            if (stream != null) {
                stream.close();
                stream = null;
            }
        }

        /** 단어 단위 줄바꿈. 공백 없는 한국어 문장은 글자 단위로 끊는다. */
        private List<String> wrap(String text, float size, float maxWidth) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder current = new StringBuilder();
            int lastSpace = -1;

            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                current.append(c);
                if (c == ' ') {
                    lastSpace = current.length() - 1;
                }
                if (current.length() > 1 && textWidth(font, current.toString(), size) > maxWidth) {
                    int cut = lastSpace > 0 ? lastSpace : current.length() - 1;
                    lines.add(current.substring(0, cut).stripTrailing());
                    String tail = current.substring(cut).stripLeading();
                    current = new StringBuilder(tail);
                    lastSpace = -1;
                }
            }
            if (!current.isEmpty()) {
                lines.add(current.toString());
            }
            return lines.isEmpty() ? List.of("") : lines;
        }

        /** 폰트에 없는 글자(이모지 등)를 '?' 로 바꾼다. 그냥 두면 showText 가 통째로 실패한다. */
        private String sanitize(String text) {
            StringBuilder sb = new StringBuilder(text.length());
            text.codePoints().forEach(cp -> {
                if (cp == '\t') {
                    sb.append("    ");
                } else if (cp >= 0x20) {
                    String ch = new String(Character.toChars(cp));
                    sb.append(hasGlyph(ch) ? ch : "?");
                }
            });
            return sb.toString();
        }

        private boolean hasGlyph(String ch) {
            return glyphSupport.computeIfAbsent(ch, key -> {
                try {
                    font.getStringWidth(key);
                    return true;
                } catch (IOException | IllegalArgumentException e) {
                    return false;
                }
            });
        }
    }

    private static String stripEmphasis(String text) {
        return text.replace("**", "").replace("`", "");
    }

    private static boolean isTableSeparator(String line) {
        return line.startsWith("|") && line.chars().allMatch(c -> c == '|' || c == '-' || c == ':' || c == ' ');
    }

    private static String tableRow(String line) {
        String[] cells = line.split("\\|");
        List<String> values = new ArrayList<>();
        for (String cell : cells) {
            String trimmed = cell.trim();
            if (!trimmed.isEmpty()) {
                values.add(trimmed);
            }
        }
        return String.join("   ·   ", values);
    }
}
