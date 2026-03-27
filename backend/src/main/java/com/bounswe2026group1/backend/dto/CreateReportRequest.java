package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateReportRequest {
    private Long userId;
    private double latitude;
    private double longitude;
    private String description;
    private Tag tag;
}
