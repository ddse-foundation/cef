package org.ddse.ml.cef.datasource;

import java.time.Instant;

/**
 * Metadata for a file from a data source.
 *
 * @author mrmanna
 */
public class FileMetadata {

    private String uri;
    private long size;
    private Instant lastModified;
    private String contentType;

    public FileMetadata() {
    }

    public FileMetadata(String uri, long size, Instant lastModified, String contentType) {
        this.uri = uri;
        this.size = size;
        this.lastModified = lastModified;
        this.contentType = contentType;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Instant getLastModified() {
        return lastModified;
    }

    public void setLastModified(Instant lastModified) {
        this.lastModified = lastModified;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    @Override
    public String toString() {
        return "FileMetadata{" +
                "uri='" + uri + '\'' +
                ", size=" + size +
                ", lastModified=" + lastModified +
                ", contentType='" + contentType + '\'' +
                '}';
    }
}
