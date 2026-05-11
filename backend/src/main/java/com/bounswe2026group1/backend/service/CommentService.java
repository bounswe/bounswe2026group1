package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CommentAnnotationResponse;
import com.bounswe2026group1.backend.dto.CommentResponse;
import com.bounswe2026group1.backend.dto.CreateCommentRequest;
import com.bounswe2026group1.backend.dto.UpdateCommentRequest;
import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.repository.CommentRepository;
import com.bounswe2026group1.backend.repository.ReportRepository;
import com.bounswe2026group1.backend.repository.RegisteredUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;
    private final ReportRepository reportRepository;
    private final RegisteredUserRepository registeredUserRepository;
    private final NotificationService notificationService;

    public List<CommentResponse> getAll() {
        return commentRepository.findAll().stream().map(CommentResponse::fromEntity).toList();
    }

    public Optional<CommentResponse> getById(Long id) {
        return commentRepository.findById(id).map(CommentResponse::fromEntity);
    }

    public List<CommentResponse> getByAuthor(Long id) {
        return commentRepository.findByAuthorId(id).stream().map(CommentResponse::fromEntity).toList();
    }

    public List<CommentResponse> getByReport(Long reportId) {
        return commentRepository.findByReportReportId(reportId).stream().map(CommentResponse::fromEntity).toList();
    }

    public Optional<CommentAnnotationResponse> getByIdAsAnnotation(Long id, String apiBase) {
        return commentRepository.findById(id).map(c -> CommentAnnotationResponse.fromEntity(c, apiBase));
    }

    public List<CommentAnnotationResponse> getByReportAsAnnotation(Long reportId, String apiBase) {
        return commentRepository.findByReportReportId(reportId).stream()
                .map(c -> CommentAnnotationResponse.fromEntity(c, apiBase))
                .toList();
    }

    @Transactional
    public CommentResponse create(CreateCommentRequest req) {
        Comment comment = new Comment();
        comment.setContent(req.content());
        comment.setReport(reportRepository.getReferenceById(req.report().reportId()));
        comment.setAuthor(registeredUserRepository.getReferenceById(req.author().id()));
        Comment saved = commentRepository.save(comment);
        notificationService.notifyNewComment(saved);
        return CommentResponse.fromEntity(saved);
    }

    @Transactional
    public Optional<CommentResponse> update(Long id, UpdateCommentRequest req) {
        return commentRepository.findById(id).map(existing -> {
            existing.setContent(req.content());
            return CommentResponse.fromEntity(commentRepository.save(existing));
        });
    }

    public boolean delete(Long id) {
        if (!commentRepository.existsById(id)) return false;
        commentRepository.deleteById(id);
        return true;
    }
}
