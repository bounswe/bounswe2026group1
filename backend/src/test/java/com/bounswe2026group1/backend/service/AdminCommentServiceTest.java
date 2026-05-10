package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminCommentResponse;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.model.RegisteredUser;
import com.bounswe2026group1.backend.model.Report;
import com.bounswe2026group1.backend.repository.CommentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminCommentServiceTest {

    @Mock private CommentRepository commentRepository;

    @InjectMocks
    private AdminCommentService adminCommentService;

    @Test
    void listComments_allBranch_usesFindAllWhenNoFilters() {
        Pageable p = PageRequest.of(0, 5);
        RegisteredUser author = new RegisteredUser();
        author.setId(9L);
        author.setName("Bob");
        Report rep = new Report();
        rep.setReportId(44L);

        Comment c = new Comment();
        c.setId(1L);
        c.setContent("hi");
        c.setAuthor(author);
        c.setReport(rep);
        when(commentRepository.findAll(p)).thenReturn(new PageImpl<>(List.of(c)));

        Page<AdminCommentResponse> page = adminCommentService.listComments(null, null, p);

        assertEquals(1, page.getTotalElements());
        verify(commentRepository).findAll(p);
    }

    @Test
    void listComments_filtersByReportAndAuthor() {
        Pageable p = PageRequest.of(0, 5);
        when(commentRepository.findByAuthorIdAndReportReportId(2L, 9L, p))
                .thenReturn(new PageImpl<>(List.of()));

        adminCommentService.listComments(9L, 2L, p);

        verify(commentRepository).findByAuthorIdAndReportReportId(2L, 9L, p);
    }

    @Test
    void deleteComment_throwsWhenMissing() {
        when(commentRepository.existsById(5L)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> adminCommentService.deleteComment(5L));
        verify(commentRepository, never()).deleteById(anyLong());
    }

    @Test
    void deleteComment_removesWhenPresent() {
        when(commentRepository.existsById(5L)).thenReturn(true);
        adminCommentService.deleteComment(5L);
        verify(commentRepository).deleteById(5L);
    }
}
