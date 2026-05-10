package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ObjectTypeInfoResponse;
import com.bounswe2026group1.backend.service.ObjectTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/object-types")
@RequiredArgsConstructor
@Tag(name = "Object Types",
        description = "Static catalogue of report object types and their issue/measurement schemas.")
public class ObjectTypeController {

    private final ObjectTypeService objectTypeService;

    @GetMapping
    @Operation(
            summary = "List all report object types",
            description = "Returns each known object type with its associated issue list and " +
                    "measurement schema, so clients can render the right form when a user " +
                    "attaches an object to a report."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Catalogue of object types with their valid issue types and measurement schemas.",
                    content = @Content(schema = @Schema(implementation = ObjectTypeInfoResponse.class)))
    })
    public List<ObjectTypeInfoResponse> getAll() {
        return objectTypeService.getAll();
    }
}
