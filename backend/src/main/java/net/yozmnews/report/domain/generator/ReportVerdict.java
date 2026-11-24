package net.yozmnews.report.domain.generator;

import java.util.List;
import java.util.Objects;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.output.ReportCitationCheck;
import net.yozmnews.report.domain.output.ReportOutputRead;
import net.yozmnews.report.domain.output.ReportOutputReader;
import net.yozmnews.report.domain.output.ReportOutputSchema;

/**
 * 생성 판정과 다시 묻기 — 정본 · docs/specs/report.md §5 · report-R8
 * <p>
 * 끝까지 쓴 응답만 읽는다. 첫 호출에서 읽지 못하거나, 읽었지만 칸이 빠진 갈래가 있으면 다시 묻는다({@link #judge}) — 한 생성에 다시 묻기는 한
 * 번이다. 읽지 못해 다시 물은 호출에서 읽지 못하면 실패로 끝낸다({@link #settle(ReportOutcome, OutputShape.Obj)}). 빠진 갈래 때문에 다시
 * 물었으면 두 읽기 가운데 빠진 갈래가 적은 쪽(같으면 다시 물은 쪽)을 쓰고, 다시 물은 호출이 읽히지 않거나 끝나지 못했으면 첫 읽기를 쓴다
 * ({@link #settle(ReportOutcome, OutputShape.Obj, ReportAliases, Read)}). 잘린 응답 · 거절은 다시 묻지 않고 그 사유의 코드로 끝낸다.
 * 생성기의 실패는 그 코드 그대로다. 빠진 갈래의 판정은 대조의 정본({@link ReportCitationCheck#missing})이다.
 */
public final class ReportVerdict {

	/** 잘린 응답의 실패 메시지 */
	public static final String TRUNCATED = "출력이 출력 토큰 상한에 닿아 잘렸다";

	/** 거절한 응답의 실패 메시지 */
	public static final String REFUSED = "모델이 출력을 거절했다";

	/** 빠진 갈래 때문에 다시 물을 때의 진단 머리 — 뒤에 빠진 갈래 키를 입력 순서로 {@code ", "} 로 잇는다 */
	public static final String MISSING = "출력에 없는 갈래: ";

	private ReportVerdict() {
	}

	/**
	 * 첫 호출의 판정 — 끝까지 쓴 응답을 읽지 못하거나 칸이 빠진 갈래가 있으면 다시 묻는다
	 * @param root 이번 요청의 출력 서술
	 * @param aliases 이번 요청의 별칭 — 입력 갈래
	 */
	public static Verdict judge(ReportOutcome outcome, OutputShape.Obj root, ReportAliases aliases) {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(root, "root");
		Objects.requireNonNull(aliases, "aliases");
		return switch (outcome) {
			case ReportOutcome.Generated generated when generated.stop() == GenerationStop.COMPLETE ->
				switch (ReportOutputReader.read(generated.text(), root)) {
					case ReportOutputRead.Read read -> {
						Read first = new Read(read);
						List<ReportSectionKind> missing = ReportCitationCheck.missing(read.draft(), aliases);
						yield missing.isEmpty() ? first
								: new RetryMissing(
										MISSING + String.join(", ", missing.stream().map(ReportOutputSchema::key).toList()),
										missing.size(), first);
					}
					case ReportOutputRead.Unreadable unreadable -> new Retry(unreadable.reason());
				};
			case ReportOutcome.Generated generated -> settle(generated, root);
			case ReportOutcome.Failed failed -> settle(failed, root);
		};
	}

	/**
	 * 읽지 못해 다시 물은 호출(또는 다시 묻지 않을 결과)의 판정 — 읽지 못하면 실패다
	 * @param root 이번 요청의 출력 서술
	 */
	public static Settled settle(ReportOutcome outcome, OutputShape.Obj root) {
		Objects.requireNonNull(outcome, "outcome");
		Objects.requireNonNull(root, "root");
		return switch (outcome) {
			case ReportOutcome.Failed failed -> new Fail(failed.failure(), failed.message());
			case ReportOutcome.Generated generated -> switch (generated.stop()) {
				case COMPLETE -> switch (ReportOutputReader.read(generated.text(), root)) {
					case ReportOutputRead.Read read -> new Read(read);
					case ReportOutputRead.Unreadable unreadable -> new Fail(ReportFailure.UNREADABLE_OUTPUT,
							unreadable.reason());
				};
				case TRUNCATED -> new Fail(ReportFailure.OUTPUT_TRUNCATED, TRUNCATED);
				case REFUSED -> new Fail(ReportFailure.OUTPUT_REFUSED, REFUSED);
			};
		};
	}

	/**
	 * 빠진 갈래 때문에 다시 물은 호출의 판정 — 읽었으면 두 읽기 가운데 빠진 갈래가 적은 쪽(같으면 다시 물은 쪽), 읽지 못했거나 끝나지 못했으면 첫 읽기다
	 * @param first 첫 호출의 읽기({@link RetryMissing#first})
	 */
	public static Read settle(ReportOutcome second, OutputShape.Obj root, ReportAliases aliases, Read first) {
		Objects.requireNonNull(second, "second");
		Objects.requireNonNull(root, "root");
		Objects.requireNonNull(aliases, "aliases");
		Objects.requireNonNull(first, "first");
		return switch (second) {
			case ReportOutcome.Failed failed -> first;
			case ReportOutcome.Generated generated -> switch (generated.stop()) {
				case COMPLETE -> switch (ReportOutputReader.read(generated.text(), root)) {
					case ReportOutputRead.Read read -> {
						Read again = new Read(read);
						yield (missing(again, aliases) <= missing(first, aliases)) ? again : first;
					}
					case ReportOutputRead.Unreadable unreadable -> first;
				};
				case TRUNCATED, REFUSED -> first;
			};
		};
	}

	private static int missing(Read read, ReportAliases aliases) {
		return ReportCitationCheck.missing(read.output().draft(), aliases).size();
	}

	/** 첫 호출의 판정 — 다시 묻거나 끝난 판정 */
	public sealed interface Verdict permits Retry, RetryMissing, Settled {
	}

	/** 끝난 판정 — 읽었거나 실패다 */
	public sealed interface Settled extends Verdict permits Read, Fail {
	}

	/** 읽었다 — 대조해 저장한다 */
	public record Read(ReportOutputRead.Read output) implements Settled {

		public Read {
			Objects.requireNonNull(output, "output");
		}

	}

	/** 실패 코드로 끝낸다 */
	public record Fail(ReportFailure failure, String message) implements Settled {

		public Fail {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
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
	 * 읽었지만 칸이 빠진 갈래가 있다 — 빠진 갈래 키를 진단에 실어 한 번 다시 묻는다
	 * @param diagnosis 빠진 갈래의 진단({@link #MISSING} + 키들)
	 * @param missing 빠진 갈래 수 — 1 이상
	 * @param first 첫 호출의 읽기 — 다시 물은 호출이 낫지 않으면 이것을 쓴다
	 */
	public record RetryMissing(String diagnosis, int missing, Read first) implements Verdict {

		/**
		 * @throws IllegalArgumentException 빠진 갈래 수가 1 보다 작을 때
		 */
		public RetryMissing {
			Objects.requireNonNull(diagnosis, "diagnosis");
			Objects.requireNonNull(first, "first");
			if (missing < 1) {
				throw new IllegalArgumentException("빠진 갈래가 없다: " + missing);
			}
		}

	}

}
