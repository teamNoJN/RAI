package com.rai.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/** PDF / Text 파일에서 본문 텍스트를 추출한다. */
@Component
public class TextExtractor {

    public String extract(String filename, InputStream input) throws IOException {
        if (filename != null && filename.toLowerCase().endsWith(".pdf")) {
            try (PDDocument document = Loader.loadPDF(input.readAllBytes())) {
                return new PDFTextStripper().getText(document);
            }
        }
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
