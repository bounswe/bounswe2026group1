package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class CommentRepositoryTest extends AbstractPostgisIntegrationTest {

    @Autowired
    private CommentRepository commentRepository;

    @Autowired
    private RegisteredUserRepository registeredUserRepository;

    @Autowired
    private ReportRepository reportRepository;

    private RegisteredUser alice;
    private RegisteredUser bob;
    private Report report;

    @BeforeEach
    void setUp() {
        alice = persistUser("alice@c.example", "Alice");
        bob = persistUser("bob@c.example", "Bob");

        report = reportRepository.save(
                new Report(alice, GeoUtils.point4326(41.0, 29.0), "Ramp",
                        ReportType.OBSTACLE, ReportEnvironment.OUTDOOR));

        Comment c1 = new Comment();
        c1.setContent("first");
        c1.setAuthor(alice);
        c1.setReport(report);
        commentRepository.save(c1);

        Comment c2 = new Comment();
        c2.setContent("second");
        c2.setAuthor(bob);
        c2.setReport(report);
        commentRepository.save(c2);
    }

    @Test
    void findCommenterUserIdsByReportId_returnsDistinctAuthorIds() {
        List<Long> ids = commentRepository.findCommenterUserIdsByReportId(report.getReportId());

        assertEquals(2, ids.size());
        Set<Long> unique = new HashSet<>(ids);
        assertEquals(2, unique.size());
        assertTrue(unique.contains(alice.getId()));
        assertTrue(unique.contains(bob.getId()));
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
