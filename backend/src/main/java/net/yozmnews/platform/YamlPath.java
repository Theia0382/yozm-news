package net.yozmnews.platform;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * YAML 문서 안의 위치 — docs/specs/platform.md §3.
 * <p>
 * 표시는 {@code source.name} · {@code feeds[0].url} · {@code items.summary[1]} 같은 꼴이고, 최상위는 빈 문자열이다.
 */
public record YamlPath(List<Segment> segments) {

	public static final YamlPath ROOT = new YamlPath(List.of());

	public YamlPath {
		segments = List.copyOf(segments);
	}

	public static YamlPath of(String... keys) {
		YamlPath path = ROOT;
		for (String key : keys) {
			path = path.child(key);
		}
		return path;
	}

	public YamlPath child(String key) {
		return append(new Segment.Key(key));
	}

	public YamlPath index(int index) {
		return append(new Segment.Index(index));
	}

	private YamlPath append(Segment segment) {
		List<Segment> appended = new ArrayList<>(this.segments);
		appended.add(segment);
		return new YamlPath(appended);
	}

	@Override
	public String toString() {
		StringBuilder text = new StringBuilder();
		for (Segment segment : this.segments) {
			switch (segment) {
				case Segment.Key key -> {
					if (!text.isEmpty()) {
						text.append('.');
					}
					text.append(key.name());
				}
				case Segment.Index index -> text.append('[').append(index.value()).append(']');
			}
		}
		return text.toString();
	}

	/** 위치의 한 조각 — 매핑 키 또는 목록 번호 */
	public sealed interface Segment {

		/** 매핑 키 */
		record Key(String name) implements Segment {

			public Key {
				Objects.requireNonNull(name, "name");
			}

		}

		/** 목록 번호 */
		record Index(int value) implements Segment {

		}

	}

}
