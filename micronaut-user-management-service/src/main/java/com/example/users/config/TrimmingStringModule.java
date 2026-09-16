package com.example.users.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdScalarDeserializer;
import com.fasterxml.jackson.databind.deser.std.StringDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import jakarta.inject.Singleton;

import java.io.IOException;

/**
 * Trims every incoming string and turns a blank one into null (BR-2).
 *
 * <p>Doing it at deserialization means a required field submitted as "   "
 * fails @NotBlank instead of being stored as whitespace, and an optional field
 * submitted as "" is read as absent rather than as an invalid short value.
 */
@Singleton
public class TrimmingStringModule extends SimpleModule {

    public TrimmingStringModule() {
        super("trimming-string-module");
        addDeserializer(String.class, new TrimmingStringDeserializer());
    }

    private static final class TrimmingStringDeserializer extends StdScalarDeserializer<String> {

        private TrimmingStringDeserializer() {
            super(String.class);
        }

        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) throws IOException {
            String value = StringDeserializer.instance.deserialize(parser, context);
            if (value == null) {
                return null;
            }
            String trimmed = value.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
    }
}
