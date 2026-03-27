package com.bounswe2026group1.backend.service;

import com.bounswe2026group1.backend.model.Comment;
import com.bounswe2026group1.backend.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository commentRepository;

    public List<Comment> getAll() {
        return commentRepository.findAll();
    }

    public Optional<Comment> getById(Long id) {
        return commentRepository.findById(id);
    }

    public List<Comment> getByAuthor(Long id) {
        return commentRepository.findByAuthorId(id);
    }

    public Comment create(Comment comment) {
        return commentRepository.save(comment);
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
