package net.yozmnews.source.domain.profile;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

/**
 * 파싱 전에 피드 원문에 적용하는 보정. 닫힌 목록에서만 고른다 — 프로필에는 코드를 넣을 수 없다.
 */
public sealed interface PreCorrection {

	Rule rule();

	/** 정의되지 않은 엔티티를 치환한다 */
	record ReplaceUndefinedEntities() implements PreCorrection {

		@Override
		public Rule rule() {
			return Rule.REPLACE_UNDEFINED_ENTITIES;
		}

	}

	/** XML 에 쓸 수 없는 제어 문자를 지운다 */
	record StripInvalidControlChars() implements PreCorrection {

		@Override
		public Rule rule() {
			return Rule.STRIP_INVALID_CONTROL_CHARS;
		}

	}

	/** 선언과 관계없이 이 문자 인코딩으로 읽는다 */
	record ForceEncoding(Charset charset) implements PreCorrection {

		public ForceEncoding {
			Objects.requireNonNull(charset, "charset");
		}

		@Override
		public Rule rule() {
			return Rule.FORCE_ENCODING;
		}

	}

	/** 보정 규칙 이름 — 프로필 텍스트의 {@code rule} 값 */
	enum Rule {

		REPLACE_UNDEFINED_ENTITIES("replace-undefined-entities"),
		STRIP_INVALID_CONTROL_CHARS("strip-invalid-control-chars"),
		FORCE_ENCODING("force-encoding");

		private final String key;

		Rule(String key) {
			this.key = key;
		}

		public String key() {
			return this.key;
		}

		public static Optional<Rule> ofKey(String key) {
			return Arrays.stream(values()).filter(rule -> rule.key.equals(key)).findFirst();
		}

	}

}
