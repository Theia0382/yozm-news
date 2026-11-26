package net.yozmnews.platform.adapter.in.web;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.JavaType;
import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * 화면용 API 명세의 스키마 이름 겹침 검사 — docs/specs/platform.md §4 · platform-R128. 명세 생성 한 번 동안 생성기가 정의한 스키마
 * 이름마다 처음 본 타입을 기록하고, 생성 끝에서 같은 이름에 다른 타입이 왔으면 실패시킨다.
 * <p>
 * 기록은 생성 시작({@link #start()})부터 끝({@link #finish(Components)})까지 이 스레드에 둔다. springdoc 은 변환기를 JVM 정적
 * 목록에 등록하고 같은 클래스의 앞 인스턴스를 갈아 끼우므로, 사슬에 든 인스턴스와 이 구성의 bean 인스턴스가 다를 수 있다. 생성 하나는 한
 * 스레드에서 돈다. 변환기 안에서 던진 예외는 springdoc 이 경고 로그로 삼키고 그 스키마를 뺀 명세를 내므로, 변환기는 기록만 하고 실패는
 * 생성 끝에서 낸다.
 */
final class SchemaNameCheck implements ModelConverter {

	private static final ThreadLocal<Recording> RECORD = new ThreadLocal<>();

	/** 이 스레드에서 새 기록을 시작한다 — 앞 생성이 남긴 기록은 버린다 */
	static void start() {
		RECORD.set(new Recording());
	}

	/**
	 * 이 스레드의 기록을 꺼내 지우고 명세의 스키마와 대조한다. 기록이 없으면(시작하지 않은 생성) 아무것도 하지 않는다.
	 * @throws IllegalStateException 같은 이름을 서로 다른 타입이 받았을 때, 명세의 스키마 이름 가운데 기록에 없는 것이 있을 때(변환기가
	 * 사슬에 없다)
	 */
	static void finish(@Nullable Components components) {
		Recording record = RECORD.get();
		RECORD.remove();
		if (record == null) {
			return;
		}
		List<String> problems = new ArrayList<>();
		for (Collision collision : record.collisions) {
			problems.add("명세 스키마 이름이 겹친다 — " + collision.name() + ": " + collision.first() + " · "
					+ collision.other());
		}
		Set<String> unseen = new LinkedHashSet<>();
		if (components != null && components.getSchemas() != null) {
			for (String name : components.getSchemas().keySet()) {
				if (!record.seen.contains(name)) {
					unseen.add(name);
				}
			}
		}
		if (!unseen.isEmpty()) {
			problems.add("명세 스키마의 타입을 보지 못했다 — " + String.join(" · ", unseen));
		}
		if (!problems.isEmpty()) {
			throw new IllegalStateException(String.join("\n", problems));
		}
	}

	@Override
	public @Nullable Schema resolve(AnnotatedType type, ModelConverterContext context,
			Iterator<ModelConverter> chain) {
		if (!chain.hasNext()) {
			return null;
		}
		Recording record = RECORD.get();
		if (record == null) {
			return chain.next().resolve(type, context, chain);
		}
		Schema resolved = chain.next().resolve(type, new RecordingContext(context, record), chain);
		if (resolved != null && resolved.getName() != null) {
			record.seen.add(resolved.getName());
		}
		return resolved;
	}

	/** 생성 한 번의 기록 — 이름별 첫 타입, 본 이름, 겹침 */
	private static final class Recording {

		private final Map<String, JavaType> firstTypes = new HashMap<>();

		private final Set<String> seen = new HashSet<>();

		private final Set<Collision> collisions = new LinkedHashSet<>();

		void define(String name, @Nullable Type type) {
			this.seen.add(name);
			if (type == null) {
				return;
			}
			JavaType normalized = Json.mapper().constructType(type);
			JavaType first = this.firstTypes.putIfAbsent(name, normalized);
			if (first != null && !first.equals(normalized)) {
				this.collisions.add(new Collision(name, first.toCanonical(), normalized.toCanonical()));
			}
		}

	}

	private record Collision(String name, String first, String other) {
	}

	/** 받은 context 에 모두 맡기고 스키마 정의만 기록한다 */
	private record RecordingContext(ModelConverterContext delegate, Recording record) implements ModelConverterContext {

		@Override
		public void defineModel(String name, Schema model) {
			this.record.define(name, null);
			this.delegate.defineModel(name, model);
		}

		@Override
		public void defineModel(String name, Schema model, AnnotatedType type, String prevName) {
			this.record.define(name, type == null ? null : type.getType());
			this.delegate.defineModel(name, model, type, prevName);
		}

		@Override
		public void defineModel(String name, Schema model, Type type, String prevName) {
			this.record.define(name, type);
			this.delegate.defineModel(name, model, type, prevName);
		}

		@Override
		public Schema resolve(AnnotatedType type) {
			return this.delegate.resolve(type);
		}

		@Override
		public Map<String, Schema> getDefinedModels() {
			return this.delegate.getDefinedModels();
		}

		@Override
		public Iterator<ModelConverter> getConverters() {
			return this.delegate.getConverters();
		}

	}

}
