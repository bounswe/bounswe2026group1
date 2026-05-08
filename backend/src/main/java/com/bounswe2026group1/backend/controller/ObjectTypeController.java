package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ObjectTypeInfoResponse;
import com.bounswe2026group1.backend.service.ObjectTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/object-types")
@RequiredArgsConstructor
public class ObjectTypeController {

    private final ObjectTypeService objectTypeService;

    @GetMapping
    public List<ObjectTypeInfoResponse> getAll() {
        return objectTypeService.getAll();
    }
}
