package net.yozmnews.llm.domain.schema;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * 출력 스키마가 JSON 객체 문서인가 — 요청의 검사와 공급자 맞춤이 함께 쓰는 판정이다.
 * <p>
 * 지키는 규칙: llm-R21
 */
class SchemaDocumentsTests {

	@Test
	void anObjectDocumentIsAnObject() {
		assertThat(SchemaDocuments.isObject("{}")).isTrue();
		assertThat(SchemaDocuments.isObject(" {\"type\":\"object\"} ")).isTrue();
	}

	@Test
	void otherDocumentsAreNot() {
		assertThat(SchemaDocuments.isObject("[]")).isFalse();
		assertThat(SchemaDocuments.isObject("\"object\"")).isFalse();
		assertThat(SchemaDocuments.isObject("null")).isFalse();
		assertThat(SchemaDocuments.isObject("{\"type\":")).isFalse();
		assertThat(SchemaDocuments.isObject("{} []")).isFalse();
		assertThat(SchemaDocuments.isObject("")).isFalse();
	}

}
