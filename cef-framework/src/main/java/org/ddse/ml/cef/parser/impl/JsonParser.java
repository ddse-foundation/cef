package org.ddse.ml.cef.parser.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.ddse.ml.cef.parser.DocumentParser;
import org.ddse.ml.cef.parser.ParsedDocument;
import org.ddse.ml.cef.parser.ParserOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Parser for JSON files using Jackson.
 *
 * @author mrmanna
 */
@Component
public class JsonParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(JsonParser.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String fileExtension) {
        return "json".equalsIgnoreCase(fileExtension);
    }

    @Override
    public Mono<ParsedDocument> parse(InputStream inputStream, ParserOptions options) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            log.debug("Parsing JSON document");

            // Parse as List<Map> or Map depending on structure
            Object parsed = objectMapper.readValue(inputStream, Object.class);

            List<Map<String, Object>> records;
            if (parsed instanceof List) {
                records = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                records = List.of((Map<String, Object>) parsed);
            } else {
                throw new IllegalArgumentException("JSON must be an object or array of objects");
            }

            ParsedDocument doc = new ParsedDocument(records, "json");
            doc.setParseTimeMs(System.currentTimeMillis() - startTime);

            log.debug("Parsed {} records from JSON", records.size());
            return doc;
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<String> getSupportedExtensions() {
        return List.of("json");
    }
}
