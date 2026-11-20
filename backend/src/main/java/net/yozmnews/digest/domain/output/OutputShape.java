package net.yozmnews.digest.domain.output;

import java.util.List;
import java.util.Objects;
import java.util.Set;

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
	 * 키마다 같은 꼴의 값을 갖는 객체 — 키는 이번 요청의 항목 별칭이다. JSON Schema 는 모든 키를 필수로 그리고, 읽기는 없는 키 · {@code null}
	 * 인 키를 빼고 읽는다(출력 전체를 읽지 못한 것이 아니다). 값을 버리면 그 키를 빼고, 서술하지 않은 키는 무시한다.
	 * @param keys 키 — 순서대로, 비지 않고 겹치지 않는다
	 */
	record Keyed(List<String> keys, OutputShape value) implements OutputShape {

		public Keyed {
			keys = List.copyOf(keys);
			Objects.requireNonNull(value, "value");
			if (keys.isEmpty()) {
				throw new IllegalArgumentException("키가 없다");
			}
			if (Set.copyOf(keys).size() != keys.size()) {
				throw new IllegalArgumentException("겹친 키가 있다: " + keys);
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
