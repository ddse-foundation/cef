package org.ddse.ml.cef.parser.impl;

import org.ddse.ml.cef.parser.DocumentParser;
import org.ddse.ml.cef.parser.ParsedDocument;
import org.ddse.ml.cef.parser.ParserOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

/**
 * Parser for YAML files using SnakeYAML.
 *
 * @author mrmanna
 */
@Component
public class YamlParser implements DocumentParser {

    private static final Logger log = LoggerFactory.getLogger(YamlParser.class);
    private final Yaml yaml = new Yaml();

    @Override
    public boolean supports(String fileExtension) {
        return "yaml".equalsIgnoreCase(fileExtension) || "yml".equalsIgnoreCase(fileExtension);
    }

    @Override
    public Mono<ParsedDocument> parse(InputStream inputStream, ParserOptions options) {
        return Mono.fromCallable(() -> {
            long startTime = System.currentTimeMillis();

            log.debug("Parsing YAML document");

            // Parse YAML
            Object parsed = yaml.load(new InputStreamReader(inputStream));

            List<Map<String, Object>> records;
            if (parsed instanceof List) {
                records = (List<Map<String, Object>>) parsed;
            } else if (parsed instanceof Map) {
                records = List.of((Map<String, Object>) parsed);
            } else {
                throw new IllegalArgumentException("YAML must be an object or array of objects");
            }

            ParsedDocument doc = new ParsedDocument(records, "yaml");
            doc.setParseTimeMs(System.currentTimeMillis() - startTime);

            log.debug("Parsed {} records from YAML", records.size());
            return doc;
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<String> getSupportedExtensions() {
        return List.of("yaml", "yml");
    }
}
