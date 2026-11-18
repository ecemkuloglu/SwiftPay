package com.swiftpay.config;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import java.io.IOException;
import java.math.BigDecimal;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * JSON rules for the whole API:
 * <ul>
 *   <li>every BigDecimal is written as a plain string with its scale ("25000.00", "0.00", never 2.5E+4)</li>
 *   <li>Instants are ISO-8601 UTC strings</li>
 *   <li>unknown request properties are rejected (400) so typos like "ammount" never pass silently</li>
 * </ul>
 */
@Configuration
public class JacksonConfig {

    @Bean
    public Jackson2ObjectMapperBuilderCustomizer swiftPayJackson() {
        return builder -> builder
                .serializerByType(BigDecimal.class, new PlainBigDecimalAsString())
                .featuresToEnable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .featuresToDisable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    static final class PlainBigDecimalAsString extends StdSerializer<BigDecimal> {

        PlainBigDecimalAsString() {
            super(BigDecimal.class);
        }

        @Override
        public void serialize(BigDecimal value, JsonGenerator gen, SerializerProvider provider) throws IOException {
            gen.writeString(value.toPlainString());
        }
    }
}
