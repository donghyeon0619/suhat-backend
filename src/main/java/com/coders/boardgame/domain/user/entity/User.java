package com.coders.boardgame.domain.user.entity;

import com.coders.boardgame.domain.user.dto.UserDto;
import com.coders.boardgame.domain.user.enums.SchoolType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Convert(converter = SchoolTypeConverter.class)
    @Column(name = "school_type", nullable = false)
    private SchoolType schoolType;


    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int gender;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public User(UserDto userDto) {
        this.schoolType = userDto.getSchoolType();
        this.name = userDto.getName();
        this.gender = userDto.getGender();
        this.createdAt = LocalDateTime.now();
    }
}
