package com.twenty9ine.frauddetection.infrastructure.config;

import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.converter.JsonbReadingConverter;
import com.twenty9ine.frauddetection.infrastructure.adapter.persistence.converter.JsonbWritingConverter;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.AbstractJdbcConfiguration;
import org.springframework.data.jdbc.repository.config.EnableJdbcAuditing;

import java.util.Arrays;
import java.util.List;

@EnableJdbcAuditing
@Configuration
public class JdbcConfig extends AbstractJdbcConfiguration {

    @Override
    protected List<?> userConverters() {
        return Arrays.asList(
                new JsonbWritingConverter(),
                new JsonbReadingConverter()
        );
    }
}
