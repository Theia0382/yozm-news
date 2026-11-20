package net.yozmnews.digest.domain.generator;

import java.util.Objects;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.output.DigestOutputReader;
import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.digest.domain.output.OutputRead;
import net.yozmnews.llm.GenerationStop;

/**
 * 생성 한 번의 판정 — 읽을지 · 다시 물을지 · 실패로 끝낼지. 정본 — docs/specs/digest.md §5 · digest-R27
 * <p>
 * 끝까지 쓴 응답만 읽는다. 첫 호출에서 읽지 못하면 다시 묻고({@link #judge}), 다시 물은 호출에서 읽지 못하면 실패로 끝낸다({@link #settle}) —
 * 한 생성에 다시 묻기는 한 번이다. 잘린 응답 · 거절은 같은 요청을 다시 보내도 같을 가능성이 커서 다시 묻지 않고 그 사유의 코드로 끝낸다.
 * 생성기의 실패는 그 코드 그대로다.
 */
public final class GenerationVerdict {

	/** 잘린 응답의 실패 메시지 */
	public static final String TRUNCATED = "출력이 출력 토큰 상한에 닿아 잘렸다";

	/** 거절한 응답의 실패 메시지 */
	public static final String REFUSED = "모델이 출력을 거절했다";

	private GenerationVerdict() {
	}

	/**
	 * 첫 호출의 판정 — 끝까지 쓴 응답을 읽지 못하면 다시 묻는다
	 * @param set 이번 생성의 항목 — 출력 서술({@link DigestOutputSchema#root})을 정한다
	 */
	public static Verdict judge(GenerationOutcome outcome, CandidateSet set) {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(set, "set");
		return switch (outcome) {
			case GenerationOutcome.Generated generated when generated.stop() == GenerationStop.COMPLETE ->
				switch (DigestOutputReader.read(generated.text(), DigestOutputSchema.root(set))) {
					case OutputRead.Read read -> new Read(read);
					case OutputRead.Unreadable unreadable -> new Retry(unreadable.reason());
				};
			case GenerationOutcome.Generated generated -> settle(generated, set);
			case GenerationOutcome.Failed failed -> settle(failed, set);
		};
	}

	/**
	 * 다시 물은 호출(또는 다시 묻지 않을 결과)의 판정 — 읽지 못하면 실패다
	 * @param set 이번 생성의 항목 — 출력 서술({@link DigestOutputSchema#root})을 정한다
	 */
	public static Settled settle(GenerationOutcome outcome, CandidateSet set) {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(set, "set");
		return switch (outcome) {
			case GenerationOutcome.Failed failed -> new Fail(failed.failure(), failed.message());
			case GenerationOutcome.Generated generated -> switch (generated.stop()) {
				case COMPLETE -> switch (DigestOutputReader.read(generated.text(), DigestOutputSchema.root(set))) {
					case OutputRead.Read read -> new Read(read);
					case OutputRead.Unreadable unreadable -> new Fail(DigestFailure.UNREADABLE_OUTPUT, unreadable.reason());
				};
				case TRUNCATED -> new Fail(DigestFailure.OUTPUT_TRUNCATED, TRUNCATED);
				case REFUSED -> new Fail(DigestFailure.OUTPUT_REFUSED, REFUSED);
			};
		};
	}

	/** 첫 호출의 판정 — 다시 묻거나 끝난 판정 */
	public sealed interface Verdict permits Retry, Settled {
	}

	/** 끝난 판정 — 읽었거나 실패다 */
	public sealed interface Settled extends Verdict permits Read, Fail {
	}

	/** 읽었다 — 인용을 검증해 저장한다 */
	public record Read(OutputRead.Read output) implements Settled {

		public Read {
			Objects.requireNonNull(output, "output");
		}

	}

	/**
	 * 끝까지 쓴 응답을 읽지 못했다 — 진단을 실어 한 번 다시 묻는다
	 * @param diagnosis 읽기의 진단 — 출력 원문이 없다
	 */
	public record Retry(String diagnosis) implements Verdict {

		public Retry {
			Objects.requireNonNull(diagnosis, "diagnosis");
		}

	}

	/** 그 코드와 메시지로 실패로 끝낸다 */
	public record Fail(DigestFailure failure, String message) implements Settled {

		public Fail {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
		}

	}

}
