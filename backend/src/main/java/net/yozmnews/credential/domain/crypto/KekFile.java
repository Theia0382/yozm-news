package net.yozmnews.credential.domain.crypto;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * KEK 파일 원문의 형식 판정 — 정본 · docs/specs/credential.md §4 · credential-R5
 * <p>
 * 문제 문구는 줄 번호와 까닭뿐이다 — 줄의 내용(값)을 담지 않는다.
 */
public final class KekFile {

	/** 원문의 최대 길이(문자) */
	public static final int MAX_CHARS = 65_536;

	private KekFile() {
	}

	/** 원문을 읽어 형식을 판정한다 — 문제는 모두 모아 낸다 */
	public static KekFileRead parse(String text) {
		Objects.requireNonNull(text, "text");
		if (text.length() > MAX_CHARS) {
			return new KekFileRead.Invalid(List.of("파일이 " + MAX_CHARS + "자를 넘는다"));
		}
		Map<Integer, byte[]> keys = new LinkedHashMap<>();
		List<String> problems = new ArrayList<>();
		String[] lines = text.split("\n", -1);
		for (int index = 0; index < lines.length; index++) {
			String line = lines[index].strip();
			if (line.isEmpty() || line.startsWith("#")) {
				continue;
			}
			String where = (index + 1) + "번째 줄: ";
			int colon = line.indexOf(':');
			if (colon < 0) {
				problems.add(where + "버전:값 꼴이 아니다");
				continue;
			}
			@Nullable Integer version = version(line.substring(0, colon).strip());
			if (version == null) {
				problems.add(where + "버전은 1 이상의 정수여야 한다");
				continue;
			}
			if (keys.containsKey(version)) {
				problems.add(where + "버전 " + version + " 이 겹친다");
				continue;
			}
			byte @Nullable [] key = decode(line.substring(colon + 1).strip());
			if (key == null) {
				problems.add(where + "값이 표준 Base64 가 아니다");
				continue;
			}
			if (key.length != KeyEncryptionKeys.KEY_BYTES) {
				problems.add(where + "값은 " + KeyEncryptionKeys.KEY_BYTES + "바이트여야 한다 (지금 " + key.length + "바이트)");
				continue;
			}
			keys.put(version, key);
		}
		if (problems.isEmpty() && keys.isEmpty()) {
			problems.add("버전:값 줄이 하나도 없다");
		}
		return problems.isEmpty() ? new KekFileRead.Parsed(KeyEncryptionKeys.of(keys))
				: new KekFileRead.Invalid(problems);
	}

	private static @Nullable Integer version(String text) {
		if (text.isEmpty() || !text.chars().allMatch(ch -> ch >= '0' && ch <= '9')) {
			return null;
		}
		try {
			int version = Integer.parseInt(text);
			return (version >= 1) ? version : null;
		}
		catch (NumberFormatException ex) {
			return null;
		}
	}

	private static byte @Nullable [] decode(String text) {
		try {
			return Base64.getDecoder().decode(text);
		}
		catch (IllegalArgumentException ex) {
			return null;
		}
	}

}
