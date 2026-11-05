package net.yozmnews.platform.domain;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 설치된 pgvector 확장 — 판정 정본 · docs/specs/platform.md §4 · platform-R19
 * <p>
 * 버전은 점으로 나눈 숫자 성분을 앞에서부터 견주고, 짧은 쪽의 없는 성분은 0 이다({@code 0.8} = {@code 0.8.0} &lt; {@code 0.10.0}).
 *
 * @param version 설치 버전({@code pg_extension.extversion})
 */
public record VectorExtension(String version) {

	/** 확장 이름 */
	public static final String NAME = "vector";

	/** 최소 버전 */
	public static final String MINIMUM_VERSION = "0.8";

	private static final Pattern NUMERIC_VERSION = Pattern.compile("\\d{1,9}(\\.\\d{1,9})*");

	/**
	 * @throws IllegalStateException 버전을 숫자 성분으로 읽을 수 없거나 최소 버전보다 낮을 때
	 */
	public VectorExtension {
		Objects.requireNonNull(version, "version");
		if (!NUMERIC_VERSION.matcher(version).matches()) {
			throw new IllegalStateException("pgvector 확장 버전 '" + version + "' 을 숫자 성분으로 읽을 수 없다");
		}
		if (compare(components(version), components(MINIMUM_VERSION)) < 0) {
			throw new IllegalStateException("pgvector 확장 버전 " + version + " 은 최소 버전 " + MINIMUM_VERSION
					+ " 보다 낮다 — 확장 소유자 권한으로 ALTER EXTENSION " + NAME + " UPDATE 를 실행해 올린 뒤 다시 띄운다(DB 서버에 "
					+ MINIMUM_VERSION + " 이상의 pgvector 가 있어야 한다)");
		}
	}

	/**
	 * 설치된 확장.
	 * @param installedVersion 설치 버전 — 설치되지 않았으면 빈 값
	 * @return 최소 버전 이상인 설치된 확장
	 * @throws IllegalStateException 설치되지 않았을 때, 그리고 생성자의 조건
	 */
	public static VectorExtension installed(Optional<String> installedVersion) {
		return installedVersion.map(VectorExtension::new)
			.orElseThrow(() -> new IllegalStateException("DB 에 pgvector 확장(" + NAME
					+ ")이 설치되어 있지 않다 — superuser 권한으로 CREATE EXTENSION " + NAME + " 를 실행한 뒤 다시 띄운다"));
	}

	private static int[] components(String numericVersion) {
		return Arrays.stream(numericVersion.split("\\.")).mapToInt(Integer::parseInt).toArray();
	}

	/** 앞에서부터 견주고 짧은 쪽의 없는 성분은 0 이다 */
	private static int compare(int[] left, int[] right) {
		for (int i = 0; i < Math.max(left.length, right.length); i++) {
			int difference = Integer.compare((i < left.length) ? left[i] : 0, (i < right.length) ? right[i] : 0);
			if (difference != 0) {
				return difference;
			}
		}
		return 0;
	}

}
