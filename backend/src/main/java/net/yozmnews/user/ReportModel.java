package net.yozmnews.user;

import java.util.Objects;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.ReasoningEffort;

/**
 * 리포트를 만들 때 쓸 모델과 추론 강도 — {@link ReportModelDirectory} 가 낸다. docs/specs/user.md §3
 *
 * @param model 쓸 모델(user-R51)
 * @param effort 추론 강도 — 다이제스트와 함께 쓰는 설정(user-R59)
 */
public record ReportModel(LlmModel model, ReasoningEffort effort) {

	public ReportModel {
		Objects.requireNonNull(model, "model");
		Objects.requireNonNull(effort, "effort");
	}

}
