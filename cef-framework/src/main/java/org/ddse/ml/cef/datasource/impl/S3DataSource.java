package org.ddse.ml.cef.datasource.impl;

import org.ddse.ml.cef.datasource.DataSource;
import org.ddse.ml.cef.datasource.FileMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.util.List;

/**
 * Data source for AWS S3 or MinIO (S3-compatible storage).
 * 
 * Configuration via application.yml:
 * 
 * <pre>
 * cef:
 *   datasource:
 *     s3:
 *       endpoint: http://localhost:9000  # For MinIO
 *       access-key: minioadmin
 *       secret-key: minioadmin
 *       region: us-east-1
 * </pre>
 *
 * @author mrmanna
 */
@Component
@ConditionalOnClass(S3Client.class)
@ConditionalOnBean(S3Client.class)
public class S3DataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(S3DataSource.class);

    private final S3Client s3Client;

    public S3DataSource(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    @Override
    public boolean supports(String uri) {
        return uri.startsWith("s3://") || uri.startsWith("minio://");
    }

    @Override
    public Mono<InputStream> read(String uri) {
        return Mono.fromCallable(() -> {
            S3Uri s3Uri = parseUri(uri);
            log.debug("Reading S3 object: bucket={}, key={}", s3Uri.bucket, s3Uri.key);

            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(s3Uri.bucket)
                    .key(s3Uri.key)
                    .build();

            return (InputStream) s3Client.getObject(request);
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<String> listFiles(String uri) {
        return Mono.fromCallable(() -> {
            S3Uri s3Uri = parseUri(uri);
            log.debug("Listing S3 objects: bucket={}, prefix={}", s3Uri.bucket, s3Uri.key);

            ListObjectsV2Request request = ListObjectsV2Request.builder()
                    .bucket(s3Uri.bucket)
                    .prefix(s3Uri.key)
                    .build();

            ListObjectsV2Response response = s3Client.listObjectsV2(request);

            return response.contents().stream()
                    .map(S3Object::key)
                    .map(key -> "s3://" + s3Uri.bucket + "/" + key)
                    .toList();
        })
                .flatMapMany(Flux::fromIterable)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> exists(String uri) {
        return Mono.fromCallable(() -> {
            S3Uri s3Uri = parseUri(uri);

            try {
                HeadObjectRequest request = HeadObjectRequest.builder()
                        .bucket(s3Uri.bucket)
                        .key(s3Uri.key)
                        .build();

                s3Client.headObject(request);
                return true;
            } catch (NoSuchKeyException e) {
                return false;
            }
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<FileMetadata> getMetadata(String uri) {
        return Mono.fromCallable(() -> {
            S3Uri s3Uri = parseUri(uri);

            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(s3Uri.bucket)
                    .key(s3Uri.key)
                    .build();

            HeadObjectResponse response = s3Client.headObject(request);

            return new FileMetadata(
                    uri,
                    response.contentLength(),
                    response.lastModified(),
                    response.contentType());
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<String> getSupportedSchemes() {
        return List.of("s3", "minio");
    }

    private S3Uri parseUri(String uri) {
        // s3://bucket/key or minio://bucket/key
        String cleaned = uri.replaceFirst("^(s3|minio)://", "");
        int slashIndex = cleaned.indexOf('/');

        if (slashIndex == -1) {
            return new S3Uri(cleaned, "");
        }

        String bucket = cleaned.substring(0, slashIndex);
        String key = cleaned.substring(slashIndex + 1);

        return new S3Uri(bucket, key);
    }

    private static class S3Uri {
        final String bucket;
        final String key;

        S3Uri(String bucket, String key) {
            this.bucket = bucket;
            this.key = key;
        }
    }
}
