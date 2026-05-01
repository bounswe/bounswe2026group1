package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportSubscription;
import com.bounswe2026group1.backend.model.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ReportSubscriptionRepositoryTest {

    @Autowired
    private ReportSubscriptionRepository subscriptionRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @Autowired
    private ReportRepository reportRepository;

    private RegisteredUser alice;
    private RegisteredUser bob;
    private Report report;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice@example.com", "Alice");
        bob = persistUser("bob@example.com", "Bob");
        report = reportRepository.save(
                new Report(alice, new Location(41.0, 29.0), "Broken ramp", Tag.MISSING_RAMP));
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

    private RegisteredUser persistUser(String email, String name) {
        RegisteredUser user = new RegisteredUser();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("StrongP@ss1");
        user.setRole("USER");
        return registeredUserRepository.save(user);
    }
}
