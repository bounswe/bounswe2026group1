package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.config.OpenApiConfig;
import com.bounswe2026group1.backend.dto.FixRequestResponse;
import com.bounswe2026group1.backend.service.FixRequestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
// Don't import io.swagger.v3.oas.annotations.parameters.RequestBody as `RequestBody` —
// it would shadow Spring's @RequestBody from the wildcard import below. Use the FQN.
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/reports/{reportId}/fix-requests")
@RequiredArgsConstructor
@Tag(name = "Fix Requests", description = "Crowdsourced 'this issue is fixed' submissions and their voting flow.")
public class FixRequestController {

    private final FixRequestService fixRequestService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Submit a fix request for a report",
            description = "Multipart upload (`files` parts + optional `description`). Opens a new " +
                    "fix request that other users can vote on. Once it collects " +
                    "`app.report.fix.threshold` agree votes (default 5) at " +
                    "`app.report.fix.ratio` ratio (default 60%) the parent report transitions " +
                    "to `FIXED`.",
            // Springdoc would otherwise place these as query parameters; declaring an explicit
            // multipart requestBody keeps them in the body where they belong.
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    required = true,
                    content = @Content(
                            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
                            schemaProperties = {
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(
                                            name = "files",
                                            array = @io.swagger.v3.oas.annotations.media.ArraySchema(
                                                    schema = @Schema(type = "string", format = "binary"))),
                                    @io.swagger.v3.oas.annotations.media.SchemaProperty(
                                            name = "description",
                                            schema = @Schema(type = "string", maxLength = 1000,
                                                    description = "Optional free-text justification."))
                            }
                    )
            )
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Fix request created."),
            @ApiResponse(responseCode = "400", description = "Invalid files or description.",
                    content = @Content),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No report with that id.",
                    content = @Content)
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<FixRequestResponse> submit(
            @PathVariable Long reportId,
            @RequestParam("files") MultipartFile[] files,
            @RequestParam(value = "description", required = false) String description,
            @AuthenticationPrincipal String email) {
        FixRequestResponse response = fixRequestService.submit(reportId, email, description, files);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{fixId}/agree")
    @Operation(summary = "Cast an agree vote on a fix request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote recorded; returns the updated fix request."),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No fix request with that id.",
                    content = @Content)
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<FixRequestResponse> agree(
            @PathVariable Long reportId,
            @PathVariable Long fixId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(fixRequestService.agree(fixId, email));
    }

    @PostMapping("/{fixId}/disagree")
    @Operation(summary = "Cast a disagree vote on a fix request")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Vote recorded; returns the updated fix request."),
            @ApiResponse(responseCode = "401", description = "Authentication required.",
                    content = @Content),
            @ApiResponse(responseCode = "404", description = "No fix request with that id.",
                    content = @Content)
    })
    @SecurityRequirement(name = OpenApiConfig.BEARER_SCHEME)
    public ResponseEntity<FixRequestResponse> disagree(
            @PathVariable Long reportId,
            @PathVariable Long fixId,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(fixRequestService.disagree(fixId, email));
    }
}
