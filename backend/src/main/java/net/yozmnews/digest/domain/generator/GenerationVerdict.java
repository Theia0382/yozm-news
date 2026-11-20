package net.yozmnews.digest.domain.generator;

import java.util.List;
import java.util.Objects;

import net.yozmnews.digest.DigestFailure;
import net.yozmnews.digest.domain.candidate.AliasedItem;
import net.yozmnews.digest.domain.candidate.CandidateSet;
import net.yozmnews.digest.domain.output.DigestOutputReader;
import net.yozmnews.digest.domain.output.DigestOutputSchema;
import net.yozmnews.digest.domain.output.OutputRead;
import net.yozmnews.llm.GenerationStop;

/**
 * 생성 한 번의 판정 — 읽을지 · 다시 물을지 · 실패로 끝낼지. 정본 — docs/specs/digest.md §5 · digest-R27
 * <p>
 * 끝까지 쓴 응답만 읽는다. 첫 호출에서 읽지 못하거나, 읽었지만 칸이 빠진 항목이 있으면 다시 묻는다({@link #judge}) — 한 생성에 다시 묻기는
 * 한 번이다. 다시 물은 호출에서 읽지 못하면 실패로 끝낸다({@link #settle(GenerationOutcome, CandidateSet)}). 빠진 항목 때문에 다시 물었으면
 * 두 읽기 가운데 빠진 항목이 적은 쪽을 쓰고, 다시 물은 호출이 끝나지 못했으면 첫 읽기를 쓴다({@link #settle(GenerationOutcome, CandidateSet,
 * Read)}). 잘린 응답 · 거절은 같은 요청을 다시 보내도 같을 가능성이 커서 다시 묻지 않고 그 사유의 코드로 끝낸다. 생성기의 실패는 그 코드
 * 그대로다.
 */
public final class GenerationVerdict {

	/** 잘린 응답의 실패 메시지 */
	public static final String TRUNCATED = "출력이 출력 토큰 상한에 닿아 잘렸다";

	/** 거절한 응답의 실패 메시지 */
	public static final String REFUSED = "모델이 출력을 거절했다";

	/** 빠진 항목 때문에 다시 물을 때의 진단 머리 — 뒤에 빠진 항목 별칭을 항목 순서로 잇는다 */
	public static final String MISSING = "출력에 없는 항목: ";

	private GenerationVerdict() {
	}

	/**
	 * 첫 호출의 판정 — 끝까지 쓴 응답을 읽지 못하거나 칸이 빠진 항목이 있으면 다시 묻는다
	 * @param set 이번 생성의 항목 — 출력 서술({@link DigestOutputSchema#root})을 정한다
	 */
	public static Verdict judge(GenerationOutcome outcome, CandidateSet set) {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(set, "set");
		return switch (outcome) {
			case GenerationOutcome.Generated generated when generated.stop() == GenerationStop.COMPLETE ->
				switch (DigestOutputReader.read(generated.text(), DigestOutputSchema.root(set))) {
					case OutputRead.Read read -> {
						List<String> missing = missing(read, set);
						Read first = new Read(read, missing.size());
						yield missing.isEmpty() ? first : new RetryMissing(MISSING + String.join(", ", missing), first);
					}
					case OutputRead.Unreadable unreadable -> new Retry(unreadable.reason());
				};
			case GenerationOutcome.Generated generated -> settle(generated, set);
			case GenerationOutcome.Failed failed -> settle(failed, set);
		};
	}

	/**
	 * 읽지 못해 다시 물은 호출(또는 다시 묻지 않을 결과)의 판정 — 읽지 못하면 실패다. 읽었으면 그 출력의 빠진 항목 수가 처음 읽은 출력의 수다
	 * @param set 이번 생성의 항목 — 출력 서술({@link DigestOutputSchema#root})을 정한다
	 */
	public static Settled settle(GenerationOutcome outcome, CandidateSet set) {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(set, "set");
		return switch (outcome) {
			case GenerationOutcome.Failed failed -> new Fail(failed.failure(), failed.message());
			case GenerationOutcome.Generated generated -> switch (generated.stop()) {
				case COMPLETE -> switch (DigestOutputReader.read(generated.text(), DigestOutputSchema.root(set))) {
					case OutputRead.Read read -> new Read(read, missing(read, set).size());
					case OutputRead.Unreadable unreadable -> new Fail(DigestFailure.UNREADABLE_OUTPUT, unreadable.reason());
				};
				case TRUNCATED -> new Fail(DigestFailure.OUTPUT_TRUNCATED, TRUNCATED);
				case REFUSED -> new Fail(DigestFailure.OUTPUT_REFUSED, REFUSED);
			};
		};
	}

	/**
	 * 빠진 항목 때문에 다시 물은 호출의 판정 — 읽었으면 두 읽기 가운데 빠진 항목이 적은 쪽(같으면 다시 물은 쪽), 읽지 못했거나 끝나지 못했으면 첫
	 * 읽기다. 처음 읽은 출력에 없던 항목 수는 언제나 첫 읽기의 수다
	 * @param first 첫 호출의 읽기({@link RetryMissing#first})
	 */
	public static Read settle(GenerationOutcome second, CandidateSet set, Read first) {
		Objects.requireNonNull(second, "second");
		Objects.requireNonNull(set, "set");
		Objects.requireNonNull(first, "first");
		return switch (second) {
			case GenerationOutcome.Failed failed -> first;
			case GenerationOutcome.Generated generated -> switch (generated.stop()) {
				case COMPLETE -> switch (DigestOutputReader.read(generated.text(), DigestOutputSchema.root(set))) {
					case OutputRead.Read read -> (missing(read, set).size() <= first.firstMissing())
							? new Read(read, first.firstMissing()) : first;
					case OutputRead.Unreadable unreadable -> first;
				};
				case TRUNCATED, REFUSED -> first;
			};
		};
	}

	/** 칸이 빠진 항목의 별칭 — 항목 순서 */
	private static List<String> missing(OutputRead.Read read, CandidateSet set) {
		return set.items().stream().filter(item -> !read.draft().covers(item)).map(AliasedItem::alias).toList();
	}

	/** 첫 호출의 판정 — 다시 묻거나 끝난 판정 */
	public sealed interface Verdict permits Retry, RetryMissing, Settled {
	}

	/** 끝난 판정 — 읽었거나 실패다 */
	public sealed interface Settled extends Verdict permits Read, Fail {
	}

	/**
	 * 읽었다 — 항목과 대조해 저장한다
	 * @param firstMissing 처음 읽은 출력에 없던 항목 수
	 */
	public record Read(OutputRead.Read output, int firstMissing) implements Settled {

		public Read {
			Objects.requireNonNull(output, "output");
			if (firstMissing < 0) {
				throw new IllegalArgumentException("빠진 항목 수가 음수다: " + firstMissing);
			}
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

	/**
	 * 읽었지만 칸이 빠진 항목이 있다 — 빠진 항목 별칭을 진단에 실어 한 번 다시 묻는다
	 * @param diagnosis 빠진 항목 별칭의 진단
	 * @param first 첫 호출의 읽기 — 다시 물은 호출이 낫지 않으면 이것을 쓴다
	 */
	public record RetryMissing(String diagnosis, Read first) implements Verdict {

		public RetryMissing {
			Objects.requireNonNull(diagnosis, "diagnosis");
			Objects.requireNonNull(first, "first");
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
