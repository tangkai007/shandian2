package com.mars.moderation.repo;

import com.mars.moderation.ContentReport;

import java.util.List;
import java.util.Optional;

/** 举报仓储（表 {@code content_report}）。 */
public interface ContentReportRepository {

    void save(ContentReport report);

    Optional<ContentReport> findById(String reportId);

    /** 队列：按状态（{@code null} 取全部）与创建时间升序。 */
    List<ContentReport> listByStatus(ContentReport.ReportStatus status);
}
