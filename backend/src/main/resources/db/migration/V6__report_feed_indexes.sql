-- Indexes supporting filterable / sortable report feed (see ReportRepositoryImpl).
-- Each filter or ORDER BY column gets a single-column index. PostgreSQL can
-- combine these via bitmap index scans when filters are mixed, so per-combination
-- composite indexes aren't necessary at current data sizes.

CREATE INDEX IF NOT EXISTS idx_reports_status
    ON reports(status);

CREATE INDEX IF NOT EXISTS idx_reports_user_id
    ON reports(user_id);

CREATE INDEX IF NOT EXISTS idx_reports_publish_date_desc
    ON reports(publish_date DESC);

CREATE INDEX IF NOT EXISTS idx_reports_agrees_desc
    ON reports(agrees DESC);

CREATE INDEX IF NOT EXISTS idx_report_objects_object_type
    ON report_objects(object_type);

CREATE INDEX IF NOT EXISTS idx_report_object_issues_issue_type
    ON report_object_issues(issue_type);
