package org.ddse.ml.cef.datasource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.InputStream;
import java.util.List;

/**
 * Interface for data sources (local files, S3, MinIO, etc.).
 * 
 * Implementations:
 * - LocalFileDataSource: Local file system
 * - S3DataSource: AWS S3 or MinIO (S3-compatible)
 * 
 * Framework auto-detects based on URI scheme:
 * - file:///path/to/file -> LocalFileDataSource
 * - s3://bucket/key -> S3DataSource
 * - minio://bucket/key -> S3DataSource (with MinIO endpoint)
 *
 * @author mrmanna
 */
public interface DataSource {

    /**
     * Check if this data source supports the given URI scheme.
     */
    boolean supports(String uri);

    /**
     * Read file as input stream.
     */
    Mono<InputStream> read(String uri);

    /**
     * List files in directory/bucket.
     */
    Flux<String> listFiles(String uri);

    /**
     * Check if file exists.
     */
    Mono<Boolean> exists(String uri);

    /**
     * Get file metadata.
     */
    Mono<FileMetadata> getMetadata(String uri);

    /**
     * Get supported URI schemes.
     */
    List<String> getSupportedSchemes();
}
