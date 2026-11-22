package net.yozmnews.platform;

import java.util.Objects;

/**
 * LLM 출력 읽기의 결과 · docs/specs/platform.md §3 · platform-R31
 */
public sealed interface StructuredRead {

	/**
	 * 출력을 읽었다.
	 * @param overLimit 상한을 넘어 버린 칸(키 꼴의 값 · 배열 원소)과 지운 값의 수
	 */
	record Read(OutputValue.Obj root, int overLimit) implements StructuredRead {

		public Read {
			Objects.requireNonNull(root, "root");
			if (overLimit < 0) {
				throw new IllegalArgumentException("overLimit 이 음수다: " + overLimit);
			}
		}

	}

	/**
	 * 출력을 읽지 못했다.
	 * @param reason 짧은 진단 — 어느 속성이 왜. 출력 원문을 싣지 않는다
	 */
	record Unreadable(String reason) implements StructuredRead {

		public Unreadable {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
