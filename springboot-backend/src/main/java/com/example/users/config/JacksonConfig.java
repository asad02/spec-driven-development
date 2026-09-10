package com.example.users.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * Trims every incoming String before validation, so BR-2 holds without a .trim()
 * in every service method and "   " correctly fails @NotBlank.
 *
 * <p>Written against Jackson 3 (tools.jackson), which Spring Boot 4 ships — this
 * is not portable from the Micronaut service's Jackson 2 equivalent.
 */
@Configuration
public class JacksonConfig {

    @Bean
    JsonMapperBuilderCustomizer trimmingCustomizer() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(String.class, new TrimmingStringDeserializer());
        return builder -> builder
                .addModule(module)
                // BR-5: a client echoing back id/createdAt is ignored, not rejected.
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    static final class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

        TrimmingStringDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            String value = parser.getString();
            return value == null ? null : value.trim();
        }
    }
}
