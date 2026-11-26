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
 * 화면용 API 명세의 연산 ID 겹침 — 메서드 이름이 같은 처리기 둘을 더한 앱에서 명세 생성이 실패한다.
 * <p>
 * 지키는 규칙: platform-R129
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = { YozmnewsApplication.class, OpenApiOperationIdCollisionTests.FirstController.class,
		OpenApiOperationIdCollisionTests.SecondController.class }, properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiOperationIdCollisionTests {

	@Autowired
	MockMvc mockMvc;

	@Test
	void twoHandlersWithTheSameMethodNameFailTheSpec() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/v3/api-docs")).andReturn();

		assertThat(result.getResponse().getStatus()).isEqualTo(500);
		assertThat(result.getResolvedException()).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("명세 연산 ID 가 겹친다 — operationIdSample: ")
			.hasMessageContaining("GET /api/operation-id-sample/first")
			.hasMessageContaining("GET /api/operation-id-sample/second");
	}

	// 중첩 클래스라 다른 테스트 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	@PublicApi(reason = "시험 — 명세 연산 ID 겹침만 본다")
	static class FirstController {

		@GetMapping("/api/operation-id-sample/first")
		String operationIdSample() {
			return "first";
		}

	}

	@RestController
	@PublicApi(reason = "시험 — 명세 연산 ID 겹침만 본다")
	static class SecondController {

		@GetMapping("/api/operation-id-sample/second")
		String operationIdSample() {
			return "second";
		}

	}

}
