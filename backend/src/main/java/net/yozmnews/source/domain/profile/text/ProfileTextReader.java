package net.yozmnews.source.domain.profile.text;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.source.domain.profile.ProblemCode;
import net.yozmnews.source.domain.profile.ProfilePath;
import net.yozmnews.source.domain.profile.ProfileProblem;
import net.yozmnews.source.domain.profile.text.ProfileTree.AliasNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.Entry;
import net.yozmnews.source.domain.profile.text.ProfileTree.ListNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.MapNode;
import net.yozmnews.source.domain.profile.text.ProfileTree.ScalarKind;
import net.yozmnews.source.domain.profile.text.ProfileTree.ScalarNode;
import org.jspecify.annotations.Nullable;
import org.snakeyaml.engine.v2.api.LoadSettings;
import org.snakeyaml.engine.v2.api.lowlevel.Parse;
import org.snakeyaml.engine.v2.common.Anchor;
import org.snakeyaml.engine.v2.events.AliasEvent;
import org.snakeyaml.engine.v2.events.CollectionStartEvent;
import org.snakeyaml.engine.v2.events.Event;
import org.snakeyaml.engine.v2.events.MappingStartEvent;
import org.snakeyaml.engine.v2.events.NodeEvent;
import org.snakeyaml.engine.v2.events.ScalarEvent;
import org.snakeyaml.engine.v2.events.SequenceStartEvent;
import org.snakeyaml.engine.v2.exceptions.Mark;
import org.snakeyaml.engine.v2.exceptions.MarkedYamlEngineException;
import org.snakeyaml.engine.v2.exceptions.YamlEngineException;
import org.snakeyaml.engine.v2.nodes.Tag;
import org.snakeyaml.engine.v2.resolver.JsonScalarResolver;

/**
 * 프로필 원문 읽기기 — docs/specs/source.md §4 · source-R32.
 * <p>
 * YAML(그리고 JSON) 텍스트를 {@link ProfileTree} 로 옮기며 키와 값의 앵커 · 태그 · 위치를 그대로 받는다. 스레드 안전하다.
 */
public final class ProfileTextReader {

	/** 텍스트 길이 상한(문자 수) — 넘으면 읽지 않는다 */
	public static final int MAX_CHARS = 65_536;

	private final JsonScalarResolver resolver = new JsonScalarResolver();

	public ReadResult read(String text) {
		Objects.requireNonNull(text, "text");
		if (text.length() > MAX_CHARS) {
			return unreadable(ProblemCode.TOO_LARGE, "프로필 텍스트는 " + MAX_CHARS + "자까지다 — 지금 " + text.length() + "자",
					TextLocation.UNKNOWN);
		}
		try {
			return new Builder(new Parse(LoadSettings.builder().build()).parseString(text).iterator()).build();
		}
		catch (MarkedYamlEngineException ex) {
			return unreadable(ProblemCode.SYNTAX_ERROR, "YAML 문법 오류 — " + ex.getProblem(),
					ex.getProblemMark().map(ProfileTextReader::location).orElse(TextLocation.UNKNOWN));
		}
		catch (YamlEngineException ex) {
			return unreadable(ProblemCode.SYNTAX_ERROR, "YAML 문법 오류 — " + ex.getMessage(), TextLocation.UNKNOWN);
		}
	}

	private static ReadResult unreadable(ProblemCode code, String message, TextLocation location) {
		return new ReadResult.Unreadable(new ProfileProblem(ProfilePath.ROOT, code, message, location));
	}

	private static TextLocation location(Mark mark) {
		return new TextLocation(mark.getLine() + 1, mark.getColumn() + 1);
	}

	private static TextLocation location(Event event) {
		return event.getStartMark().map(ProfileTextReader::location).orElse(TextLocation.UNKNOWN);
	}

	/**
	 * 이벤트를 따라 트리를 만드는 빌더 — 깊게 중첩된 입력이 스택을 넘치지 않게 재귀하지 않고 열린 컬렉션을 직접 쌓는다
	 */
	private final class Builder {

		private final Iterator<Event> events;

		private final Deque<Open> open = new ArrayDeque<>();

		private @Nullable ProfileTree root;

		Builder(Iterator<Event> events) {
			this.events = events;
		}

