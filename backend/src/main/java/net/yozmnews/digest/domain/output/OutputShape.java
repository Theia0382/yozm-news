package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;

/**
 * 출력 구조의 서술 마디 — 읽기와 JSON Schema 문서가 함께 따르는 꼴 · docs/specs/digest.md §5 · digest-R4
 */
public sealed interface OutputShape {

	/**
	 * 객체 — 서술한 속성만 읽고 모르는 속성은 무시한다.
	 */
	record Obj(List<Property> properties) implements OutputShape {

		public Obj {
			properties = List.copyOf(properties);
		}

	}

	/**
	 * 배열 — 원소가 {@code maxItems} 를 넘으면 앞에서부터 그만큼만 읽는다.
	 */
	record Arr(OutputShape item, int maxItems) implements OutputShape {

		public Arr {
			Objects.requireNonNull(item, "item");
			if (maxItems < 1) {
				throw new IllegalArgumentException("배열 상한이 1 보다 작다: " + maxItems);
			}
		}

	}

	/**
	 * 문자열 — 앞뒤 공백을 지운 값의 코드 포인트로 상한을 잰다.
	 */
	record Str(int maxCodePoints, OnBlank onBlank, OverLimit onOverLimit) implements OutputShape {

		public Str {
			Objects.requireNonNull(onBlank, "onBlank");
			Objects.requireNonNull(onOverLimit, "onOverLimit");
			if (maxCodePoints < 1) {
				throw new IllegalArgumentException("문자열 상한이 1 보다 작다: " + maxCodePoints);
			}
		}

	}

	/**
	 * 객체의 속성 하나.
	 * @param required 참이면 빠졌을 때 읽지 못한 것이다
	 * @param nullable 참이면 {@code null} 을 없음으로 받는다
	 */
	record Property(String name, OutputShape shape, boolean required, boolean nullable) {

		public Property {
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(shape, "shape");
		}

	}

}
