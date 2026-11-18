package net.yozmnews.digest.adapter.out.llm;

import java.util.Objects;

import net.yozmnews.digest.domain.digest.DigestFailure;
import net.yozmnews.digest.domain.generator.DigestGenerator;
import net.yozmnews.digest.domain.generator.DigestRequest;
import net.yozmnews.digest.domain.generator.GenerationFailures;
import net.yozmnews.digest.domain.generator.GenerationOutcome;
import net.yozmnews.digest.domain.generator.TokenUsage;
import net.yozmnews.digest.domain.prompt.DigestPrompt;
import net.yozmnews.llm.GenerationRequest;
import net.yozmnews.llm.GenerationResult;
import net.yozmnews.llm.LlmClientFactory;
import net.yozmnews.llm.StoredGeneration;

/**
 * 실제 생성기 — 사용자의 저장한 키로 {@code llm} 관문의 생성 호출을 부르고 결과를 생성기 결과로 옮긴다 · docs/specs/digest.md §4
 * <p>
 * 판단이 없다 — 요청의 프롬프트 · 쓸 모델 · 추론 강도를 관문 요청으로, 관문 결과를 생성기 결과로 옮기기만 한다. 본문 몫은 관문의 상한
 * ({@link GenerationRequest#MAX_BODY_TOKENS})이고 글 길이는 지시가 정한다. 키 · 프롬프트 · 응답 본문을 로그에 남기지 않는다. 다시 묻기는
 * 부르는 쪽(유스케이스)이 하고, 일시 장애의 다시 보내기는 관문이 한다.
 * <p>
 * 결선은 {@link LlmDigestGeneratorConfiguration}(profile {@code local} 밖)이다. 공개인 것은 {@code local} 문맥의 흐름 시험이 관문의
 * 로컬 대체물로 이 생성기를 직접 만들기 때문이다 — 모듈 밖에서는 포트({@link DigestGenerator})로만 닿는다.
 */
public final class LlmDigestGenerator implements DigestGenerator {

	/** 쓸 모델 공급자의 저장한 키가 없을 때의 실패 메시지 */
	public static final String KEY_MISSING = "쓸 모델 공급자의 저장한 키가 없다";

	private final LlmClientFactory gateway;

	public LlmDigestGenerator(LlmClientFactory gateway) {
		this.gateway = Objects.requireNonNull(gateway, "gateway");
	}

	@Override
	public GenerationOutcome generate(DigestRequest request) {
		Objects.requireNonNull(request, "request");
		DigestPrompt prompt = request.prompt();
		GenerationRequest call = new GenerationRequest(request.model(), prompt.instructions(), prompt.userContent(),
				prompt.outputSchema(), request.effort(), GenerationRequest.MAX_BODY_TOKENS);
		return switch (this.gateway.generateStored(request.userId(), call)) {
			case StoredGeneration.Missing missing -> new GenerationOutcome.Failed(DigestFailure.KEY_MISSING, KEY_MISSING);
			case StoredGeneration.Done done -> switch (done.result()) {
				case GenerationResult.Generated generated -> new GenerationOutcome.Generated(generated.text(),
						new TokenUsage(generated.inputTokens(), generated.outputTokens()), generated.stop());
				case GenerationResult.Failed failed ->
					new GenerationOutcome.Failed(GenerationFailures.of(failed.kind()), failed.message());
			};
		};
	}

}
