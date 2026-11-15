package net.yozmnews.credential.domain.crypto;

import java.util.List;
import java.util.Objects;

/**
 * KEK 파일을 읽은 결과.
 */
public sealed interface KekFileRead {

	/** 형식에 맞다 */
	record Parsed(KeyEncryptionKeys keys) implements KekFileRead {

		public Parsed {
			Objects.requireNonNull(keys, "keys");
		}

	}

	/**
	 * 형식이 틀렸다.
	 *
	 * @param problems 문제마다 한 줄 — 줄 번호와 까닭이고 값은 없다
	 */
	record Invalid(List<String> problems) implements KekFileRead {

		public Invalid {
			problems = List.copyOf(problems);
		}

	}

}
