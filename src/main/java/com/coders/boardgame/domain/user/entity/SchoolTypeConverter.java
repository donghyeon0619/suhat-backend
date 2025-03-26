package com.coders.boardgame.domain.user.entity;

import com.coders.boardgame.domain.user.enums.SchoolType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class SchoolTypeConverter implements AttributeConverter<SchoolType, Integer> {

    // 엔티티 -> DB에 저장할 때 호출
    @Override
    public Integer convertToDatabaseColumn(SchoolType schoolType) {
        if (schoolType == null) return null;
        return schoolType.getCode();
    }

    // DB -> 엔티티로 조회할 떄 호출
    @Override
    public SchoolType convertToEntityAttribute(Integer dbData) {
        if (dbData == null) return null;
        return SchoolType.of(dbData);
    }
}
