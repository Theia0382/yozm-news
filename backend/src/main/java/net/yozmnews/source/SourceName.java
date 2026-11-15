package net.yozmnews.source;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 고를 소스 하나 — docs/specs/source.md §3.
 * @param id 소스
 * @param name 출처 이름 — 프로필의 소스 이름, 프로필을 얻지 못하면 기록된 이름(source-R57). 둘 다 없으면 null
 */
public record SourceName(SourceId id, @Nullable String name) {

	public SourceName {
		Objects.requireNonNull(id, "id");
	}

}
