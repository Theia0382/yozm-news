package net.yozmnews.llm.domain.schema;

import java.util.Objects;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 출력 스키마가 JSON 객체 문서인가를 정하는 정본 — docs/specs/llm.md §3 GenerationRequest · llm-R21.
 * <p>
 * 요청의 검사와 공급자 맞춤이 같은 판정을 쓴다. 읽지 못하는 문서, 객체가 아닌 문서, 객체 뒤에 다른 값이 이어지는 문서는 객체 문서가 아니다.
 */
public final class SchemaDocuments {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private SchemaDocuments() {
	}

	/** JSON 객체 문서인가 */
	public static boolean isObject(String document) {
		return read(document).isPresent();
	}

	/** JSON 객체 문서면 그 트리, 아니면 빈 값 */
	static Optional<ObjectNode> read(String document) {
		Objects.requireNonNull(document, "document");
		try {
			return (JSON.readTree(document) instanceof ObjectNode object) ? Optional.of(object) : Optional.empty();
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
	}

}
