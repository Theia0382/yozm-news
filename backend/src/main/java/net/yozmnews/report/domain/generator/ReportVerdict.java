package net.yozmnews.report.domain.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.llm.GenerationStop;
import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.ReportSectionKind;
import net.yozmnews.report.domain.alias.ReportAliases;
import net.yozmnews.report.domain.output.CitedReport;
import net.yozmnews.report.domain.output.ReportCitationCheck;
import net.yozmnews.report.domain.output.ReportOutputRead;
import net.yozmnews.report.domain.output.ReportOutputReader;
import net.yozmnews.report.domain.output.ReportOutputSchema;

/**
 * 생성 판정과 다시 묻기 — 정본 · docs/specs/report.md §5 · report-R8
 * <p>
 * 끝까지 쓴 응답만 읽는다. 첫 호출에서 읽지 못하거나, 읽었지만 칸이 빠진 갈래나 글에 남은 별칭이 있으면 다시 묻는다({@link #judge}) — 한
 * 생성에 다시 묻기는 한 번이다. 읽지 못해 다시 물은 호출에서 읽지 못하면 실패로 끝낸다({@link #settle(ReportOutcome, OutputShape.Obj)}).
 * 고쳐 쓰라고 다시 물었으면 두 읽기 가운데 모자람({@link Shortfall} — 빠진 갈래 수, 남은 별칭 수의 사전식)이 적은 쪽(같으면 다시 물은 쪽)을
 * 쓰고, 다시 물은 호출이 읽히지 않거나 끝나지 못했으면 첫 읽기를 쓴다({@link #settle(ReportOutcome, OutputShape.Obj, ReportAliases, Read)})
 * — 다시 물어도 남은 별칭은 실패로 치지 않는다. 고른 읽기를 대조한 뒤 남은 갈래 섹션이 하나도 없으면 읽지 못한 출력으로 끝낸다
 * ({@link #withoutSections}). 잘린 응답 · 거절은 다시 묻지 않고 그 사유의 코드로 끝낸다. 생성기의 실패는 그 코드 그대로다.
 * 빠진 갈래 · 남은 별칭의 판정은 대조의 정본({@link ReportCitationCheck#missing} · {@link ReportCitationCheck#leftAliases})이다.
 */
public final class ReportVerdict {

	/** 잘린 응답의 실패 메시지 */
	public static final String TRUNCATED = "출력이 출력 토큰 상한에 닿아 잘렸다";

	/** 거절한 응답의 실패 메시지 */
	public static final String REFUSED = "모델이 출력을 거절했다";

	/** 빠진 갈래 때문에 다시 물을 때의 진단 머리 — 뒤에 빠진 갈래 키를 입력 순서로 {@code ", "} 로 잇는다 */
	public static final String MISSING = "출력에 없는 갈래: ";

	/** 글에 별칭이 남아 다시 물을 때의 진단 머리 — 뒤에 남은 별칭을 {@code ", "} 로 잇는다 */
	public static final String LEFT_ALIASES = "제목 · 본문에 남은 별칭(이름으로 바꿔 쓸 것): ";

	/** 대조 뒤 남은 갈래 섹션이 없는 출력의 실패 메시지 */
	public static final String NO_SECTION = "출력에 대조를 지난 갈래 섹션이 없다";

	/** 진단 둘을 잇는 구분 */
	private static final String DIAGNOSIS_SEPARATOR = " · ";

	private ReportVerdict() {
	}

