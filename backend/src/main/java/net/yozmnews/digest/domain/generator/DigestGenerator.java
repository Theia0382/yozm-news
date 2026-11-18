package net.yozmnews.digest.domain.generator;

/**
 * 다이제스트 생성기 포트 — docs/specs/digest.md §4
 */
public interface DigestGenerator {

	/**
	 * 요청으로 출력 원문을 만든다 — 파싱 · 검증하지 않는다. 트랜잭션 밖에서 불린다. 예상한 실패는 {@link GenerationOutcome.Failed},
	 * 예외는 예상 밖의 것만이다 — docs/specs/digest.md §4
	 */
	GenerationOutcome generate(DigestRequest request);

}
