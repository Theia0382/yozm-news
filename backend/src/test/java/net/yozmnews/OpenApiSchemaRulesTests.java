package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 화면용 API 명세의 범위와 속성 규칙 — 명세는 {@code /api/**} 만 담고, 속성은 모두 필수, JSpecify {@code @Nullable} 은
 * null 허용 타입. 규칙이 실제 응답과 맞는지(null 속성도 키째 쓰는지)를 같은 표본으로 함께 본다.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = { YozmnewsApplication.class, OpenApiSchemaRulesTests.RulesSampleController.class },
		properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiSchemaRulesTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void specCoversScreenApiPathsOnly() throws Exception {
		JsonNode paths = JsonMapper.builder().build().readTree(body("/v3/api-docs")).path("paths");

		assertThat(paths.has("/api/rules-sample")).as("화면용 API 경로").isTrue();
		assertThat(paths.has("/public-api/rules-sample")).as("화면용 API 밖의 경로").isFalse();
	}

	@Test
	void allPropertiesAreRequiredAndNullableOnesAllowNull() throws Exception {
		JsonNode schemas = JsonMapper.builder().build().readTree(body("/v3/api-docs")).at("/components/schemas");

		JSONAssert.assertEquals("""
				{
				  "type": "object",
				  "properties": {
				    "name": { "type": "string" },
				    "nickname": { "type": ["string", "null"] },
				    "tags": { "type": "array", "items": { "type": "string" } },
				    "child": { "oneOf": [ { "$ref": "#/components/schemas/RulesChild" }, { "type": "null" } ] },
				    "sibling": { "$ref": "#/components/schemas/RulesChild" }
				  },
				  "required": ["child", "name", "nickname", "sibling", "tags"]
				}
				""", schemas.path("RulesSample").toString(), JSONCompareMode.STRICT);
		JSONAssert.assertEquals("""
				{
				  "type": "object",
				  "properties": { "value": { "type": "string" } },
				  "required": ["value"]
				}
				""", schemas.path("RulesChild").toString(), JSONCompareMode.STRICT);
	}

	@Test
	void responseWritesNullPropertiesWithTheirKeys() throws Exception {
		JSONAssert.assertEquals("""
				{"name":"n","nickname":null,"tags":[],"child":null,"sibling":{"value":"v"}}
				""", body("/api/rules-sample"), JSONCompareMode.STRICT);
	}

	private String body(String path) throws Exception {
		return this.mockMvc.perform(get(path)).andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
	}

	// 중첩 클래스라 다른 테스트 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	static class RulesSampleController {

		@GetMapping("/api/rules-sample")
		RulesSample rulesSample() {
			return new RulesSample("n", null, List.of(), null, new RulesChild("v"));
		}

		@GetMapping("/public-api/rules-sample")
		RulesChild outsideScreenApi() {
			return new RulesChild("v");
		}

	}

	record RulesSample(String name, @Nullable String nickname, List<String> tags, @Nullable RulesChild child,
			RulesChild sibling) {
	}

	record RulesChild(String value) {
	}

}
