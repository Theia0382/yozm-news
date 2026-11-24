package net.yozmnews.report.adapter.out.llm;

import java.util.Objects;

import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.StoredGeneration;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.domain.generator.ReportFailures;
import net.yozmnews.report.domain.generator.ReportGenerator;
import net.yozmnews.report.domain.generator.ReportOutcome;
import net.yozmnews.report.domain.generator.ReportRequest;
import net.yozmnews.report.domain.generator.TokenUsage;
import net.yozmnews.report.domain.prompt.ReportPrompt;
import net.yozmnews.report.domain.prompt.ReportPromptRules;

/**
 * 실제 생성기 — 사용자의 저장한 키로 {@code llm} 관문의 생성 호출을 부르고 결과를 생성기 결과로 옮긴다 · docs/specs/report.md §4 ·
 * report-R24
 * <p>
 * 판단이 없다 — 요청의 프롬프트 · 쓸 모델 · 추론 강도와 갈래 수로 낸 출력 본문 토큰({@link ReportPromptRules#bodyTokens})을 관문 요청으로,
 * 관문 결과를 생성기 결과로 옮기기만 한다. 키 · 프롬프트 · 응답 본문을 로그에 남기지 않는다. 다시 묻기는 부르는 쪽(유스케이스)이 하고, 일시 장애의
 * 다시 보내기는 관문이 한다.
 * <p>
 * 결선은 {@link LlmReportGeneratorConfiguration}(profile {@code local} 밖)이다. 공개인 것은 {@code local} 문맥의 흐름 시험이 관문의
 * 로컬 대체물로 이 생성기를 직접 만들기 때문이다 — 모듈 밖에서는 포트({@link ReportGenerator})로만 닿는다.
 */
public final class LlmReportGenerator implements ReportGenerator {

	/** 쓸 모델 공급자의 저장한 키가 없을 때의 실패 메시지 */
	public static final String KEY_MISSING = "쓸 모델 공급자의 저장한 키가 없다";

	private final LlmClientFactory gateway;

	public LlmReportGenerator(LlmClientFactory gateway) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
	}

	@Override
	public ReportOutcome generate(ReportRequest request) {
		Objects.requireNonNull(request, "request");
		ReportPrompt prompt = request.prompt();
		GenerationRequest call = new GenerationRequest(request.model(), prompt.instructions(), prompt.userContent(),
				prompt.outputSchema(), request.effort(), ReportPromptRules.bodyTokens(request.aliases().sections().size()));
		return switch (this.gateway.generateStored(request.userId(), call)) {
			case StoredGeneration.Missing missing -> new ReportOutcome.Failed(ReportFailure.KEY_MISSING, KEY_MISSING);
			case StoredGeneration.Done done -> switch (done.result()) {
				case GenerationResult.Generated generated -> new ReportOutcome.Generated(generated.text(),
						new TokenUsage(generated.inputTokens(), generated.outputTokens()), generated.stop());
				case GenerationResult.Failed failed ->
					new ReportOutcome.Failed(ReportFailures.of(failed.kind()), failed.message());
			};
		};
	}

}
