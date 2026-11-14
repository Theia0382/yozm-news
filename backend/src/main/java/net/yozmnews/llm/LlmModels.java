package net.yozmnews.llm;

import java.util.List;
import java.util.Optional;

/**
 * 허용 모델 목록 조회 — docs/specs/llm.md §3 · llm-R2 ~ R4. 두 역할 공통이고 실행 중에 바뀌지 않는다
 */
public interface LlmModels {

	/** 공급자 선언 순, 공급자 안에서는 파일에 적힌 순 */
	List<LlmModel> all();

	/** 그 공급자의 모델 — 없으면 빈 목록 */
	List<LlmModel> of(Provider provider);

	/** 목록에 없는 모델이면 빈 값 */
	Optional<LlmModel> find(Provider provider, String id);

}
