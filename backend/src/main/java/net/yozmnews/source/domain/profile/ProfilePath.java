package net.yozmnews.source.domain.profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 프로필 안의 위치. 표시는 {@code source.name} · {@code feeds[0].url} · {@code items.summary[1]} 같은 꼴이고, 최상위는 빈
 * 문자열이다.
 */
public record ProfilePath(List<Segment> segments) {

	public static final ProfilePath ROOT = new ProfilePath(List.of());

	public ProfilePath {
		segments = List.copyOf(segments);
	}

	public static ProfilePath of(String... keys) {
		ProfilePath path = ROOT;
		for (String key : keys) {
			path = path.child(key);
		}
		return path;
	}

	public ProfilePath child(String key) {
		return append(new Segment.Key(key));
	}

	public ProfilePath index(int index) {
		return append(new Segment.Index(index));
	}

	private ProfilePath append(Segment segment) {
		List<Segment> appended = new ArrayList<>(this.segments);
		appended.add(segment);
		return new ProfilePath(appended);
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

	public sealed interface Segment {

		record Key(String name) implements Segment {

			public Key {
				Objects.requireNonNull(name, "name");
			}

		}

		record Index(int value) implements Segment {

		}

	}

}
