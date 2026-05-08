package com.bounswe2026group1.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class MeasurementWarning {

    private String field;
    private String message;
}
