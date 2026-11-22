package net.yozmnews.report.domain.generator;

import java.util.Objects;

import net.yozmnews.platform.OutputShape;
import net.yozmnews.report.ReportFailure;
import net.yozmnews.report.domain.output.ReportOutputRead;
import net.yozmnews.report.domain.output.ReportOutputReader;

/**
 * 생성 한 번의 판정 — 정본 · docs/specs/report.md §5 · report-R8
 * <p>
 * 끝까지 쓴 응답만 읽는다. 잘림 · 거절은 본문이 있어도 읽지 않는다. 다시 묻지 않는다.
 */
public sealed interface ReportVerdict {

	/** 잘린 응답의 실패 메시지 */
	String TRUNCATED = "출력이 출력 토큰 상한에 닿아 잘렸다";

	/** 거절한 응답의 실패 메시지 */
	String REFUSED = "모델이 출력을 거절했다";

	/** 읽었다 */
	record Read(ReportOutputRead.Read output) implements ReportVerdict {

		public Read {
			Objects.requireNonNull(output, "output");
		}

	}

	/** 실패 코드로 끝낸다 */
	record Fail(ReportFailure failure, String message) implements ReportVerdict {

		public Fail {
			Objects.requireNonNull(failure, "failure");
			Objects.requireNonNull(message, "message");
		}

	}

	/**
	 * 생성 결과를 판정한다
	 * @param root 이번 요청의 출력 서술
	 */
	static ReportVerdict judge(ReportOutcome outcome, OutputShape.Obj root) {
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

}
