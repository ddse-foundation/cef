package org.ddse.ml.cef.parser;

import java.util.HashMap;
import java.util.Map;

/**
 * Options for document parsing.
 * Allows customization of parser behavior.
 *
 * @author mrmanna
 */
public class ParserOptions {

    private Map<String, Object> options = new HashMap<>();

    public ParserOptions() {
    }

    public void set(String key, Object value) {
        options.put(key, value);
    }

    public Object get(String key) {
        return options.get(key);
    }

    public <T> T get(String key, T defaultValue) {
        Object value = options.get(key);
        return value != null ? (T) value : defaultValue;
    }

    public Map<String, Object> getAll() {
        return options;
    }

    // Common option keys
    public static final String SKIP_HEADER = "skipHeader";
    public static final String DELIMITER = "delimiter";
    public static final String ENCODING = "encoding";
    public static final String MAX_RECORDS = "maxRecords";
    public static final String ANTLR_GRAMMAR = "antlrGrammar";
}
