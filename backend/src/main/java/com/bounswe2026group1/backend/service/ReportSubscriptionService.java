package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportSubscription;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.ReportSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportSubscriptionService {

    private final ReportSubscriptionRepository subscriptionRepository;
    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;

    /** Subscribe is idempotent: re-subscribing returns the existing row instead of
     *  raising a conflict. The "Follow Updates" UI calls this every time the
     *  button is tapped, so we treat the second call as a no-op. */
    @Transactional
    public ReportSubscription subscribe(Long reportId, String email) {
        RegisteredUser user = resolveUser(email);
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report not found: " + reportId));

        return subscriptionRepository.findByUserIdAndReportReportId(user.getId(), reportId)
                .orElseGet(() -> {
                    ReportSubscription sub = new ReportSubscription();
                    sub.setUser(user);
                    sub.setReport(report);
                    return subscriptionRepository.save(sub);
                });
    }

    /** Internal entry-point for service-layer code that already has the user
     *  and report entities in hand (report-create, vote-cast, comment-create).
     *  Idempotent — re-subscribing a user is a no-op. We accept Hibernate
     *  reference proxies, so this stays a single round trip when the caller
     *  already holds the entity. */
    @Transactional
    public void subscribeInternal(Report report, RegisteredUser user) {
        if (report == null || user == null) return;
        Long userId = user.getId();
        Long reportId = report.getReportId();
        if (userId == null || reportId == null) return;
        subscriptionRepository.findByUserIdAndReportReportId(userId, reportId)
                .orElseGet(() -> {
                    ReportSubscription sub = new ReportSubscription();
                    sub.setUser(user);
                    sub.setReport(report);
                    return subscriptionRepository.save(sub);
                });
    }

    /** Unsubscribe is idempotent: deleting a row that does not exist is a no-op. */
    @Transactional
    public void unsubscribe(Long reportId, String email) {
        RegisteredUser user = resolveUser(email);
        if (!reportRepository.existsById(reportId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found: " + reportId);
        }
        subscriptionRepository.deleteByUserIdAndReportReportId(user.getId(), reportId);
    }

    @Transactional(readOnly = true)
    public boolean isSubscribed(Long reportId, String email) {
        RegisteredUser user = resolveUser(email);
        return subscriptionRepository.existsByUserIdAndReportReportId(user.getId(), reportId);
    }

    @Transactional(readOnly = true)
    public List<Long> findSubscriberUserIds(Long reportId) {
        return subscriptionRepository.findSubscriberUserIdsByReportId(reportId);
    }

    private RegisteredUser resolveUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return registeredUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "User not found: " + email));
    }
}
