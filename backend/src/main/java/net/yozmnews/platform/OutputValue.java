package net.yozmnews.platform;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * LLM 출력을 서술대로 읽은 값 — 객체 · 배열 · 문자열 · docs/specs/platform.md §3 · platform-R31
 * <p>
 * 읽기({@link StructuredOutputReader})가 서술의 필수 · 타입 · 처분을 확인한 뒤의 값이다. 객체에는 없는 선택 속성 · {@code null} · 없음으로 받은 빈
 * 문자열 · 지운 값의 키가 없다.
 */
public sealed interface OutputValue {

	/**
	 * 객체 — 서술 순서로 남은 속성 · 키만 담는다. 키 꼴({@link OutputShape.Keyed})의 값도 이 꼴이다.
	 */
	record Obj(Map<String, OutputValue> values) implements OutputValue {

		public Obj {
			values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
		}

		/**
		 * @return 그 이름의 문자열
		 * @throws IllegalStateException 그 이름의 값이 없거나 문자열이 아닐 때 — 서술대로 읽은 값에서는 생기지 않는다
		 */
		public String string(String name) {
			return switch (require(name)) {
				case Str str -> str.value();
				case Obj obj -> throw wrongShape(name, "문자열이 아니다");
				case Arr arr -> throw wrongShape(name, "문자열이 아니다");
			};
		}

		/**
		 * @return 그 이름의 문자열 — 없으면 빈 값
		 * @throws IllegalStateException 값이 있는데 문자열이 아닐 때
		 */
		public Optional<String> optionalString(String name) {
			return this.values.containsKey(name) ? Optional.of(string(name)) : Optional.empty();
		}

		/**
		 * @return 그 이름의 객체
		 * @throws IllegalStateException 그 이름의 값이 없거나 객체가 아닐 때
		 */
		public Obj obj(String name) {
			return switch (require(name)) {
				case Obj obj -> obj;
				case Arr arr -> throw wrongShape(name, "객체가 아니다");
				case Str str -> throw wrongShape(name, "객체가 아니다");
			};
		}

		/**
		 * @return 그 이름의 배열
		 * @throws IllegalStateException 그 이름의 값이 없거나 배열이 아닐 때
		 */
		public Arr arr(String name) {
			return switch (require(name)) {
				case Arr arr -> arr;
				case Obj obj -> throw wrongShape(name, "배열이 아니다");
				case Str str -> throw wrongShape(name, "배열이 아니다");
			};
		}

		private OutputValue require(String name) {
			Objects.requireNonNull(name, "name");
			OutputValue value = this.values.get(name);
			if (value == null) {
				throw new IllegalStateException("값이 없다: " + name);
			}
			return value;
		}

		private static IllegalStateException wrongShape(String name, String reason) {
			return new IllegalStateException(reason + ": " + name);
		}

	}

	/**
	 * 배열 — 버린 원소는 빠져 있다.
	 */
	record Arr(List<OutputValue> items) implements OutputValue {

		public Arr {
			items = List.copyOf(items);
		}

	}

	/**
	 * 문자열 — 앞뒤 공백을 지운 값이다.
	 */
	record Str(String value) implements OutputValue {

		public Str {
			Objects.requireNonNull(value, "value");
		}

	}

}
