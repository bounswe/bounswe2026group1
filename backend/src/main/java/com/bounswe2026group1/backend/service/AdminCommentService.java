package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.admin.AdminCommentResponse;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class AdminCommentService {

    private final CommentRepository commentRepository;

    public Page<AdminCommentResponse> listComments(Long reportId, Long authorId, Pageable pageable) {
        Page<Comment> page;
        if (reportId != null && authorId != null) {
            page = commentRepository.findByAuthorIdAndReportReportId(authorId, reportId, pageable);
        } else if (reportId != null) {
            page = commentRepository.findByReportReportId(reportId, pageable);
        } else if (authorId != null) {
            page = commentRepository.findByAuthorId(authorId, pageable);
        } else {
            page = commentRepository.findAll(pageable);
        }
        return page.map(AdminCommentResponse::fromEntity);
    }

    @Transactional
    public void deleteComment(Long commentId) {
        if (!commentRepository.existsById(commentId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Comment not found with id: " + commentId);
        }
        commentRepository.deleteById(commentId);
    }
}
