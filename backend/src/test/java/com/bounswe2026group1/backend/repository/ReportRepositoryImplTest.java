package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
class ReportRepositoryImplTest extends AbstractPostgisIntegrationTest {

    // Istanbul center as the reference point for radius queries
    private static final double REF_LAT = 41.015137;
    private static final double REF_LON = 28.979530;

    @Autowired
    private ReportRepository reportRepository;

    @Autowired
    private RegisteredUserRepository userRepository;

    @Test
    void findFeedWithinRadius_returnsOnlyReportsInsideRadius_orderedByDistance() {
        RegisteredUser user = persistUser("ada-feed@test.com");

        // ~30m east of reference — well inside any sane radius
        Report near = persistReport(user, REF_LAT, REF_LON + 0.0003,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        // ~110m east
        Report mid = persistReport(user, REF_LAT, REF_LON + 0.0010,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        // ~5km east — outside a 1km radius
        Report far = persistReport(user, REF_LAT, REF_LON + 0.05,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        Page<Report> page = reportRepository.findFeedWithinRadius(
                null, null, REF_LAT, REF_LON, 1.0, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(near.getReportId()));
        assertTrue(ids.contains(mid.getReportId()));
        assertFalse(ids.contains(far.getReportId()), "report outside radius must not appear");
        assertEquals(near.getReportId(), ids.get(0), "nearest report must come first");
        assertEquals(2L, page.getTotalElements());
    }

    @Test
    void findFeedWithinRadius_filtersByReportType() {
        RegisteredUser user = persistUser("type-filter@test.com");
        Report obstacle = persistReport(user, REF_LAT, REF_LON + 0.0003,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report feature = persistReport(user, REF_LAT, REF_LON + 0.0004,
                ReportType.FEATURE, ReportEnvironment.OUTDOOR);

        Page<Report> obstacles = reportRepository.findFeedWithinRadius(
                ReportType.OBSTACLE, null, REF_LAT, REF_LON, 1.0, PageRequest.of(0, 10));

        List<Long> ids = obstacles.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(obstacle.getReportId()));
        assertFalse(ids.contains(feature.getReportId()));
        assertEquals(1L, obstacles.getTotalElements());
    }

    @Test
    void findFeedWithinRadius_filtersByEnvironment() {
        RegisteredUser user = persistUser("env-filter@test.com");
        Report indoor = persistReport(user, REF_LAT, REF_LON + 0.0003,
                ReportType.OBSTACLE, ReportEnvironment.INDOOR);
        Report outdoor = persistReport(user, REF_LAT, REF_LON + 0.0004,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        Page<Report> indoorPage = reportRepository.findFeedWithinRadius(
                null, ReportEnvironment.INDOOR, REF_LAT, REF_LON, 1.0, PageRequest.of(0, 10));

        List<Long> ids = indoorPage.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(indoor.getReportId()));
        assertFalse(ids.contains(outdoor.getReportId()));
        assertEquals(1L, indoorPage.getTotalElements());
    }

    @Test
    void findFeedWithinRadius_combinesTypeAndEnvironmentFilters() {
        RegisteredUser user = persistUser("combo-filter@test.com");
        Report match = persistReport(user, REF_LAT, REF_LON + 0.0003,
                ReportType.FEATURE, ReportEnvironment.INDOOR);
        Report wrongType = persistReport(user, REF_LAT, REF_LON + 0.0004,
                ReportType.OBSTACLE, ReportEnvironment.INDOOR);
        Report wrongEnv = persistReport(user, REF_LAT, REF_LON + 0.0005,
                ReportType.FEATURE, ReportEnvironment.OUTDOOR);

        Page<Report> page = reportRepository.findFeedWithinRadius(
                ReportType.FEATURE, ReportEnvironment.INDOOR,
                REF_LAT, REF_LON, 1.0, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(match.getReportId()));
        assertFalse(ids.contains(wrongType.getReportId()));
        assertFalse(ids.contains(wrongEnv.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedWithinRadius_paginatesWithCorrectTotal() {
        RegisteredUser user = persistUser("page-radius@test.com");
        // Persist 3 reports at increasing distances
        Report r1 = persistReport(user, REF_LAT, REF_LON + 0.0001,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report r2 = persistReport(user, REF_LAT, REF_LON + 0.0002,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report r3 = persistReport(user, REF_LAT, REF_LON + 0.0003,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        Pageable pageable = PageRequest.of(0, 2);
        Page<Report> first = reportRepository.findFeedWithinRadius(
                null, null, REF_LAT, REF_LON, 1.0, pageable);

        assertEquals(2, first.getContent().size());
        assertEquals(3L, first.getTotalElements(), "total must reflect full match set, not page size");
        // ordered by distance ASC
        assertEquals(r1.getReportId(), first.getContent().get(0).getReportId());
        assertEquals(r2.getReportId(), first.getContent().get(1).getReportId());

        Page<Report> second = reportRepository.findFeedWithinRadius(
                null, null, REF_LAT, REF_LON, 1.0, PageRequest.of(1, 2));
        assertEquals(1, second.getContent().size());
        assertEquals(r3.getReportId(), second.getContent().get(0).getReportId());
    }

    @Test
    void findFeedRecent_returnsAllReports_orderedByPublishDateDesc() {
        RegisteredUser user = persistUser("recent-order@test.com");
        Report older = persistReportWithPublishDate(user,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR,
                Instant.now().minusSeconds(3600));
        Report newer = persistReportWithPublishDate(user,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR,
                Instant.now());

        Page<Report> page = reportRepository.findFeedRecent(null, null, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertEquals(newer.getReportId(), ids.get(0), "newer report should come first");
        assertEquals(older.getReportId(), ids.get(1));
    }

    @Test
    void findFeedRecent_filtersByReportType() {
        RegisteredUser user = persistUser("recent-type@test.com");
        Report obstacle = persistReport(user, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report feature = persistReport(user, REF_LAT, REF_LON,
                ReportType.FEATURE, ReportEnvironment.OUTDOOR);

        Page<Report> page = reportRepository.findFeedRecent(
                ReportType.FEATURE, null, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(feature.getReportId()));
        assertFalse(ids.contains(obstacle.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_filtersByEnvironment() {
        RegisteredUser user = persistUser("recent-env@test.com");
        Report indoor = persistReport(user, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.INDOOR);
        Report outdoor = persistReport(user, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        Page<Report> page = reportRepository.findFeedRecent(
                null, ReportEnvironment.OUTDOOR, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(outdoor.getReportId()));
        assertFalse(ids.contains(indoor.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_paginatesWithCorrectTotal() {
        RegisteredUser user = persistUser("recent-page@test.com");
        for (int i = 0; i < 3; i++) {
            persistReportWithPublishDate(user,
                    ReportType.OBSTACLE, ReportEnvironment.OUTDOOR,
                    Instant.now().minusSeconds(i * 60L));
        }

        Page<Report> first = reportRepository.findFeedRecent(null, null, PageRequest.of(0, 2));
        assertEquals(2, first.getContent().size());
        assertEquals(3L, first.getTotalElements());

        Page<Report> second = reportRepository.findFeedRecent(null, null, PageRequest.of(1, 2));
        assertEquals(1, second.getContent().size());
    }

    private RegisteredUser persistUser(String email) {
        RegisteredUser u = new RegisteredUser();
        u.setName("Test");
        u.setEmail(email);
        u.setPassword("hashedPassword");
        u.setRole(UserRole.USER);
        return userRepository.save(u);
    }

    private Report persistReport(RegisteredUser user, double lat, double lon,
                                 ReportType type, ReportEnvironment env) {
        Report r = new Report(user, GeoUtils.point4326(lat, lon),
                "test report", type, env);
        return reportRepository.save(r);
    }

    private Report persistReportWithPublishDate(RegisteredUser user,
                                                ReportType type, ReportEnvironment env,
                                                Instant publishDate) {
        Report r = new Report(user, GeoUtils.point4326(REF_LAT, REF_LON),
                "test report", type, env);
        r.setPublishDate(publishDate);
        return reportRepository.save(r);
    }
}
