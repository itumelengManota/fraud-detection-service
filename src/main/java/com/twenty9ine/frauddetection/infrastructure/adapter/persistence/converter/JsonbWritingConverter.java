package com.twenty9ine.frauddetection.infrastructure.adapter.persistence.converter;

import org.postgresql.util.PGobject;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.convert.WritingConverter;

import java.sql.SQLException;

@WritingConverter
public class JsonbWritingConverter implements Converter<String, PGobject> {

    @Override
    public PGobject convert(String source) {
        PGobject pgObject = new PGobject();
        pgObject.setType("jsonb");
        try {
            pgObject.setValue(source);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to convert String to JSONB", e);
        }
        return pgObject;
    }
}