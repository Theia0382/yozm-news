package net.yozmnews.digest.domain.generator;

/**
 * 다이제스트 생성기 포트 — docs/specs/digest.md §4
 */
public interface DigestGenerator {

	/** 후보로 다이제스트 출력 원문을 만든다 — docs/specs/digest.md §4 */
	GeneratedOutput generate(DigestRequest request);

}
