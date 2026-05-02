package com.bounswe2026group1.backend.controller;

import com.bounswe2026group1.backend.dto.ReportCategoryResponse;
import com.bounswe2026group1.backend.service.ReportCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class ReportCategoryController {

    private final ReportCategoryService categoryService;

    @GetMapping
    public List<ReportCategoryResponse> getAll() {
        return categoryService.getAllAsTree();
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReportCategoryResponse> getById(@PathVariable Long id) {
        return categoryService.getById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
