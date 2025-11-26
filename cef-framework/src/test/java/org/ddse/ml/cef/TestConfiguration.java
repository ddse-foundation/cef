package org.ddse.ml.cef;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.r2dbc.postgresql.codec.Json;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;
import org.springframework.data.convert.WritingConverter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;

import java.util.Arrays;
import java.util.Map;

/**
 * Test configuration for PostgreSQL R2DBC repository tests with JSONB support.
 * Only active when 'duckdb' profile is NOT active.
 * 
 * @author mrmanna
 */
@SpringBootApplication
@EnableR2dbcRepositories
@Profile("!duckdb")
public class TestConfiguration {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(
                org.springframework.data.r2dbc.dialect.PostgresDialect.INSTANCE,
                Arrays.asList(
                        new MapToJsonConverter(),
                        new JsonToMapConverter(),
                        new VectorToFloatArrayConverter(),
                        new FloatArrayToVectorConverter()));
    }

    @WritingConverter
    public static class MapToJsonConverter implements Converter<Map<String, Object>, Json> {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        public Json convert(Map<String, Object> source) {
            try {
                return Json.of(objectMapper.writeValueAsBytes(source));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException("Failed to convert Map to JSON", e);
            }
        }
    }

    @ReadingConverter
    public static class JsonToMapConverter implements Converter<Json, Map<String, Object>> {
        private static final ObjectMapper objectMapper = new ObjectMapper();

        @Override
        @SuppressWarnings("unchecked")
        public Map<String, Object> convert(Json source) {
            try {
                return objectMapper.readValue(source.asArray(), Map.class);
            } catch (Exception e) {
                throw new IllegalStateException("Failed to convert JSON to Map", e);
            }
        }
    }

    @ReadingConverter
    public static class VectorToFloatArrayConverter implements Converter<io.r2dbc.postgresql.codec.Vector, float[]> {
        @Override
        public float[] convert(io.r2dbc.postgresql.codec.Vector source) {
            // Vector.getVector() returns float[], just return it
            return source.getVector();
        }
    }

    @WritingConverter
    public static class FloatArrayToVectorConverter implements Converter<float[], io.r2dbc.postgresql.codec.Vector> {
        @Override
        public io.r2dbc.postgresql.codec.Vector convert(float[] source) {
            // Vector.of() takes float varargs
            return io.r2dbc.postgresql.codec.Vector.of(source);
        }
    }
}
