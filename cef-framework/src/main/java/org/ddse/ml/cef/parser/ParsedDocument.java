package org.ddse.ml.cef.parser;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Result of document parsing.
 * Contains raw parsed data and metadata.
 *
 * @author mrmanna
 */
public class ParsedDocument {

    private List<Map<String, Object>> records;
    private Map<String, Object> metadata;
    private String sourceFile;
    private long parseTimeMs;

    public ParsedDocument() {
        this.metadata = new HashMap<>();
    }

    public ParsedDocument(List<Map<String, Object>> records, String sourceFile) {
        this.records = records;
        this.sourceFile = sourceFile;
        this.metadata = new HashMap<>();
    }

    public List<Map<String, Object>> getRecords() {
        return records;
    }

    public void setRecords(List<Map<String, Object>> records) {
        this.records = records;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public void setMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public String getSourceFile() {
        return sourceFile;
    }

    public void setSourceFile(String sourceFile) {
        this.sourceFile = sourceFile;
    }

    public long getParseTimeMs() {
        return parseTimeMs;
    }

    public void setParseTimeMs(long parseTimeMs) {
        this.parseTimeMs = parseTimeMs;
    }

    public int getRecordCount() {
        return records != null ? records.size() : 0;
    }

    @Override
    public String toString() {
        return "ParsedDocument{" +
                "recordCount=" + getRecordCount() +
                ", sourceFile='" + sourceFile + '\'' +
                ", parseTimeMs=" + parseTimeMs +
                '}';
    }
}
