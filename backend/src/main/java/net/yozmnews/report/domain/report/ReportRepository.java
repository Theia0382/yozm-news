package net.yozmnews.report.domain.report;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

/**
 * 리포트 상태 저장소 · docs/specs/report.md §4 · report-R1 · report-R13
 */
public interface ReportRepository extends CrudRepository<Report, Long> {

	/** 리포트 행을 잠그고 읽는다 — 생성의 시작 · 끝이 쓰기 전에 부른다. 트랜잭션 안에서 부른다 */
	@Query("select * from reports where id = :id for update")
	Optional<Report> lock(@Param("id") long id);

	/**
	 * 통계 스냅숏을 쓴다 — 시작 저장 직전에, 같은 트랜잭션에서 부른다. jsonb 칸이라 SQL 캐스트로 쓰고 버전을 올리지 않는다
	 * @param snapshot JSON 객체 하나인 문자열
	 */
	@Modifying
	@Query("update reports set stats_snapshot = cast(:snapshot as jsonb) where id = :id")
	void writeSnapshot(@Param("id") long id, @Param("snapshot") String snapshot);

}
