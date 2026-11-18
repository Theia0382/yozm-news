package net.yozmnews.digest.domain.generator;

import java.util.Objects;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.llm.GenerationStop;

/**
 * 생성기 호출 한 번의 결과 — docs/specs/digest.md §4
 * <p>
 * 생성기는 출력을 파싱 · 검증하지 않는다 — 읽기와 다시 묻기는 부르는 쪽이 한다. 예상한 실패는 {@link Failed} 이고, 예외는 예상 밖의 것만이다.
 */
public sealed interface GenerationOutcome {

	/**
	 * 공급자가 답했다 — 끝까지 썼든 잘렸든 거절했든 과금된 응답이다
	 * @param text 출력 원문 — 잘림 · 거절이면 스키마에 맞지 않거나 비었을 수 있다
	 * @param usage 공급자가 센 토큰
	 * @param stop 종료 사유
	 */
	record Generated(String text, TokenUsage usage, GenerationStop stop) implements GenerationOutcome {

		public Generated {
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(usage, "usage");
			Objects.requireNonNull(stop, "stop");
		}

	}

	/**
	 * 공급자가 답하지 못했거나 보내지 않았다 — 토큰이 없다
	 * @param failure 다이제스트 실패 코드
	 * @param message 운영 로그용 문구 — 키 · 프롬프트 · 응답 본문이 없다
	 */
	record Failed(DigestFailure failure, String message) implements GenerationOutcome {

		public Failed {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
		}

	}

}
