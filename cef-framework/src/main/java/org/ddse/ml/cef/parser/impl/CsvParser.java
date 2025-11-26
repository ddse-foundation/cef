package org.ddse.ml.cef.parser.impl;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Parser for CSV files using OpenCSV.
 *
 * @author mrmanna
 */
@Component
public class CsvParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(CsvParser.class);

    @Override
    public boolean supports(String fileExtension) {
        return "csv".equalsIgnoreCase(fileExtension);
    }

    @Override
    public Mono<ParsedDocument> parse(InputStream inputStream, ParserOptions options) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            log.debug("Parsing CSV document");

            boolean skipHeader = options.get(ParserOptions.SKIP_HEADER, true);

            try (CSVReader reader = new CSVReader(new InputStreamReader(inputStream))) {
                List<String[]> allRows = reader.readAll();

                if (allRows.isEmpty()) {
                    log.warn("CSV file is empty");
                    return new ParsedDocument(List.of(), "csv");
                }

                // First row as headers (if skipHeader=true)
                String[] headers = skipHeader ? allRows.get(0) : generateHeaders(allRows.get(0).length);

                int startRow = skipHeader ? 1 : 0;
                List<Map<String, Object>> records = new ArrayList<>();

                for (int i = startRow; i < allRows.size(); i++) {
                    String[] row = allRows.get(i);
                    Map<String, Object> record = new HashMap<>();

                    for (int j = 0; j < Math.min(headers.length, row.length); j++) {
                        record.put(headers[j], row[j]);
                    }

                    records.add(record);
                }

                ParsedDocument doc = new ParsedDocument(records, "csv");
                doc.setParseTimeMs(System.currentTimeMillis() - startTime);

                log.debug("Parsed {} records from CSV", records.size());
                return doc;

            } catch (IOException | CsvException e) {
                throw new RuntimeException("Failed to parse CSV", e);
            }
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<String> getSupportedExtensions() {
        return List.of("csv");
    }

    private String[] generateHeaders(int columnCount) {
        String[] headers = new String[columnCount];
        for (int i = 0; i < columnCount; i++) {
            headers[i] = "column_" + i;
        }
        return headers;
    }
}
