package net.yozmnews.report.domain.output;

import java.util.Objects;

/**
 * 리포트 출력 읽기의 결과 · docs/specs/report.md §5 · report-R5
 */
public sealed interface ReportOutputRead {

	/**
	 * 출력을 읽었다.
	 * @param overLimit 상한을 넘어 버린 갈래 칸 · 별칭 원소의 수
	 */
	record Read(ReportDraft draft, int overLimit) implements ReportOutputRead {

		public Read {
			Objects.requireNonNull(draft, "draft");
			if (overLimit < 0) {
				throw new IllegalArgumentException("overLimit 이 음수다: " + overLimit);
			}
		}

	}

	/**
	 * 출력을 읽지 못했다.
	 * @param reason 짧은 진단 — 어느 속성이 왜. 출력 원문을 싣지 않는다
	 */
	record Unreadable(String reason) implements ReportOutputRead {

		public Unreadable {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
