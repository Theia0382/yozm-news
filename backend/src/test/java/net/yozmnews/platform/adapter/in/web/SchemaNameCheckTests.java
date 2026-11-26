package net.yozmnews.platform.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverterContextImpl;
import io.swagger.v3.core.jackson.ModelResolver;
import io.swagger.v3.core.util.Json31;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 스키마 이름 겹침 검사 — 생성기의 변환기 사슬(이 변환기 + swagger-core {@code ModelResolver})에 타입을 풀어 본다.
 * <p>
 * 지키는 규칙: platform-R128
 */
class SchemaNameCheckTests {

	private static final String FIRST_ITEM = "net.yozmnews.platform.adapter.in.web.SchemaNameCheckTests$First$Item";

	private static final String SECOND_ITEM = "net.yozmnews.platform.adapter.in.web.SchemaNameCheckTests$Second$Item";

	/** 기록은 스레드에 남으므로 시험마다 지운다 — {@code finish} 는 던지기 전에 기록을 지운다 */
	@AfterEach
	void clearRecording() {
		try {
			SchemaNameCheck.finish(null);
		}
		catch (IllegalStateException ignored) {
			// 끝내지 않은 시험의 겹침 — 지우기만 한다
		}
	}

	@Test
	void twoTypesWithTheSameSimpleNameFail() {
		SchemaNameCheck.start();
		ModelConverterContextImpl context = context();
		context.resolve(new AnnotatedType(First.Item.class));
		context.resolve(new AnnotatedType(Second.Item.class));

		assertThatIllegalStateException().isThrownBy(() -> SchemaNameCheck.finish(components(context)))
			.withMessageContaining("명세 스키마 이름이 겹친다 — Item: ")
			.withMessageContaining(FIRST_ITEM)
			.withMessageContaining(SECOND_ITEM);
	}

	@Test
	void typesWithDifferentNamesPass() {
		SchemaNameCheck.start();
		ModelConverterContextImpl context = context();
		context.resolve(new AnnotatedType(Alpha.class));
		context.resolve(new AnnotatedType(Beta.class));

		assertThat(context.getDefinedModels()).containsOnlyKeys("Alpha", "Beta");
		assertThatNoException().isThrownBy(() -> SchemaNameCheck.finish(components(context)));
	}

	@Test
	void collisionAcrossContextsOfOneGenerationFails() {
		SchemaNameCheck.start();
		ModelConverterContextImpl first = context();
		first.resolve(new AnnotatedType(First.Item.class));
		ModelConverterContextImpl second = context();
		second.resolve(new AnnotatedType(Second.Item.class));

		assertThatIllegalStateException().isThrownBy(() -> SchemaNameCheck.finish(components(first)))
			.withMessageContaining(FIRST_ITEM)
			.withMessageContaining(SECOND_ITEM);
	}

	@Test
	void oneTypeUsedInManyPlacesPasses() {
		SchemaNameCheck.start();
		ModelConverterContextImpl context = context();
		context.resolve(new AnnotatedType(Left.class));
		context.resolve(new AnnotatedType(Right.class));
		ModelConverterContextImpl other = context();
		other.resolve(new AnnotatedType(Shared.class));

		assertThat(context.getDefinedModels()).containsOnlyKeys("Left", "Right", "Shared");
		assertThatNoException().isThrownBy(() -> SchemaNameCheck.finish(components(context)));
	}

	@Test
	void schemaTheConverterDidNotSeeFails() {
		SchemaNameCheck.start();
		ModelConverterContextImpl context = context();
		context.resolve(new AnnotatedType(Alpha.class));
		Components components = components(context).addSchemas("Unseen", new Schema<>());

		assertThatIllegalStateException().isThrownBy(() -> SchemaNameCheck.finish(components))
			.withMessage("명세 스키마의 타입을 보지 못했다 — Unseen");
	}

	@Test
	void withoutStartNothingIsRecorded() {
		ModelConverterContextImpl context = context();
		context.resolve(new AnnotatedType(First.Item.class));
		context.resolve(new AnnotatedType(Second.Item.class));

		assertThatNoException().isThrownBy(() -> SchemaNameCheck.finish(components(context)));
	}

	@Test
	void startDropsTheRecordingOfAnEarlierGeneration() {
		SchemaNameCheck.start();
		ModelConverterContextImpl earlier = context();
		earlier.resolve(new AnnotatedType(First.Item.class));
		earlier.resolve(new AnnotatedType(Second.Item.class));

		SchemaNameCheck.start();
		ModelConverterContextImpl next = context();
		next.resolve(new AnnotatedType(Alpha.class));

		assertThatNoException().isThrownBy(() -> SchemaNameCheck.finish(components(next)));
	}

	@Test
	void finishEndsTheRecording() {
		SchemaNameCheck.start();
		ModelConverterContextImpl context = context();
		context.resolve(new AnnotatedType(First.Item.class));
		context.resolve(new AnnotatedType(Second.Item.class));
		assertThatIllegalStateException().isThrownBy(() -> SchemaNameCheck.finish(components(context)));

		assertThatNoException().isThrownBy(() -> SchemaNameCheck.finish(components(context)));
	}

	private static ModelConverterContextImpl context() {
		return new ModelConverterContextImpl(
				List.of(new SchemaNameCheck(), new ModelResolver(Json31.mapper()).openapi31(true)));
	}

	private static Components components(ModelConverterContextImpl context) {
		Map<String, Schema> defined = context.getDefinedModels();
		return new Components().schemas(new LinkedHashMap<>(defined));
	}

	static final class First {

		record Item(String first) {
		}

	}

	static final class Second {

		record Item(long second) {
		}

	}

	record Alpha(String value) {
	}

	record Beta(String value) {
	}

	record Shared(String value) {
	}

	record Left(Shared shared) {
	}

	record Right(Shared shared) {
	}

}
