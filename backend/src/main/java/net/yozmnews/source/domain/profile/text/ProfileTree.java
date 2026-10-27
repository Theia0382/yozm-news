package net.yozmnews.source.domain.profile.text;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * 프로필 텍스트를 받은 그대로 옮긴 트리. 판정은 하지 않는다 — 같은 매핑 안의 중복 키, 별칭, 앵커, 명시 태그도 버리지 않고
 * 남긴다. 그것이 오류인지는 트리를 읽는 쪽이 정한다.
 */
public sealed interface ProfileTree {

	TextLocation location();

	/** YAML 앵커({@code &name}). 없으면 null */
	@Nullable String anchor();

	/** 명시 태그({@code !!str}, {@code !foo}). 없으면 null */
	@Nullable String tag();

	/** 매핑. 항목은 텍스트에 나온 순서이고, 같은 키가 두 번 나오면 두 항목이 모두 있다 */
	record MapNode(List<Entry> entries, TextLocation location, @Nullable String anchor,
			@Nullable String tag) implements ProfileTree {

		public MapNode {
			entries = List.copyOf(entries);
			Objects.requireNonNull(location, "location");
		}

	}

	/** 매핑 항목. 키는 보통 {@link ScalarNode} 지만 YAML 복합 키({@code ? [a, b]})면 다른 노드다 */
	record Entry(ProfileTree key, ProfileTree value) {

		public Entry {
			Objects.requireNonNull(key, "key");
			Objects.requireNonNull(value, "value");
		}

	}

	/** 목록 */
	record ListNode(List<ProfileTree> items, TextLocation location, @Nullable String anchor,
			@Nullable String tag) implements ProfileTree {

		public ListNode {
			items = List.copyOf(items);
			Objects.requireNonNull(location, "location");
		}

	}

	/** 스칼라. text 는 원문 값이고, kind 는 YAML 1.2 JSON 스키마로 해석한 종류다 */
	record ScalarNode(ScalarKind kind, String text, TextLocation location, @Nullable String anchor,
			@Nullable String tag) implements ProfileTree {

		public ScalarNode {
			Objects.requireNonNull(kind, "kind");
			Objects.requireNonNull(text, "text");
			Objects.requireNonNull(location, "location");
		}

	}

	/** 별칭({@code *name}). 펼치지 않고 가리키는 앵커 이름만 둔다 */
	record AliasNode(String anchorName, TextLocation location) implements ProfileTree {

		public AliasNode {
			Objects.requireNonNull(anchorName, "anchorName");
			Objects.requireNonNull(location, "location");
		}

		@Override
		public @Nullable String anchor() {
			return null;
		}

		@Override
		public @Nullable String tag() {
			return null;
		}

	}

	/**
	 * 스칼라 종류 — YAML 1.2 JSON 스키마. 불리언은 소문자 {@code true} · {@code false} 뿐이고, {@code no} · {@code 015} ·
	 * {@code ~} 와 따옴표 친 값은 문자열이다.
	 */
	enum ScalarKind {

		STRING, INTEGER, DECIMAL, BOOLEAN, NULL

	}

}
