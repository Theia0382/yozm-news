package net.yozmnews.report.domain.report;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
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
	 * 시작의 기록을 쓴다 — 통계 스냅숏 · 생성 때 이은 직전 리포트 · 기간 날짜를 적는 사용자 시간대. 시작 저장 직전에, 같은 트랜잭션에서
	 * 부른다(시작한 행은 셋 가운데 스냅숏 · 시간대가 언제나 있다 — 테이블 제약). jsonb 칸이라 SQL 캐스트로 쓰고 버전을 올리지 않는다
	 * @param snapshot JSON 객체 하나인 문자열
	 * @param previous 직전 리포트의 ID — 없으면 null
	 * @param zone IANA 시간대 ID
	 */
	@Modifying
	@Query("""
			update reports set stats_snapshot = cast(:snapshot as jsonb), previous_report_id = :previous, zone = :zone
			where id = :id""")
	void writeStart(@Param("id") long id, @Param("snapshot") String snapshot, @Param("previous") @Nullable Long previous,
			@Param("zone") String zone);

	/**
	 * 기간 날짜를 적는 사용자 시간대를 쓴다 — 시작하지 않고 끝낼 때, 끝내는 저장 직전에 같은 트랜잭션에서 부른다(끝난 행은 시간대가 언제나 있다 —
	 * 테이블 제약). 버전을 올리지 않는다
	 * @param zone IANA 시간대 ID
	 */
	@Modifying
	@Query("update reports set zone = :zone where id = :id")
	void writeZone(@Param("id") long id, @Param("zone") String zone);

	/**
	 * 시작의 기록(통계 스냅숏 · 직전 리포트 · 시간대)을 비운다 — 다시 만들기로 생성을 기다림이 된 행을, 그 저장 직후에 같은 트랜잭션에서
	 * 부른다(시작한 행만 스냅숏이 있어야 한다 — 테이블 제약). 버전을 올리지 않는다
	 */
	@Modifying
	@Query("update reports set stats_snapshot = null, previous_report_id = null, zone = null where id = :id")
	void clearStart(@Param("id") long id);

}
