package net.yozmnews.report.domain.generator;

import java.util.Objects;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.platform.UserId;
import net.yozmnews.report.domain.alias.ReportAliases;

/**
 * 생성 요청 · docs/specs/report.md §4 · report-R2
 *
 * @param userId 리포트의 사용자 — 그 사용자의 저장한 키로 부른다
 * @param model 쓸 모델
 * @param aliases 별칭을 매긴 갈래 · 인용 대상 — 갈래가 하나 이상이다
 * @param outputSchema 이번 요청의 출력 JSON Schema 문서
 */
public record ReportRequest(UserId userId, LlmModel model, ReportAliases aliases, String outputSchema) {

	/**
	 * @throws IllegalArgumentException 갈래가 하나도 없을 때 — 갈래 0 개는 생성기를 부르지 않는다
	 */
	public ReportRequest {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(aliases, "aliases");
		Objects.requireNonNull(outputSchema, "outputSchema");
		if (aliases.hasNoSections()) {
			throw new IllegalArgumentException("갈래가 없는 요청이다");
		}
	}

}
