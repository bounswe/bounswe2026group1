package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.ReportResponse;
import com.bounswe2026group1.backend.model.Media;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportStatus;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.util.GeoUtils;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminReportServiceTest {

    @Mock private ReportRepository reportRepository;
    @Mock private S3MediaService s3MediaService;
    @Mock private PublicSseService publicSseService;

    @InjectMocks
    private AdminReportService adminReportService;

    private Report report;
    private RegisteredUser owner;

    @BeforeEach
    void setUp() {
        owner = new RegisteredUser();
        owner.setId(2L);
        owner.setEmail("owner@test.com");

        report = new Report(owner, GeoUtils.point4326(41.0, 29.0), "x",
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        report.setReportId(7L);
        report.setStatus(ReportStatus.PENDING);
    }

    @Test
    void listReports_mapsPage() {
        Pageable p = PageRequest.of(0, 10);
        when(reportRepository.findAll(any(Specification.class), eq(p)))
                .thenReturn(new PageImpl<>(List.of(report)));

        Page<ReportResponse> page = adminReportService.listReports(
                null, null, null, null, null, null, p);

        assertEquals(1, page.getTotalElements());
        assertEquals(7L, page.getContent().getFirst().getReportId());
    }

    @Test
    void changeStatus_updatesAndSaves() {
        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));
        when(reportRepository.save(any(Report.class))).thenAnswer(inv -> inv.getArgument(0));

        ReportResponse res = adminReportService.changeStatus(7L, ReportStatus.VERIFIED);

        assertEquals(ReportStatus.VERIFIED, report.getStatus());
        assertEquals(7L, res.getReportId());
    }

    @Test
    void changeStatus_throwsWhenMissing() {
        when(reportRepository.findById(99L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class,
                () -> adminReportService.changeStatus(99L, ReportStatus.VERIFIED));
    }

    @Test
    void deleteReport_removesMediaAndBroadcasts() {
        Media m = new Media();
        m.setFilePath("key/photo.jpg");
        report.setMediaList(List.of(m));

        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));

        adminReportService.deleteReport(7L);

        verify(s3MediaService).deleteFile("key/photo.jpg");
        verify(reportRepository).delete(report);
        verify(publicSseService).broadcastReportDeleted(7L);
    }

    @Test
    void deleteReport_throwsWhenMissing() {
        when(reportRepository.findById(404L)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class,
                () -> adminReportService.deleteReport(404L));

        verify(s3MediaService, never()).deleteFile(any());
        verify(publicSseService, never()).broadcastReportDeleted(any());
    }

    @Test
    void deleteReport_withMultipleMedia_deletesEach() {
        Media a = new Media();
        a.setFilePath("key/a.jpg");
        Media b = new Media();
        b.setFilePath("key/b.jpg");
        report.setMediaList(List.of(a, b));

        when(reportRepository.findById(7L)).thenReturn(Optional.of(report));

        adminReportService.deleteReport(7L);

        verify(s3MediaService).deleteFile("key/a.jpg");
        verify(s3MediaService).deleteFile("key/b.jpg");
    }

    // ───── Specification (buildSpec) branch coverage ────────────────────────
    // The filter lambda only executes when JPA evaluates the Specification.
    // Capture the spec passed to repository.findAll, then invoke its toPredicate
    // against Criteria mocks to exercise every if-branch.

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void buildSpec_appliesEveryFilterWhenAllSupplied() {
        Pageable p = PageRequest.of(0, 10);
        when(reportRepository.findAll(any(Specification.class), eq(p)))
                .thenReturn(new PageImpl<>(List.of(report)));

        Instant from = Instant.parse("2026-01-01T00:00:00Z");
        Instant to   = Instant.parse("2026-12-31T23:59:59Z");
        adminReportService.listReports(
                ReportStatus.PENDING, 42L, ReportEnvironment.INDOOR, ReportType.OBSTACLE,
                from, to, p);

        ArgumentCaptor<Specification<Report>> captor =
                ArgumentCaptor.forClass(Specification.class);
        verify(reportRepository).findAll(captor.capture(), eq(p));

        Root root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);

        // One sentinel path is enough — the production code only uses these as opaque
        // arguments to the cb.* matchers (also mocked), so we don't need per-field paths.
        Path categoryPath = mock(Path.class);
        Path leaf = mock(Path.class);

        when(root.get(anyString())).thenReturn(leaf);
        when(root.get("category")).thenReturn(categoryPath);
        when(categoryPath.get(anyString())).thenReturn(leaf);

        Predicate p1 = mock(Predicate.class);
        // Force the equal(Expression<?>, Object) overload — production passes
        // an enum/Long as the second arg, so the (Expression, Expression) overload
        // would silently swallow the matcher.
        when(cb.equal(any(jakarta.persistence.criteria.Expression.class), any(Object.class))).thenReturn(p1);
        when(cb.greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Instant.class))).thenReturn(p1);
        when(cb.lessThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Instant.class))).thenReturn(p1);
        Predicate combined = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(combined);

        Predicate result = captor.getValue().toPredicate(root, query, cb);

        assertEquals(combined, result);
        // All four equality filters fire
        verify(cb).equal((jakarta.persistence.criteria.Expression<?>) leaf, (Object) ReportStatus.PENDING);
        verify(cb).equal((jakarta.persistence.criteria.Expression<?>) leaf, (Object) 42L);
        verify(cb).equal((jakarta.persistence.criteria.Expression<?>) leaf, (Object) ReportEnvironment.INDOOR);
        verify(cb).equal((jakarta.persistence.criteria.Expression<?>) leaf, (Object) ReportType.OBSTACLE);
        // Both date bounds fire
        verify(cb).greaterThanOrEqualTo(leaf, from);
        verify(cb).lessThanOrEqualTo(leaf, to);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Test
    void buildSpec_appliesNoFiltersWhenAllNull() {
        Pageable p = PageRequest.of(0, 10);
        when(reportRepository.findAll(any(Specification.class), eq(p)))
                .thenReturn(new PageImpl<>(List.of()));

        adminReportService.listReports(null, null, null, null, null, null, p);

        ArgumentCaptor<Specification<Report>> captor =
                ArgumentCaptor.forClass(Specification.class);
        verify(reportRepository).findAll(captor.capture(), eq(p));

        Root root = mock(Root.class);
        CriteriaQuery<?> query = mock(CriteriaQuery.class);
        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        Predicate empty = mock(Predicate.class);
        when(cb.and(any(Predicate[].class))).thenReturn(empty);

        Predicate result = captor.getValue().toPredicate(root, query, cb);

        assertEquals(empty, result);
        // No filter predicates were built
        verify(cb, never()).equal(any(jakarta.persistence.criteria.Expression.class), any(Object.class));
        verify(cb, never()).greaterThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Instant.class));
        verify(cb, never()).lessThanOrEqualTo(any(jakarta.persistence.criteria.Expression.class), any(Instant.class));
        verify(root, never()).get(any(String.class));
    }
}
