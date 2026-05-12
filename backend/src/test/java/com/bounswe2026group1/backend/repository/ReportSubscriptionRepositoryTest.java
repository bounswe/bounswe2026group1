package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportSubscription;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import com.bounswe2026group1.backend.util.GeoUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ReportSubscriptionRepositoryTest extends AbstractPostgisIntegrationTest {

    @Autowired
    private ReportSubscriptionRepository subscriptionRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @Autowired
    private ReportRepository reportRepository;

    @PersistenceContext
    private EntityManager entityManager;

    private RegisteredUser alice;
    private RegisteredUser bob;
    private Report report;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice@example.com", "Alice");
        bob = persistUser("bob@example.com", "Bob");

        report = reportRepository.save(
                new Report(alice, GeoUtils.point4326(41.0, 29.0), "Broken ramp",
                        ReportType.OBSTACLE, ReportEnvironment.OUTDOOR));
    }

    @Test
    void existsByUserIdAndReportReportId_reflectsPersistedRow() {
        assertFalse(subscriptionRepository.existsByUserIdAndReportReportId(bob.getId(), report.getReportId()));

        ReportSubscription sub = new ReportSubscription();
        sub.setUser(bob);
        sub.setReport(report);
        subscriptionRepository.save(sub);

        assertTrue(subscriptionRepository.existsByUserIdAndReportReportId(bob.getId(), report.getReportId()));
    }

    @Test
    void deleteByUserIdAndReportReportId_removesRow() {
        ReportSubscription sub = new ReportSubscription();
        sub.setUser(bob);
        sub.setReport(report);
        subscriptionRepository.save(sub);

        subscriptionRepository.deleteByUserIdAndReportReportId(bob.getId(), report.getReportId());

        Optional<ReportSubscription> after =
                subscriptionRepository.findByUserIdAndReportReportId(bob.getId(), report.getReportId());
        assertFalse(after.isPresent());
    }

    @Test
    void findSubscriberUserIdsByReportId_returnsSubscriberIds() {
        ReportSubscription sub = new ReportSubscription();
        sub.setUser(bob);
        sub.setReport(report);
        subscriptionRepository.save(sub);

        List<Long> ids = subscriptionRepository.findSubscriberUserIdsByReportId(report.getReportId());

        assertEquals(1, ids.size());
        assertEquals(bob.getId(), ids.get(0));
    }

    @Test
    void deletingReport_cascadesToSubscriptions() {
        ReportSubscription sub = new ReportSubscription();
        sub.setUser(bob);
        sub.setReport(report);
        subscriptionRepository.save(sub);

        Long reportId = report.getReportId();

        // Mirror production: ReportService.delete fetches the report fresh and
        // cascade-loads its associations. Without clearing the persistence
        // context, the orphaned sub sits in the session and trips a transient
        // reference check at flush time.
        entityManager.flush();
        entityManager.clear();

        Report reloaded = reportRepository.findById(reportId).orElseThrow();
        reportRepository.delete(reloaded);
        reportRepository.flush();

        assertFalse(subscriptionRepository.existsByUserIdAndReportReportId(bob.getId(), reportId));
    }

    private RegisteredUser persistUser(String email, String name) {
        RegisteredUser user = new RegisteredUser();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("StrongP@ss1");
        user.setRole(UserRole.USER);
        return registeredUserRepository.save(user);
    }
}
