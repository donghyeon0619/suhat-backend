package com.coders.boardgame.domain.user.dto;

import com.coders.boardgame.domain.user.enums.SchoolType;
import lombok.Data;

@Data
public class UserDto {

    private SchoolType schoolType;
    private String name;
    private int gender;

}
