package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Long reportId;
    private Long userId;
    private double latitude;
    private double longitude;
    private String description;
    private Long categoryId;
    private String categoryName;
    /** Root → leaf names for the report's category. */
    private List<String> categoryPath;
    private ReportType categoryType;
    private ReportEnvironment environment;
    private ReportStatus status;
    private int agrees;
    private int disagrees;
    private LocalDateTime publishDate;
    private List<String> mediaUrls;
    private VoteType userVote;
    /** Parsed JSON measurements (e.g. slope, width). Empty when absent or invalid JSON. */
    private Map<String, Object> measurements;
    private List<MeasurementWarning> warnings;
    private Double entryLatitude;
    private Double entryLongitude;
    private Double exitLatitude;
    private Double exitLongitude;
    private Long lastEditedByUserId;

    public static ReportResponse fromEntity(Report report) {
        return fromEntity(report, null, Collections.emptyList());
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote) {
        return fromEntity(report, userVote, Collections.emptyList());
    }

    public static ReportResponse fromEntity(Report report, VoteType userVote, List<MeasurementWarning> warnings) {
        return fromEntity(report, userVote, warnings, null);
    }

    /**
     * @param categoryPathResolver if non-null, resolves leaf category id → path segments (root first).
     *                             If null, path is built from loaded {@link ReportCategory} entities when possible.
     */
    public static ReportResponse fromEntity(Report report, VoteType userVote, List<MeasurementWarning> warnings,
                                            Function<Long, List<String>> categoryPathResolver) {
        ReportResponse r = new ReportResponse();
        r.setReportId(report.getReportId());
        if (report.getCreatedBy() != null) {
            r.setUserId(report.getCreatedBy().getId());
        }

        if (report.getLocation() != null) {
            r.setLatitude(report.getLocation().getLatitude());
            r.setLongitude(report.getLocation().getLongitude());
        }

        String desc = report.getDescription();
        r.setDescription(desc != null ? desc : "");

        ReportCategory category = report.getCategory();
        if (category != null) {
            r.setCategoryId(category.getId());
            String catName = category.getName();
            r.setCategoryName(catName != null ? catName : "");
            r.setCategoryType(resolveType(category));
            if (categoryPathResolver != null) {
                List<String> path = categoryPathResolver.apply(category.getId());
                r.setCategoryPath(path != null ? path : List.of());
            } else {
                r.setCategoryPath(defaultCategoryPath(category));
            }
        } else {
            r.setCategoryId(null);
            r.setCategoryName("");
            r.setCategoryType(null);
            r.setCategoryPath(List.of());
        }

        r.setEnvironment(report.getEnvironment());
        r.setStatus(report.getStatus());
        r.setAgrees(report.getAgrees());
        r.setDisagrees(report.getDisagrees());
        r.setPublishDate(report.getPublishDate());
        r.setMediaUrls(report.getMediaList() != null
                ? report.getMediaList().stream().map(Media::getFilePath).toList()
                : List.of());
        r.setUserVote(userVote);
        r.setMeasurements(parseMeasurementsJson(report.getMeasurements()));
        r.setWarnings(warnings != null ? warnings : Collections.emptyList());

        if (report.getEntryPoint() != null) {
            r.setEntryLatitude(report.getEntryPoint().getLatitude());
            r.setEntryLongitude(report.getEntryPoint().getLongitude());
        }
        if (report.getExitPoint() != null) {
            r.setExitLatitude(report.getExitPoint().getLatitude());
            r.setExitLongitude(report.getExitPoint().getLongitude());
        }
        if (report.getLastEditedBy() != null) {
            r.setLastEditedByUserId(report.getLastEditedBy().getId());
        }
        return r;
    }

    private static Map<String, Object> parseMeasurementsJson(String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyMap();
        }
        try {
            Map<String, Object> map = MAPPER.readValue(json, new TypeReference<>() {});
            return map != null ? map : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static List<String> defaultCategoryPath(ReportCategory leaf) {
        if (leaf == null) {
            return List.of();
        }
        LinkedList<String> path = new LinkedList<>();
        ReportCategory current = leaf;
        int guard = 0;
        while (current != null && guard++ < 64) {
            if (current.getName() != null) {
                path.addFirst(current.getName());
            }
            current = current.getParent();
        }
        return new ArrayList<>(path);
    }

    private static ReportType resolveType(ReportCategory category) {
        ReportCategory current = category;
        int guard = 0;
        while (current != null && guard++ < 64) {
            if (current.getType() != null) {
                return current.getType();
            }
            current = current.getParent();
        }
        return null;
    }
}
