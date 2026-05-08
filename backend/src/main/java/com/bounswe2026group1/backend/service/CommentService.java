package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.dto.CreateCommentRequest;
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

    public List<Comment> getAll() {
        return commentRepository.findAll();
    }

    public Optional<Comment> getById(Long id) {
        return commentRepository.findById(id);
    }

    public List<Comment> getByAuthor(Long id) {
        return commentRepository.findByAuthorId(id);
    }

    public List<Comment> getByReport(Long reportId) {
        return commentRepository.findByReportReportId(reportId);
    }

    @Transactional
    public Comment create(CreateCommentRequest req) {
        Comment comment = new Comment();
        comment.setContent(req.content());
        comment.setReport(reportRepository.getReferenceById(req.report().reportId()));
        comment.setAuthor(registeredUserRepository.getReferenceById(req.author().id()));
        Comment saved = commentRepository.save(comment);
        notificationService.notifyNewComment(saved);
        return saved;
    }

    public Optional<Comment> update(Long id, Comment updated) {
        return commentRepository.findById(id).map(existing -> {
            existing.setContent(updated.getContent());
            return commentRepository.save(existing);
        });
    }

    public boolean delete(Long id) {
        if (!commentRepository.existsById(id)) return false;
        commentRepository.deleteById(id);
        return true;
    }
}
