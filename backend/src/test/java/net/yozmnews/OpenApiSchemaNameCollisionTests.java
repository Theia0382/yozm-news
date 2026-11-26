package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import net.yozmnews.platform.PublicApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 화면용 API 명세의 스키마 이름 겹침 — 단순 이름이 같은 두 응답 타입을 내는 시험용 컨트롤러를 더한 앱에서 명세 생성이 실패한다.
 * <p>
 * 지키는 규칙: platform-R128
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = { YozmnewsApplication.class, OpenApiSchemaNameCollisionTests.FirstController.class,
		OpenApiSchemaNameCollisionTests.SecondController.class }, properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiSchemaNameCollisionTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void twoResponseTypesWithTheSameSimpleNameFailTheSpec() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/v3/api-docs")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(500);
		assertThat(result.getResolvedException()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("명세 스키마 이름이 겹친다 — Item: ")
			.hasMessageContaining("net.yozmnews.OpenApiSchemaNameCollisionTests$First$Item")
			.hasMessageContaining("net.yozmnews.OpenApiSchemaNameCollisionTests$Second$Item")
			.message()
			.doesNotContain("Shared");
	}

	// 중첩 클래스라 다른 테스트 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	@PublicApi(reason = "시험 — 명세 스키마 이름 겹침만 본다")
	static class FirstController {

		@GetMapping("/api/collision-sample/first")
		First.Item first() {
			return new First.Item("f");
		}

		@GetMapping("/api/collision-sample/first-shared")
		Shared firstShared() {
			return new Shared("s");
		}

	}

	@RestController
	@PublicApi(reason = "시험 — 명세 스키마 이름 겹침만 본다")
	static class SecondController {

		@GetMapping("/api/collision-sample/second")
		Second.Item second() {
			return new Second.Item(2);
		}

		@GetMapping("/api/collision-sample/second-shared")
		Shared secondShared() {
			return new Shared("s");
		}

	}

	static final class First {

		record Item(String first) {
		}

	}

	static final class Second {

		record Item(long second) {
		}

	}

	record Shared(String value) {
	}

}
