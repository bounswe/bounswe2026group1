package com.bounswe2026group1.backend.repository;

import com.bounswe2026group1.backend.model.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByAuthorId(Long id);
    List<Comment> findByReportReportId(Long reportId);

    /** Returns the user ids of everyone who has commented on a report.
     *  Used by the notification audience fan-out, so we project ids only. */
    @Query("select distinct c.author.id from Comment c where c.report.reportId = :reportId")
    List<Long> findCommenterUserIdsByReportId(@Param("reportId") Long reportId);
}
