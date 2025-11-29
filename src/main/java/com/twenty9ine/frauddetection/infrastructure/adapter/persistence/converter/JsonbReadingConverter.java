package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.converter;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.ReadingConverter;

@ReadingConverter
public class JsonbReadingConverter implements Converter<PGobject, String> {

    @Override
    public String convert(PGobject source) {
        return source.getValue();
    }
}