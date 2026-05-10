package com.bounswe2026group1.backend.dto;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "A comment serialized as a W3C Web Annotation Data Model Annotation "
        + "(https://www.w3.org/TR/annotation-model/). Returned when the client requests "
        + "media type application/ld+json on the standard comment endpoints.")
public record CommentAnnotationResponse(

        @JsonProperty("@context")
        @Schema(description = "JSON-LD context.", example = "http://www.w3.org/ns/anno.jsonld")
        String context,

        @Schema(description = "Annotation IRI.", example = "https://api.mapcess.live/api/comments/501")
        String id,

        @Schema(description = "Annotation type.", example = "Annotation")
        String type,

        @Schema(description = "Author of the annotation. Null if the author has been removed.",
                nullable = true)
        Creator creator,

        @Schema(description = "When the annotation was created (UTC).", example = "2026-05-08T13:22:11Z")
        Instant created,

        @Schema(description = "Annotation body — the comment text.")
        Body body,

        @Schema(description = "IRI of the resource the annotation is about (the report).",
                example = "https://api.mapcess.live/api/reports/77")
        String target
) {

    @Schema(description = "Minimal public projection of a user as a schema.org-style Person reference. "
            + "Never includes email, password, or role.")
    public record Creator(
            @Schema(description = "Author IRI.", example = "https://api.mapcess.live/api/users/42")
            String id,
            @Schema(description = "Object type.", example = "Person")
            String type,
            @Schema(description = "Display name.", example = "Alice Example")
            String name
    ) {}

    @Schema(description = "Embedded TextualBody carrying the comment text.")
    public record Body(
            @Schema(description = "Body type.", example = "TextualBody")
            String type,
            @Schema(description = "Comment text.", example = "I confirmed this — the ramp is gone.")
            String value,
            @Schema(description = "MIME format of the body value.", example = "text/plain")
            String format
    ) {}

    public static CommentAnnotationResponse fromEntity(Comment c, String apiBase) {
        String base = apiBase == null ? "" : apiBase;
        RegisteredUser a = c.getAuthor();
        Creator creator = (a == null) ? null : new Creator(
                base + "/api/users/" + a.getId(),
                "Person",
                a.getName());
        Long reportId = (c.getReport() == null) ? null : c.getReport().getReportId();
        String target = (reportId == null) ? null : base + "/api/reports/" + reportId;
        return new CommentAnnotationResponse(
                "http://www.w3.org/ns/anno.jsonld",
                base + "/api/comments/" + c.getId(),
                "Annotation",
                creator,
                c.getCreatedAt(),
                new Body("TextualBody", c.getContent(), "text/plain"),
                target);
    }
}
