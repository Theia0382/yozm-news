package net.yozmnews.report.domain.generator;

/**
 * 리포트 생성기 포트 — 외부 의존(LLM)의 경계 · docs/specs/report.md §4 · report-R2
 * <p>
 * 출력을 파싱 · 검증하지 않는다 — 모든 구현의 출력이 같은 읽기 · 대조를 지난다. 트랜잭션 밖에서 불린다. 예상한 실패는
 * {@link ReportOutcome.Failed} 이고, 예외는 예상 밖의 것만이다.
 */
public interface ReportGenerator {

	ReportOutcome generate(ReportRequest request);

}
