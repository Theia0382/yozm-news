package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 커밋된 화면용 API 명세(저장소 루트 {@code api/openapi.json})와 현재 코드가 내는 명세의 비교. 갱신 태스크
 * ({@code updateOpenApiSpec})에서는 비교 대신 파일을 다시 쓴다. 경로와 갱신 여부는 Gradle 이 시스템 속성으로 넘긴다
 * ({@code backend/build.gradle.kts}).
 * <p>
 * 지키는 규칙: platform-R6
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "springdoc.api-docs.enabled=true")
@AutoConfigureMockMvc
class OpenApiSpecTests {

	static final String PATH_PROPERTY = "yozmnews.openapi-spec-file";

	static final String UPDATE_PROPERTY = "yozmnews.openapi-spec-update";

	private static final String UPDATE_COMMAND = "backend/gradlew -p backend updateOpenApiSpec";

	@Autowired
	MockMvc mockMvc;

	@Test
	void committedSpecMatchesCode() throws Exception {
		Path file = specFile();
		String spec = currentSpec();
		if (Boolean.getBoolean(UPDATE_PROPERTY)) {
			Files.createDirectories(file.getParent());
			Files.writeString(file, spec);
			return;
		}
		assertThat(file).as("명세 파일이 없다 — %s 로 만든다", UPDATE_COMMAND).isRegularFile();
		assertThat(Files.readString(file)).as("커밋된 명세가 코드와 다르다 — %s 로 갱신해 함께 커밋한다", UPDATE_COMMAND)
			.isEqualTo(spec);
	}

	/**
	 * 줄바꿈은 LF, 끝에 개행 하나 — springdoc 의 들여쓰기는 시스템 줄바꿈을 쓰고 끝 개행이 없다. 명세 생성이 실패하면 응답은 메시지 없는
	 * 500 이라 처리된 예외의 메시지를 실패 설명에 싣는다.
	 */
	private String currentSpec() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/v3/api-docs")).andReturn();
		Exception failure = result.getResolvedException();
		assertThat(failure).as("명세 생성이 실패했다 — %s", failure == null ? "" : failure.getMessage()).isNull();
		assertThat(result.getResponse().getStatus()).as("명세 응답 상태").isEqualTo(200);
		return result.getResponse().getContentAsString(StandardCharsets.UTF_8).replace("\r\n", "\n") + "\n";
	}

	private static Path specFile() {
		return GradleProperties.pathFrom(PATH_PROPERTY, System.getProperty(PATH_PROPERTY));
	}

}
