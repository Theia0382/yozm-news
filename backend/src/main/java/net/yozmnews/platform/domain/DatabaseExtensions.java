package net.yozmnews.platform.domain;

import java.util.Optional;

/** DB 에 설치된 확장을 읽는다 — docs/specs/platform.md §4 · platform-R19 */
public interface DatabaseExtensions {

	/**
	 * 설치된 확장의 버전.
	 * @param name 확장 이름({@code pg_extension.extname})
	 * @return 설치 버전 — 설치되지 않았으면 빈 값
	 */
	Optional<String> installedVersion(String name);

}
