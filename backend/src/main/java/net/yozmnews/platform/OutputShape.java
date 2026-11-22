package net.yozmnews.platform;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * LLM 출력 구조의 서술 마디 — 읽기와 JSON Schema 문서가 함께 따르는 꼴 · docs/specs/platform.md §3 · platform-R31 · platform-R32
 * <p>
 * 서술은 출력을 쓰는 과제 모듈이 요청마다 만든다 — 칸 구성 · 속성 이름 · 상한 값은 그 모듈의 정본이다.
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
	 * 배열 — 원소마다 같은 꼴이다. 개수 상한은 두지 않는다(원문 크기는 생성 관문의 본문 상한이 묶는다).
	 */
	record Arr(OutputShape item) implements OutputShape {

		public Arr {
			Objects.requireNonNull(item, "item");
		}

	}

	/**
	 * 키마다 같은 꼴의 값을 갖는 객체 — 키는 요청마다 정한다(예: 항목 별칭 · 갈래). JSON Schema 는 모든 키를 필수로 그리고, 읽기는 없는 키 ·
	 * {@code null} 인 키를 빼고 읽는다(출력 전체를 읽지 못한 것이 아니다). 값을 버리면 그 키를 빼고, 서술하지 않은 키는 무시한다.
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

	/**
	 * 문자열이 비었을 때(앞뒤 공백을 지운 값이 빈 문자열)의 처분.
	 */
	enum OnBlank {

		/** 출력 전체를 읽지 못한 것으로 한다 */
		UNREADABLE,

		/** 없음으로 받는다 — 선택 속성에만 쓴다 */
		ABSENT,

		/** 빈 문자열 그대로 받는다 — 뒤의 대조가 처분한다 */
		KEEP

	}

	/**
	 * 문자열이 상한을 넘을 때의 처분.
	 */
	enum OverLimit {

		/** 출력 전체를 읽지 못한 것으로 한다 */
		UNREADABLE,

		/**
		 * 그 문자열을 가진 가장 가까운 버릴 수 있는 칸 — 키 꼴의 값 또는 배열의 원소 — 을 버린다. 그 칸은 출력에 없는 것이 된다
		 */
		DROP_ITEM,

		/** 그 값만 지운다(없음으로) — 선택 속성에만 쓴다 */
		DROP_VALUE,

		/** 상한까지의 앞부분만 남긴다 — 코드 포인트로 자른다 */
		TRUNCATE

	}

}