	/**
	 * 고른 읽기를 대조한 뒤의 판정 — 남은 갈래 섹션이 하나도 없으면 읽지 못한 출력({@code UNREADABLE_OUTPUT})으로 끝낸다. 리포트의 글은 갈래
	 * 섹션뿐이라 섹션이 없으면 보일 글이 없다(report-R8)
	 * @return 섹션이 하나라도 있으면 빈 값
	 */
	public static Optional<Fail> withoutSections(CitedReport cited) {
		Objects.requireNonNull(cited, "cited");
		return cited.sections().isEmpty() ? Optional.of(new Fail(ReportFailure.UNREADABLE_OUTPUT, NO_SECTION))
				: Optional.empty();
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
						List<String> left = ReportCitationCheck.leftAliases(read.draft(), aliases);
						yield (missing.isEmpty() && left.isEmpty()) ? first
								: new RetryRewrite(diagnosis(missing, left), new Shortfall(missing.size(), left.size()), first);
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
	 * 고쳐 쓰라고 다시 물은 호출의 판정 — 읽었으면 두 읽기 가운데 모자람이 적은 쪽(같으면 다시 물은 쪽), 읽지 못했거나 끝나지 못했으면 첫 읽기다
	 * @param first 첫 호출의 읽기({@link RetryRewrite#first})
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
						yield (shortfall(again, aliases).compareTo(shortfall(first, aliases)) <= 0) ? again : first;
					}
					case ReportOutputRead.Unreadable unreadable -> first;
				};
				case TRUNCATED, REFUSED -> first;
			};
		};
	}

	private static Shortfall shortfall(Read read, ReportAliases aliases) {
		return new Shortfall(ReportCitationCheck.missing(read.output().draft(), aliases).size(),
				ReportCitationCheck.leftAliases(read.output().draft(), aliases).size());
	}

	/** 다시 묻는 진단 — 빠진 갈래 키(입력 순서)와 남은 별칭을 잇는다. 없는 쪽은 싣지 않는다 */
	private static String diagnosis(List<ReportSectionKind> missing, List<String> left) {
		List<String> parts = new ArrayList<>();
		if (!missing.isEmpty()) {
			parts.add(MISSING + String.join(", ", missing.stream().map(ReportOutputSchema::key).toList()));
		}
		if (!left.isEmpty()) {
			parts.add(LEFT_ALIASES + String.join(", ", left));
		}
		return String.join(DIAGNOSIS_SEPARATOR, parts);
	}

	/** 첫 호출의 판정 — 다시 묻거나 끝난 판정 */
	public sealed interface Verdict permits Retry, RetryRewrite, Settled {
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
	 * 읽었지만 칸이 빠진 갈래나 글에 남은 별칭이 있다 — 진단을 실어 한 번 고쳐 쓰라고 다시 묻는다
	 * @param diagnosis 진단({@link #MISSING} · {@link #LEFT_ALIASES} 로 시작하는 부분을 잇는다)
	 * @param shortfall 첫 읽기의 모자람 — 비어 있지 않다
	 * @param first 첫 호출의 읽기 — 다시 물은 호출이 낫지 않으면 이것을 쓴다
	 */
	public record RetryRewrite(String diagnosis, Shortfall shortfall, Read first) implements Verdict {

		/**
		 * @throws IllegalArgumentException 모자람이 없을 때
		 */
		public RetryRewrite {
			Objects.requireNonNull(diagnosis, "diagnosis");
			Objects.requireNonNull(shortfall, "shortfall");
			Objects.requireNonNull(first, "first");
			if (shortfall.isNone()) {
				throw new IllegalArgumentException("고쳐 쓸 것이 없다");
			}
		}

	}

	/**
	 * 읽기의 모자람 — 빠진 갈래 수를 먼저, 남은 별칭 수를 다음으로 견준다(사전식). 빠진 갈래는 섹션이 없어지는 것이라 글에 남은 별칭보다 무겁다
	 * @param missing 칸이 빠진 갈래 수
	 * @param leftAliases 글에 남은 별칭 수
	 */
	public record Shortfall(int missing, int leftAliases) implements Comparable<Shortfall> {

		/**
		 * @throws IllegalArgumentException 수가 음수일 때
		 */
		public Shortfall {
			if (missing < 0 || leftAliases < 0) {
				throw new IllegalArgumentException("모자람이 음수다");
			}
		}

		/** 모자람이 없는가 */
		public boolean isNone() {
			return this.missing == 0 && this.leftAliases == 0;
		}

		@Override
		public int compareTo(Shortfall other) {
			int byMissing = Integer.compare(this.missing, other.missing);
			return (byMissing != 0) ? byMissing : Integer.compare(this.leftAliases, other.leftAliases);
		}

	}

}