		ReadResult build() {
			next(); // StreamStart
			Event event = next();
			if (event.getEventId() == Event.ID.StreamEnd) {
				return new ReadResult.Read(new MapNode(List.of(), new TextLocation(1, 1), null, null));
			}
			// DocumentStart
			readNode(next());
			next(); // DocumentEnd
			Event after = next();
			if (after.getEventId() == Event.ID.DocumentStart) {
				return unreadable(ProblemCode.MULTIPLE_DOCUMENTS, "문서는 하나여야 한다 — '---' 로 나눈 두 번째 문서가 있다",
						location(after));
			}
			ProfileTree tree = Objects.requireNonNull(this.root);
			if (tree instanceof MapNode map) {
				return new ReadResult.Read(map);
			}
			if (tree instanceof ScalarNode scalar && scalar.kind() == ScalarKind.NULL && scalar.text().isEmpty()
					&& scalar.anchor() == null && scalar.tag() == null) {
				// 내용 없는 문서('---' 만)는 빈 텍스트와 같다
				return new ReadResult.Read(new MapNode(List.of(), scalar.location(), null, null));
			}
			return unreadable(ProblemCode.NOT_A_MAPPING, "최상위는 항목을 담은 매핑이어야 한다", tree.location());
		}

		private void readNode(Event first) {
			Event event = first;
			while (true) {
				switch (event) {
					case MappingStartEvent start -> this.open.push(new Open(start, true));
					case SequenceStartEvent start -> this.open.push(new Open(start, false));
					case ScalarEvent scalar -> add(scalar(scalar));
					case AliasEvent alias -> add(new AliasNode(alias.getAlias().getValue(), location(alias)));
					default -> add(close(event));
				}
				if (this.open.isEmpty()) {
					return;
				}
				event = next();
			}
		}

		private ProfileTree close(Event end) {
			Open collection = this.open.pop();
			CollectionStartEvent start = collection.start;
			if (collection.mapping) {
				if (end.getEventId() != Event.ID.MappingEnd) {
					throw new IllegalStateException("매핑 끝이 아닌 이벤트: " + end.getEventId());
				}
				List<Entry> entries = new ArrayList<>();
				for (int i = 0; i < collection.children.size(); i += 2) {
					entries.add(new Entry(collection.children.get(i), collection.children.get(i + 1)));
				}
				return new MapNode(entries, location(start), anchor(start), start.getTag().orElse(null));
			}
			if (end.getEventId() != Event.ID.SequenceEnd) {
				throw new IllegalStateException("목록 끝이 아닌 이벤트: " + end.getEventId());
			}
			return new ListNode(collection.children, location(start), anchor(start), start.getTag().orElse(null));
		}

		private void add(ProfileTree node) {
			Open parent = this.open.peek();
			if (parent == null) {
				this.root = node;
			}
			else {
				parent.children.add(node);
			}
		}

		private ScalarNode scalar(ScalarEvent event) {
			String value = event.getValue();
			Tag tag = ProfileTextReader.this.resolver.resolve(value, event.getImplicit().canOmitTagInPlainScalar());
			return new ScalarNode(kind(tag), value, location(event), anchor(event), event.getTag().orElse(null));
		}

		private static ScalarKind kind(Tag tag) {
			if (Tag.INT.equals(tag)) {
				return ScalarKind.INTEGER;
			}
			if (Tag.FLOAT.equals(tag)) {
				return ScalarKind.DECIMAL;
			}
			if (Tag.BOOL.equals(tag)) {
				return ScalarKind.BOOLEAN;
			}
			if (Tag.NULL.equals(tag)) {
				return ScalarKind.NULL;
			}
			return ScalarKind.STRING;
		}

		private static @Nullable String anchor(NodeEvent event) {
			Optional<Anchor> anchor = event.getAnchor();
			return anchor.map(Anchor::getValue).orElse(null);
		}

		private Event next() {
			return this.events.next();
		}

	}

	private static final class Open {

		final CollectionStartEvent start;

		final boolean mapping;

		final List<ProfileTree> children = new ArrayList<>();

		Open(CollectionStartEvent start, boolean mapping) {
			this.start = start;
			this.mapping = mapping;
		}

	}

}
