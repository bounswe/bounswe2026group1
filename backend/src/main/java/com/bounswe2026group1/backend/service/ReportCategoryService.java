package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.ReportCategoryResponse;
import com.bounswe2026group1.backend.model.ReportCategory;
import com.bounswe2026group1.backend.repository.ReportCategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportCategoryService {

    private final ReportCategoryRepository categoryRepository;

    public List<ReportCategoryResponse> getAllAsTree() {
        List<ReportCategory> all = categoryRepository.findAll();

        Map<Long, List<ReportCategory>> byParent = all.stream()
                .filter(c -> c.getParent() != null)
                .collect(Collectors.groupingBy(c -> c.getParent().getId()));

        return all.stream()
                .filter(c -> c.getParent() == null)
                .map(root -> toResponse(root, byParent))
                .collect(Collectors.toList());
    }

    public Optional<ReportCategoryResponse> getById(Long id) {
        return categoryRepository.findById(id)
                .map(category -> {
                    Map<Long, List<ReportCategory>> byParent = categoryRepository.findAll().stream()
                            .filter(c -> c.getParent() != null)
                            .collect(Collectors.groupingBy(c -> c.getParent().getId()));
                    return toResponse(category, byParent);
                });
    }

    private ReportCategoryResponse toResponse(ReportCategory category, Map<Long, List<ReportCategory>> byParent) {
        List<ReportCategoryResponse> children = byParent
                .getOrDefault(category.getId(), List.of())
                .stream()
                .map(child -> toResponse(child, byParent))
                .collect(Collectors.toList());

        return new ReportCategoryResponse(category, children);
    }
}
