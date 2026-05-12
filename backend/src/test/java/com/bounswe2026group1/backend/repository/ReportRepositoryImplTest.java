package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.dto.FeedSort;
import com.bounswe2026group1.backend.dto.ReportFeedQuery;
import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportObject;
import com.bounswe2026group1.backend.model.ReportStatus;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

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

    // ───── proximity branch ────────────────────────────────────────────────────────

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
                proximity(REF_LAT, REF_LON, 1.0), PageRequest.of(0, 10));

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

        ReportFeedQuery q = proximity(REF_LAT, REF_LON, 1.0);
        q.setReportType(ReportType.OBSTACLE);
        Page<Report> obstacles = reportRepository.findFeedWithinRadius(q, PageRequest.of(0, 10));

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

        ReportFeedQuery q = proximity(REF_LAT, REF_LON, 1.0);
        q.setEnvironment(ReportEnvironment.INDOOR);
        Page<Report> indoorPage = reportRepository.findFeedWithinRadius(q, PageRequest.of(0, 10));

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

        ReportFeedQuery q = proximity(REF_LAT, REF_LON, 1.0);
        q.setReportType(ReportType.FEATURE);
        q.setEnvironment(ReportEnvironment.INDOOR);
        Page<Report> page = reportRepository.findFeedWithinRadius(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(match.getReportId()));
        assertFalse(ids.contains(wrongType.getReportId()));
        assertFalse(ids.contains(wrongEnv.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedWithinRadius_paginatesWithCorrectTotal() {
        RegisteredUser user = persistUser("page-radius@test.com");
        Report r1 = persistReport(user, REF_LAT, REF_LON + 0.0001,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report r2 = persistReport(user, REF_LAT, REF_LON + 0.0002,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report r3 = persistReport(user, REF_LAT, REF_LON + 0.0003,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        Pageable pageable = PageRequest.of(0, 2);
        Page<Report> first = reportRepository.findFeedWithinRadius(
                proximity(REF_LAT, REF_LON, 1.0), pageable);

        assertEquals(2, first.getContent().size());
        assertEquals(3L, first.getTotalElements(), "total must reflect full match set, not page size");
        assertEquals(r1.getReportId(), first.getContent().get(0).getReportId());
        assertEquals(r2.getReportId(), first.getContent().get(1).getReportId());

        Page<Report> second = reportRepository.findFeedWithinRadius(
                proximity(REF_LAT, REF_LON, 1.0), PageRequest.of(1, 2));
        assertEquals(1, second.getContent().size());
        assertEquals(r3.getReportId(), second.getContent().get(0).getReportId());
    }

    // ───── recent branch — legacy coverage ────────────────────────────────────────

    @Test
    void findFeedRecent_returnsAllReports_orderedByPublishDateDesc() {
        RegisteredUser user = persistUser("recent-order@test.com");
        Report older = persistReportWithPublishDate(user,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR,
                Instant.now().minusSeconds(3600));
        Report newer = persistReportWithPublishDate(user,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR,
                Instant.now());

        Page<Report> page = reportRepository.findFeedRecent(new ReportFeedQuery(), PageRequest.of(0, 10));

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

        ReportFeedQuery q = new ReportFeedQuery();
        q.setReportType(ReportType.FEATURE);
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

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

        ReportFeedQuery q = new ReportFeedQuery();
        q.setEnvironment(ReportEnvironment.OUTDOOR);
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

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

        Page<Report> first = reportRepository.findFeedRecent(new ReportFeedQuery(), PageRequest.of(0, 2));
        assertEquals(2, first.getContent().size());
        assertEquals(3L, first.getTotalElements());

        Page<Report> second = reportRepository.findFeedRecent(new ReportFeedQuery(), PageRequest.of(1, 2));
        assertEquals(1, second.getContent().size());
    }

    // ───── new filters ────────────────────────────────────────────────────────────

    @Test
    void findFeedRecent_filtersByStatus_multipleValues() {
        RegisteredUser user = persistUser("status-filter@test.com");
        Report pending = persistReport(user, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report verified = persistReport(user, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        verified.setStatus(ReportStatus.VERIFIED);
        verified = reportRepository.save(verified);
        Report fixed = persistReport(user, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        fixed.setStatus(ReportStatus.FIXED);
        reportRepository.save(fixed);

        ReportFeedQuery q = new ReportFeedQuery();
        q.setStatus(List.of(ReportStatus.PENDING, ReportStatus.VERIFIED));
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(pending.getReportId()));
        assertTrue(ids.contains(verified.getReportId()));
        assertEquals(2L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_filtersByAuthorId() {
        RegisteredUser alice = persistUser("author-a@test.com");
        RegisteredUser bob = persistUser("author-b@test.com");
        Report aliceReport = persistReport(alice, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        Report bobReport = persistReport(bob, REF_LAT, REF_LON,
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);

        ReportFeedQuery q = new ReportFeedQuery();
        q.setAuthorId(alice.getId());
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(aliceReport.getReportId()));
        assertFalse(ids.contains(bobReport.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_filtersByPublishedDateRange() {
        RegisteredUser user = persistUser("date-range@test.com");
        Report old = persistReportWithPublishDate(user, ReportType.OBSTACLE,
                ReportEnvironment.OUTDOOR, Instant.parse("2026-01-01T00:00:00Z"));
        Report mid = persistReportWithPublishDate(user, ReportType.OBSTACLE,
                ReportEnvironment.OUTDOOR, Instant.parse("2026-03-15T00:00:00Z"));
        Report fresh = persistReportWithPublishDate(user, ReportType.OBSTACLE,
                ReportEnvironment.OUTDOOR, Instant.parse("2026-05-01T00:00:00Z"));

        ReportFeedQuery q = new ReportFeedQuery();
        q.setPublishedAfter(Instant.parse("2026-02-01T00:00:00Z"));
        q.setPublishedBefore(Instant.parse("2026-04-01T00:00:00Z"));
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertFalse(ids.contains(old.getReportId()));
        assertTrue(ids.contains(mid.getReportId()));
        assertFalse(ids.contains(fresh.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_freeTextSearchIsCaseInsensitive() {
        RegisteredUser user = persistUser("q-filter@test.com");
        Report hit = persistReportWithDescription(user, "Broken ELEVATOR on floor 2");
        Report miss = persistReportWithDescription(user, "Cracked sidewalk");

        ReportFeedQuery q = new ReportFeedQuery();
        q.setQ("elevator");
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(hit.getReportId()));
        assertFalse(ids.contains(miss.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_filtersByVoteThresholds() {
        RegisteredUser user = persistUser("votes-filter@test.com");
        Report low = persistReportWithVotes(user, 1, 0);
        Report high = persistReportWithVotes(user, 8, 1);
        Report controversial = persistReportWithVotes(user, 5, 5);

        ReportFeedQuery q = new ReportFeedQuery();
        q.setMinAgrees(5);
        q.setMinNetScore(3);
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertFalse(ids.contains(low.getReportId()));
        assertTrue(ids.contains(high.getReportId()));
        assertFalse(ids.contains(controversial.getReportId()),
                "5-5 fails minNetScore=3 even though it passes minAgrees=5");
        assertEquals(1L, page.getTotalElements());
    }

    @Test
    void findFeedRecent_filtersByObjectTypeAndIssueType() {
        RegisteredUser user = persistUser("object-filter@test.com");
        Report rampReport = persistReportWithObject(user, ObjectType.RAMP,
                EnumSet.of(IssueType.TOO_STEEP));
        Report doorReport = persistReportWithObject(user, ObjectType.DOOR,
                EnumSet.of(IssueType.HEAVY_DOOR));
        Report rampWrongIssue = persistReportWithObject(user, ObjectType.RAMP,
                EnumSet.of(IssueType.MISSING_HANDRAIL));

        ReportFeedQuery byObject = new ReportFeedQuery();
        byObject.setObjectType(List.of(ObjectType.RAMP));
        Page<Report> rampPage = reportRepository.findFeedRecent(byObject, PageRequest.of(0, 10));
        List<Long> rampIds = rampPage.getContent().stream().map(Report::getReportId).toList();
        assertTrue(rampIds.contains(rampReport.getReportId()));
        assertTrue(rampIds.contains(rampWrongIssue.getReportId()));
        assertFalse(rampIds.contains(doorReport.getReportId()));
        assertEquals(2L, rampPage.getTotalElements());

        ReportFeedQuery byIssue = new ReportFeedQuery();
        byIssue.setIssueType(List.of(IssueType.TOO_STEEP));
        Page<Report> steepPage = reportRepository.findFeedRecent(byIssue, PageRequest.of(0, 10));
        List<Long> steepIds = steepPage.getContent().stream().map(Report::getReportId).toList();
        assertTrue(steepIds.contains(rampReport.getReportId()));
        assertFalse(steepIds.contains(rampWrongIssue.getReportId()));
        assertFalse(steepIds.contains(doorReport.getReportId()));
        assertEquals(1L, steepPage.getTotalElements());
    }

    @Test
    void findFeedRecent_sortMostAgreed_ordersByAgreesDesc() {
        RegisteredUser user = persistUser("sort-agreed@test.com");
        Report mid = persistReportWithVotes(user, 5, 0);
        Report top = persistReportWithVotes(user, 12, 3);
        Report low = persistReportWithVotes(user, 1, 0);

        ReportFeedQuery q = new ReportFeedQuery();
        q.setSort(FeedSort.MOST_AGREED);
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertEquals(top.getReportId(), ids.get(0));
        assertEquals(mid.getReportId(), ids.get(1));
        assertEquals(low.getReportId(), ids.get(2));
    }

    @Test
    void findFeedRecent_sortOldest_reversesPublishDateOrder() {
        RegisteredUser user = persistUser("sort-oldest@test.com");
        Report older = persistReportWithPublishDate(user, ReportType.OBSTACLE,
                ReportEnvironment.OUTDOOR, Instant.now().minusSeconds(3600));
        Report newer = persistReportWithPublishDate(user, ReportType.OBSTACLE,
                ReportEnvironment.OUTDOOR, Instant.now());

        ReportFeedQuery q = new ReportFeedQuery();
        q.setSort(FeedSort.OLDEST);
        Page<Report> page = reportRepository.findFeedRecent(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertEquals(older.getReportId(), ids.get(0));
        assertEquals(newer.getReportId(), ids.get(1));
    }

    @Test
    void findFeedWithinRadius_combinesProximityWithStatusAndQ() {
        RegisteredUser user = persistUser("combo-radius@test.com");
        Report matches = persistReportWithDescription(user, "Wide RAMP needs handrail");
        matches.setStatus(ReportStatus.VERIFIED);
        matches.setLocation(GeoUtils.point4326(REF_LAT, REF_LON + 0.0003));
        reportRepository.save(matches);

        Report wrongStatus = persistReportWithDescription(user, "Wide ramp needs handrail");
        wrongStatus.setLocation(GeoUtils.point4326(REF_LAT, REF_LON + 0.0004));
        reportRepository.save(wrongStatus);
        // wrongStatus stays PENDING

        Report wrongText = persistReportWithDescription(user, "Cracked sidewalk");
        wrongText.setStatus(ReportStatus.VERIFIED);
        wrongText.setLocation(GeoUtils.point4326(REF_LAT, REF_LON + 0.0005));
        reportRepository.save(wrongText);

        ReportFeedQuery q = proximity(REF_LAT, REF_LON, 1.0);
        q.setStatus(List.of(ReportStatus.VERIFIED));
        q.setQ("ramp");
        Page<Report> page = reportRepository.findFeedWithinRadius(q, PageRequest.of(0, 10));

        List<Long> ids = page.getContent().stream().map(Report::getReportId).toList();
        assertTrue(ids.contains(matches.getReportId()));
        assertFalse(ids.contains(wrongStatus.getReportId()));
        assertFalse(ids.contains(wrongText.getReportId()));
        assertEquals(1L, page.getTotalElements());
    }

    // ───── helpers ─────────────────────────────────────────────────────────────────

    private static ReportFeedQuery proximity(double lat, double lon, double radiusKm) {
        ReportFeedQuery q = new ReportFeedQuery();
        q.setLatitude(lat);
        q.setLongitude(lon);
        q.setRadiusInKm(radiusKm);
        return q;
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

    private Report persistReportWithDescription(RegisteredUser user, String description) {
        Report r = new Report(user, GeoUtils.point4326(REF_LAT, REF_LON),
                description, ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        return reportRepository.save(r);
    }

    private Report persistReportWithVotes(RegisteredUser user, int agrees, int disagrees) {
        Report r = new Report(user, GeoUtils.point4326(REF_LAT, REF_LON),
                "test report", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        for (int i = 0; i < agrees; i++) r.incrementAgrees();
        for (int i = 0; i < disagrees; i++) r.incrementDisagrees();
        return reportRepository.save(r);
    }

    private Report persistReportWithObject(RegisteredUser user, ObjectType type, Set<IssueType> issues) {
        Report r = new Report(user, GeoUtils.point4326(REF_LAT, REF_LON),
                "test report", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        ReportObject obj = new ReportObject(r, type, issues, null);
        r.getObjects().add(obj);
        return reportRepository.save(r);
    }
}
