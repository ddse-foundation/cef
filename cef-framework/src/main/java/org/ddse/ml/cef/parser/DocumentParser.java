package org.ddse.ml.cef.parser;

import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;

/**
 * Interface for parsing documents into structured data.
 * 
 * Implementations:
 * - YamlParser: YAML files (using SnakeYAML)
 * - JsonParser: JSON files (using Jackson)
 * - CsvParser: CSV files (using OpenCSV)
 * - PdfParser: PDF files (using PDFBox for standard PDFs)
 * - AntlrPdfParser: Complex structured PDFs (using ANTLR grammar, 10-50 pages)
 * 
 * Framework auto-detects parser based on file extension.
 *
 * @author mrmanna
 */
public interface DocumentParser {

    /**
     * Check if this parser supports the given file type.
     */
    boolean supports(String fileExtension);

    /**
     * Parse document into structured data.
     * Returns list of parsed objects (domain-agnostic).
     */
    Mono<ParsedDocument> parse(InputStream inputStream, ParserOptions options);

    /**
     * Get supported file extensions.
     */
    List<String> getSupportedExtensions();
}
