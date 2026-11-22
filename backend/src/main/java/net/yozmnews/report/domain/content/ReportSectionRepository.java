package net.yozmnews.report.domain.content;

import org.springframework.data.repository.CrudRepository;

/**
 * 리포트 내용 저장소 · docs/specs/report.md §4 · report-R9
 */
public interface ReportSectionRepository extends CrudRepository<ReportSection, Long> {

}
