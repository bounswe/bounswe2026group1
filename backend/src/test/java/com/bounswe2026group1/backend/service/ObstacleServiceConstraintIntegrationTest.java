package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.IssueType;
import com.bounswe2026group1.backend.model.Location;
import com.bounswe2026group1.backend.model.ObjectType;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportObject;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.model.RoutingConstraint;
import com.bounswe2026group1.backend.model.RoutingPreset;
import com.bounswe2026group1.backend.model.UserRole;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.support.AbstractPostgisIntegrationTest;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.node.ObjectNode;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full-stack test for the avoid-polygon filter — exercises ObstacleService
 * against a real PostGIS-backed datasource, with real Reports persisted via
 * the JPA repository. Proves the constraint→hazard mapping flows end-to-end
 * through the persistence layer (not just the in-memory mock used by
 * ObstacleServiceTest).
 */
@SpringBootTest
class ObstacleServiceConstraintIntegrationTest extends AbstractPostgisIntegrationTest {

    @Autowired private ObstacleService obstacleService;
    @Autowired private ReportRepository reportRepository;
    @Autowired private RegisteredUserRepository registeredUserRepository;

    private RegisteredUser author;

    @BeforeEach
    @Transactional
    void seedReports() {
        // Tests share the singleton PostGIS container, so clean state by tag.
        reportRepository.deleteAll();
        registeredUserRepository.findByEmail("obstacle-int@test.com").ifPresent(registeredUserRepository::delete);

        author = new RegisteredUser();
        author.setName("Obstacle Tester");
        author.setEmail("obstacle-int@test.com");
        author.setPassword("hashed-password-stub");
        author.setRole(UserRole.USER);
        author = registeredUserRepository.save(author);

        // Three VERIFIED OBSTACLE reports, each with a distinct hazard.
        persistObstacle(41.0850, 29.0451, ObjectType.STAIR, IssueType.MISSING_HANDRAIL);
        persistObstacle(41.0851, 29.0452, ObjectType.SIDEWALK, IssueType.BLOCKED);
        persistObstacle(41.0852, 29.0453, ObjectType.RAMP, IssueType.TOO_STEEP);
    }

    @Test
    void buildAvoidPolygons_emptyConstraints_includesAllVerifiedObstaclesFromDb() {
        ObjectNode polygons = obstacleService.buildAvoidPolygons(Set.of());

        assertThat(polygons).isNotNull();
        assertThat(polygons.get("coordinates")).hasSize(3);
    }

    @Test
    void buildAvoidPolygons_avoidStairsOnly_keepsOnlyStairReport() {
        ObjectNode polygons = obstacleService.buildAvoidPolygons(EnumSet.of(RoutingConstraint.AVOID_STAIRS));

        assertThat(polygons).isNotNull();
        assertThat(polygons.get("coordinates")).hasSize(1);
    }

    @Test
    void buildAvoidPolygons_avoidBlockedSidewalks_keepsOnlySidewalkReport() {
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                EnumSet.of(RoutingConstraint.AVOID_BLOCKED_OR_MISSING_SIDEWALKS));

