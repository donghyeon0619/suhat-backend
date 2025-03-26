package com.coders.boardgame.domain.user.enums;

import lombok.Getter;

/**
 * 학교별 enum 타입 클래스
 */
@Getter
public enum SchoolType {

    LOWER_ELEMENTARY(0, "초등학교 1~3학년"), // 초등학교 1~3학년
    UPPER_ELEMENTARY(1, "초등학교 4~6학년"), // 초등학교 4~6학년
    MIDDLE_SCHOOL(2, "중학생");   // 중학생

    private final int code;
    private final String description;

    SchoolType(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public static SchoolType of(int code) {
        for (SchoolType s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("부적절한 학교타입: " + code);
    }
}
