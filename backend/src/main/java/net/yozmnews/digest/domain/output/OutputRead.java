package net.yozmnews.digest.domain.output;

import java.util.Objects;

/**
 * 출력 읽기의 결과 · docs/specs/digest.md §5 · digest-R4 · digest-R5
 */
public sealed interface OutputRead {

	/**
	 * 출력을 읽었다.
	 * @param overLimit 상한을 넘어 버린 원소 · 읽지 않은 배열 꼬리 원소 · 지운 값의 수
	 */
	record Read(DigestDraft draft, int overLimit) implements OutputRead {

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
	record Unreadable(String reason) implements OutputRead {

		public Unreadable {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