        assertThat(polygons).isNotNull();
        assertThat(polygons.get("coordinates")).hasSize(1);
    }

    @Test
    void buildAvoidPolygons_avoidUnsafeRamps_keepsOnlyRampReport() {
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                EnumSet.of(RoutingConstraint.AVOID_UNSAFE_RAMPS));

        assertThat(polygons).isNotNull();
        assertThat(polygons.get("coordinates")).hasSize(1);
    }

    @Test
    void buildAvoidPolygons_wheelchairUserPreset_keepsRelevantReports() {
        // WHEELCHAIR_USER bundles AVOID_STAIRS, REQUIRE_RAMPS, AVOID_UNSAFE_RAMPS,
        // AVOID_NARROW_SIDEWALKS, AVOID_BLOCKED_OR_MISSING_SIDEWALKS.
        // Of our seeded reports: stair (matches AVOID_STAIRS), blocked sidewalk
        // (matches AVOID_BLOCKED), and ramp-too-steep (matches AVOID_UNSAFE_RAMPS) all match.
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                RoutingPreset.WHEELCHAIR_USER.getConstraints());

        assertThat(polygons).isNotNull();
        assertThat(polygons.get("coordinates")).hasSize(3);
    }

    @Test
    void buildAvoidPolygons_blindOrLowVisionPreset_filtersOutWheelchairOnlyReports() {
        // BLIND_OR_LOW_VISION bundles AVOID_LOW_CLEARANCE, AVOID_BLOCKED_OR_MISSING_SIDEWALKS,
        // AVOID_DAMAGED_TACTILE_PAVING. Only the blocked-sidewalk report matches; the
        // wheelchair-relevant stair and ramp reports are filtered out.
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                RoutingPreset.BLIND_OR_LOW_VISION.getConstraints());

        assertThat(polygons).isNotNull();
        assertThat(polygons.get("coordinates")).hasSize(1);
    }

    @Test
    void buildAvoidPolygons_constraintWithNoMatches_returnsNull() {
        // None of the seeded reports have NO_TACTILE_PAVING, so the constraint set is empty
        // after filtering and the polygon set collapses to null.
        ObjectNode polygons = obstacleService.buildAvoidPolygons(
                EnumSet.of(RoutingConstraint.AVOID_DAMAGED_TACTILE_PAVING));

        assertThat(polygons).isNull();
    }

    // -------------------------------------------------------------------------
    // Regression: native-query enum binding (issue: prod /api/routes 500)
    // -------------------------------------------------------------------------

    @Test
    void findObstaclesOnPath_executesAgainstPostgisWithoutEnumBindingError() {
        // findObstaclesOnPath() uses native query findReportsInBoundingBoxWithStatuses,
        // which historically broke because Hibernate binds Collection<ReportStatus> as
        // smallint ordinals in native queries (clashing with the varchar status column).
        // The fix is to pass status names (strings); this test fails if anyone reverts.
        Location p1 = new Location(41.0850, 29.0451);
        Location p2 = new Location(41.0852, 29.0453);
        List<Report> obstacles = obstacleService.findObstaclesOnPath(List.of(p1, p2));

        // We don't assert a specific count — what matters is the call doesn't throw
        // an InvalidDataAccessResourceUsageException ("operator does not exist:
        // character varying = smallint"). Returning an empty list is fine; throwing isn't.
        assertThat(obstacles).isNotNull();
    }

    @Test
    void findObstaclesOnPath_emptyConstraints_returnsEveryNearbyObstacle() {
        // Path that brushes past all three seeded VERIFIED OUTDOOR obstacles
        // (stair, sidewalk, ramp at 41.0850/0851/0852).
        Location p1 = new Location(41.0850, 29.0450);
        Location p2 = new Location(41.0853, 29.0454);

        List<Report> obstacles = obstacleService.findObstaclesOnPath(List.of(p1, p2), Set.of());

        assertThat(obstacles).hasSize(3);
    }

    @Test
    void findObstaclesOnPath_withNonMatchingConstraint_filtersOutObstaclesUserDoesntCareAbout() {
        // The user only cares about low overhead clearance — none of the seeded
        // reports match that hazard. The fastest route may still graze them, but
        // the response's hasObstacles flag should be false: warning the user about
        // an obstacle they explicitly don't care about contradicts the prefs design.
        Location p1 = new Location(41.0850, 29.0450);
        Location p2 = new Location(41.0853, 29.0454);

        List<Report> obstacles = obstacleService.findObstaclesOnPath(List.of(p1, p2),
                EnumSet.of(RoutingConstraint.AVOID_LOW_CLEARANCE));

        assertThat(obstacles).isEmpty();
    }

    @Test
    void findObstaclesOnPath_withMatchingConstraint_keepsRelevantObstaclesOnly() {
        // The user has AVOID_BLOCKED_OR_MISSING_SIDEWALKS — only the seeded
        // sidewalk-blocked report should survive.
        Location p1 = new Location(41.0850, 29.0450);
        Location p2 = new Location(41.0853, 29.0454);

        List<Report> obstacles = obstacleService.findObstaclesOnPath(List.of(p1, p2),
                EnumSet.of(RoutingConstraint.AVOID_BLOCKED_OR_MISSING_SIDEWALKS));

        assertThat(obstacles).hasSize(1);
        assertThat(obstacles.get(0).getDescription()).contains("BLOCKED");
    }

    @Test
    void findClosestRampInBoundingBox_executesAgainstPostgisWithoutEnumBindingError() {
        // findClosestRampInBoundingBox uses native query findByTypeInBoundingBoxWithStatuses,
        // which has the same enum-binding pitfall on both the type and statuses params.
        Location start = new Location(41.0840, 29.0440);
        Location end = new Location(41.0860, 29.0470);
        // Returning null is a valid outcome (no FEATURE-RAMP reports seeded).
        // The test guards only against the SQL/Hibernate exception.
        Report ramp = obstacleService.findClosestRampInBoundingBox(start, end);
        assertThat(ramp).isNull();
    }

    @Test
    void buildAvoidPolygons_pendingReportNeverFiltersIn_evenWithMatchingConstraint() {
        // Add a PENDING report whose hazard matches a constraint — must not appear in polygons
        // because only VERIFIED reports flow through.
        Report pending = new Report(author, GeoUtils.point4326(41.0853, 29.0454),
                "Pending stairs", ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        pending.setStatus(ReportStatus.PENDING);
        ReportObject obj = new ReportObject(pending, ObjectType.STAIR, EnumSet.of(IssueType.MISSING_HANDRAIL), null);
        pending.getObjects().add(obj);
        reportRepository.save(pending);

        ObjectNode polygons = obstacleService.buildAvoidPolygons(EnumSet.of(RoutingConstraint.AVOID_STAIRS));

        assertThat(polygons).isNotNull();
        // Still 1 (the seeded VERIFIED stair report) — the PENDING one was rejected upstream.
        assertThat(polygons.get("coordinates")).hasSize(1);
    }

    @Test
    void buildAvoidPolygons_indoorObstacles_areFilteredOutOfTheBaselineToo() {
        // Indoor obstacles (broken elevators, heavy doors) must never become avoid
        // polygons — ORS foot-walking can't navigate inside buildings, so a polygon
        // around an indoor point is geometrically meaningless.
        Report indoorElevator = new Report(author, GeoUtils.point4326(41.0855, 29.0456),
                "Indoor broken elevator", ReportType.OBSTACLE, ReportEnvironment.INDOOR);
        indoorElevator.setStatus(ReportStatus.VERIFIED);
        indoorElevator.getObjects().add(new ReportObject(indoorElevator,
                ObjectType.ELEVATOR, EnumSet.of(IssueType.OUT_OF_SERVICE), null));
        reportRepository.save(indoorElevator);

        // Empty constraints → baseline behaviour (every VERIFIED OUTDOOR obstacle).
        ObjectNode polygons = obstacleService.buildAvoidPolygons(Set.of());

        assertThat(polygons).isNotNull();
        // Still 3 (the original seeded OUTDOOR obstacles) — the new INDOOR one is excluded.
        assertThat(polygons.get("coordinates")).hasSize(3);
    }

    private void persistObstacle(double lat, double lon, ObjectType objectType, IssueType issue) {
        Report r = new Report(author, GeoUtils.point4326(lat, lon),
                objectType + " " + issue, ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        r.setStatus(ReportStatus.VERIFIED);
        Report saved = reportRepository.save(r);
        Set<IssueType> issues = new HashSet<>(List.of(issue));
        saved.getObjects().add(new ReportObject(saved, objectType, issues, null));
        reportRepository.save(saved);
    }
}
