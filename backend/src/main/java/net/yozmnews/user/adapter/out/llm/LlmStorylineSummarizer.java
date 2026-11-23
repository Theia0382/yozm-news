package net.yozmnews.user.adapter.out.llm;

import java.util.Objects;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.LlmFailureKind;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.user.domain.summary.StorylineSummarizer;
import net.yozmnews.user.domain.summary.SummaryFailure;
import net.yozmnews.user.domain.summary.SummaryOutcome;
import net.yozmnews.user.domain.summary.SummaryPrompt;
import net.yozmnews.user.domain.summary.SummaryRequest;
import net.yozmnews.user.domain.summary.SummaryRules;

/**
 * 실제 정리 생성기 — 사용자의 저장한 키로 {@code llm} 관문의 생성 호출을 부른다. docs/specs/user.md §4
 * <p>
 * 키를 다루지 않는다 — 복호화와 공급자 호출은 관문의 것이다. 추론 강도와 출력 본문 토큰 상한은 {@link SummaryRules} 의 것이다.
 */
public final class LlmStorylineSummarizer implements StorylineSummarizer {

	private final LlmClientFactory gateway;

	public LlmStorylineSummarizer(LlmClientFactory gateway) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
	}

	@Override
	public SummaryOutcome summarize(SummaryRequest request) {
		Objects.requireNonNull(request, "request");
		SummaryPrompt prompt = request.prompt();
		GenerationRequest call = new GenerationRequest(request.model(), prompt.instructions(), prompt.userContent(),
				prompt.outputSchema(), SummaryRules.EFFORT, SummaryRules.bodyTokens(prompt.aliases().size()));
		return switch (this.gateway.generateStored(request.user(), call)) {
			case StoredGeneration.Missing missing -> new SummaryOutcome.KeyMissing();
			case StoredGeneration.Done done -> switch (done.result()) {
				case GenerationResult.Generated generated -> new SummaryOutcome.Generated(generated.text(),
						generated.inputTokens(), generated.outputTokens(), generated.stop());
				case GenerationResult.Failed failed -> new SummaryOutcome.Failed(failure(failed.kind()));
			};
		};
	}

	private static SummaryFailure failure(LlmFailureKind kind) {
		return switch (kind) {
			case INVALID_KEY -> SummaryFailure.INVALID_KEY;
			case PERMISSION_DENIED -> SummaryFailure.PERMISSION_DENIED;
			case QUOTA_EXHAUSTED -> SummaryFailure.QUOTA_EXHAUSTED;
			case TRANSIENT -> SummaryFailure.PROVIDER_UNAVAILABLE;
			case UNEXPECTED -> SummaryFailure.UNEXPECTED;
		};
	}

}
