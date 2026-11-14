package net.yozmnews.llm;

import java.util.Objects;

import net.yozmnews.platform.SecretValue;
import org.springframework.modulith.NamedInterface;

/**
 * 공급 인터페이스가 건네는 저장한 키 — docs/specs/llm.md §3 · llm-R17
 */
@NamedInterface("stored-key")
public record StoredKey(long id, SecretValue value) {

	public StoredKey {
		Objects.requireNonNull(value, "value");
	}

}
