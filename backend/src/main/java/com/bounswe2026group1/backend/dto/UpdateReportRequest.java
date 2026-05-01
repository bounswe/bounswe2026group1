package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Tag;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateReportRequest {
    private String description;
    private Tag tag;
    private Double latitude;
    private Double longitude;
    private List<Long> mediaIdsToRemove;
}
