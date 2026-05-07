package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminStatsResponse;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.UserStatus;
import com.bounswe2026group1.backend.repository.CommentRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AdminStatsService {

    private final RegisteredUserRepository userRepository;
    private final ReportRepository reportRepository;
    private final CommentRepository commentRepository;

    public AdminStatsResponse getStats() {
        return new AdminStatsResponse(
                userRepository.count(),
                userRepository.countByStatus(UserStatus.ACTIVE),
                userRepository.countByStatus(UserStatus.BANNED),
                reportRepository.count(),
                reportRepository.countByStatus(ReportStatus.PENDING),
                reportRepository.countByStatus(ReportStatus.VERIFIED),
                commentRepository.count()
        );
    }
}
