package org.ddse.ml.cef.datasource.impl;

import org.ddse.ml.cef.datasource.DataSource;
import org.ddse.ml.cef.datasource.FileMetadata;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;

/**
 * Data source for local file system.
 *
 * @author mrmanna
 */
@Component
public class LocalFileDataSource implements DataSource {

    private static final Logger log = LoggerFactory.getLogger(LocalFileDataSource.class);

    @Override
    public boolean supports(String uri) {
        return uri.startsWith("file://") || !uri.contains("://");
    }

    @Override
    public Mono<InputStream> read(String uri) {
        return Mono.fromCallable(() -> {
            Path path = resolvePath(uri);
            log.debug("Reading local file: {}", path);
            return (InputStream) new FileInputStream(path.toFile());
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<String> listFiles(String uri) {
        return Mono.fromCallable(() -> {
            Path path = resolvePath(uri);
            log.debug("Listing files in: {}", path);

            try (Stream<Path> stream = Files.walk(path)) {
                return stream
                        .filter(Files::isRegularFile)
                        .map(Path::toString)
                        .toList();
            }
        })
                .flatMapMany(Flux::fromIterable)
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> exists(String uri) {
        return Mono.fromCallable(() -> {
            Path path = resolvePath(uri);
            return Files.exists(path);
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<FileMetadata> getMetadata(String uri) {
        return Mono.fromCallable(() -> {
            Path path = resolvePath(uri);

            if (!Files.exists(path)) {
                throw new IOException("File not found: " + uri);
            }

            long size = Files.size(path);
            Instant lastModified = Files.getLastModifiedTime(path).toInstant();
            String contentType = Files.probeContentType(path);

            return new FileMetadata(uri, size, lastModified, contentType);
        })
                .subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public List<String> getSupportedSchemes() {
        return List.of("file");
    }

    private Path resolvePath(String uri) {
        if (uri.startsWith("file://")) {
            return Paths.get(uri.substring(7));
        }
        return Paths.get(uri);
    }
}
