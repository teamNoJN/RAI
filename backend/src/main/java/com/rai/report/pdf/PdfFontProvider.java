package com.rai.report.pdf;

import lombok.extern.slf4j.Slf4j;
import org.apache.fontbox.ttf.OpenTypeFont;
import org.apache.fontbox.ttf.TTFParser;
import org.apache.fontbox.ttf.TrueTypeFont;
import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * PDF 내보내기에 쓸 한글 폰트를 찾아 문서에 임베딩한다.
 *
 * PDFBox 내장 14 폰트에는 한글 글리프가 없다. 본문이 한국어이므로 CJK 폰트 파일이 반드시 필요하고,
 * 어디서 찾을지는 실행 환경마다 다르다. 그래서 아래 순서로 탐색하고, 실패하면 다음 후보로 넘어간다.
 *   1. classpath {@code /fonts/NanumGothic.ttf} — 폰트를 저장소에 같이 넣는 경우 (배포 환경 권장)
 *   2. {@code rai.report.pdf.font-path} 설정값 — 환경변수로 지정하는 경우
 *   3. 리눅스 배포판·macOS 의 알려진 설치 경로
 *
 * <p><b>TrueType(.ttf) 만 쓴다.</b> PDFBox 는 CFF 아웃라인(.otf · Noto Sans CJK · AppleSDGothicNeo 같은
 * .ttc)을 서브셋 임베딩하지 못해 저장 시점에 깨진다. 서브셋 없이 통째로 넣으면 PDF 1건이 십수 MB 가 되므로
 * 후보에서 아예 제외하고, 잘못 지정된 경우 여기서 사유를 남기고 건너뛴다.
 */
@Slf4j
@Component
public class PdfFontProvider {

    private static final String CLASSPATH_FONT = "/fonts/NanumGothic.ttf";

    private static final List<Path> SYSTEM_CANDIDATES = List.of(
            Path.of("/usr/share/fonts/nanum/NanumGothic.ttf"),            // alpine font-nanum
            Path.of("/usr/share/fonts/truetype/nanum/NanumGothic.ttf"),   // debian fonts-nanum
            Path.of("/System/Library/Fonts/Supplemental/Arial Unicode.ttf")); // macOS 개발 머신

    private final String configuredPath;
    private volatile Boolean available;

    public PdfFontProvider(@Value("${rai.report.pdf.font-path:}") String configuredPath) {
        this.configuredPath = configuredPath;
    }

    /** 임베딩 가능한 한글 폰트가 이 환경에 있는지. (없어도 내보내기만 실패하고 나머지 API 는 정상이다) */
    public boolean isAvailable() {
        Boolean cached = available;
        if (cached != null) {
            return cached;
        }
        try (PDDocument probe = new PDDocument()) {
            load(probe);
            available = true;
        } catch (IOException e) {
            log.warn("PDF 내보내기용 한글 폰트를 준비하지 못했습니다: {}", e.getMessage());
            available = false;
        }
        return available;
    }

    /** 서브셋 임베딩 — 실제 사용한 글리프만 넣는다. CJK 폰트를 통째로 넣으면 파일이 수십 MB 가 된다. */
    public PDType0Font load(PDDocument document) throws IOException {
        List<String> failures = new ArrayList<>();

        try (InputStream bundled = getClass().getResourceAsStream(CLASSPATH_FONT)) {
            if (bundled != null) {
                return embed(document, bundled.readAllBytes());
            }
        } catch (IOException e) {
            failures.add("classpath:" + CLASSPATH_FONT + " — " + e.getMessage());
        }

        for (Path path : candidatePaths()) {
            try {
                return embed(document, Files.readAllBytes(path));
            } catch (IOException e) {
                failures.add(path + " — " + e.getMessage());
            }
        }

        throw new IOException("임베딩 가능한 한글 TrueType 폰트를 찾지 못했습니다. "
                + "classpath:" + CLASSPATH_FONT + " 에 폰트를 넣거나 rai.report.pdf.font-path 로 지정하세요. "
                + "시도: " + (failures.isEmpty() ? SYSTEM_CANDIDATES.toString() : failures.toString()));
    }

    /** 폰트 바이트는 문서를 저장할 때까지 살아 있어야 하므로 파일이 아니라 메모리 버퍼로 읽어 넘긴다. */
    private PDType0Font embed(PDDocument document, byte[] fontBytes) throws IOException {
        TrueTypeFont font = new TTFParser().parse(new RandomAccessReadBuffer(fontBytes));
        if (font instanceof OpenTypeFont openType && openType.isPostScript()) {
            font.close();
            throw new IOException("CFF(OTF) 아웃라인 폰트는 서브셋 임베딩을 지원하지 않습니다. TrueType 폰트가 필요합니다");
        }
        return PDType0Font.load(document, font, true);
    }

    private List<Path> candidatePaths() {
        List<Path> paths = new ArrayList<>();
        if (!configuredPath.isBlank()) {
            Path configured = Path.of(configuredPath);
            if (Files.isReadable(configured)) {
                paths.add(configured);
            } else {
                log.warn("rai.report.pdf.font-path 를 읽을 수 없습니다: {}", configured);
            }
        }
        SYSTEM_CANDIDATES.stream().filter(Files::isReadable).forEach(paths::add);
        return paths;
    }
}
