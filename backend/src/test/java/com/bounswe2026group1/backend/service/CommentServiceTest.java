package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CommentResponse;
import com.bounswe2026group1.backend.dto.CreateCommentRequest;
import com.bounswe2026group1.backend.dto.UpdateCommentRequest;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.model.ReportEnvironment;
import com.bounswe2026group1.backend.model.ReportType;
import com.bounswe2026group1.backend.repository.CommentRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import com.bounswe2026group1.backend.util.GeoUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock private CommentRepository commentRepository;
    @Mock private ReportRepository reportRepository;
    @Mock private RegisteredUserRepository registeredUserRepository;
    @Mock private NotificationService notificationService;

    @InjectMocks
    private CommentService commentService;

    private Report report;
    private RegisteredUser author;

    @BeforeEach
    void setUp() {
        author = new RegisteredUser();
        author.setId(3L);
        author.setName("Alice");

        report = new Report(author, GeoUtils.point4326(41.0, 29.0), "issue",
                ReportType.OBSTACLE, ReportEnvironment.OUTDOOR);
        report.setReportId(18L);
    }

    @Test
    void create_persistsAndNotifies() {
        CreateCommentRequest req = new CreateCommentRequest(
                "looks good",
                new CreateCommentRequest.AuthorRef(3L),
                new CreateCommentRequest.ReportRef(18L));

        when(reportRepository.getReferenceById(18L)).thenReturn(report);
        when(registeredUserRepository.getReferenceById(3L)).thenReturn(author);

        Comment saved = new Comment();
        saved.setId(99L);
        saved.setContent("looks good");
        saved.setReport(report);
        saved.setAuthor(author);
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> {
            Comment c = inv.getArgument(0);
            c.setId(99L);
            return c;
        });

        CommentResponse out = commentService.create(req);

        assertEquals(99L, out.id());
        verify(notificationService).notifyNewComment(any(Comment.class));
        verify(commentRepository).save(any(Comment.class));
    }

    @Test
    void update_returnsMappedResponseWhenFound() {
        Comment existing = new Comment();
        existing.setId(1L);
        existing.setContent("old");
        when(commentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(commentRepository.save(any(Comment.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<CommentResponse> res = commentService.update(1L, new UpdateCommentRequest("new text"));

        assertTrue(res.isPresent());
        assertEquals("new text", res.get().content());
    }

    @Test
    void delete_returnsFalseWhenMissing() {
        when(commentRepository.existsById(42L)).thenReturn(false);
        assertFalse(commentService.delete(42L));
        verify(commentRepository, never()).deleteById(anyLong());
    }

    @Test
    void delete_removesWhenPresent() {
        when(commentRepository.existsById(7L)).thenReturn(true);
        assertTrue(commentService.delete(7L));
        verify(commentRepository).deleteById(7L);
    }

    @Test
    void getByReport_delegatesToRepository() {
        Comment c = new Comment();
        c.setId(5L);
        c.setContent("hi");
        c.setAuthor(author);
        c.setReport(report);
        when(commentRepository.findByReportReportId(18L)).thenReturn(List.of(c));

        List<CommentResponse> list = commentService.getByReport(18L);
        assertEquals(1, list.size());
        assertEquals("hi", list.get(0).content());
    }
}
