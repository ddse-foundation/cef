package org.ddse.ml.cef.parser.impl;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.ddse.ml.cef.parser.DocumentParser;
import org.ddse.ml.cef.parser.ParsedDocument;
import org.ddse.ml.cef.parser.ParserOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for standard PDF files using Apache PDFBox.
 * 
 * For complex structured PDFs (10-50 pages with custom grammar),
 * use AntlrPdfParser instead.
 *
 * @author mrmanna
 */
@Component
public class PdfParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(PdfParser.class);

    @Override
    public boolean supports(String fileExtension) {
        return "pdf".equalsIgnoreCase(fileExtension);
    }

    @Override
    public Mono<ParsedDocument> parse(InputStream inputStream, ParserOptions options) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            log.debug("Parsing PDF document with PDFBox");

            try (PDDocument document = org.apache.pdfbox.Loader.loadPDF(inputStream.readAllBytes())) {
                PDFTextStripper stripper = new PDFTextStripper();
                String text = stripper.getText(document);

                // Split into pages or paragraphs
                String[] pages = text.split("\\f"); // Form feed separates pages

                Map<String, Object> record = new HashMap<>();
                record.put("fullText", text);
                record.put("pageCount", document.getNumberOfPages());
                record.put("pages", List.of(pages));

                ParsedDocument doc = new ParsedDocument(List.of(record), "pdf");
                doc.setParseTimeMs(System.currentTimeMillis() - startTime);
                doc.getMetadata().put("pageCount", document.getNumberOfPages());

                log.debug("Parsed PDF with {} pages", document.getNumberOfPages());
                return doc;

            } catch (IOException e) {
                throw new RuntimeException("Failed to parse PDF", e);
            }
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<String> getSupportedExtensions() {
        return List.of("pdf");
    }
}
