package com.allclear.gateway.domain.jwt;

import lombok.Builder;
import lombok.Getter;

public class StudentResponseDTO {

    @Getter
    @Builder
    public static class GetStudentDTO {
        private Long studentId;
        private String studentIdNumber;
        private int grade;
    }
}
