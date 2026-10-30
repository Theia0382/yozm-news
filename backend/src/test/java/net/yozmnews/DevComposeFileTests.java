package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 개발용 Compose 파일의 서비스 이미지 읽기 — 임시 디렉터리에 쓴 Compose 파일로 본다
 */
class DevComposeFileTests {

	@TempDir
	Path dir;

	@Test
	void readsImageOfNamedService() throws IOException {
		// 첫 서비스가 아니라 이름으로 고른다
		Path file = write("compose.yaml", """
				services:
				  web:
				    image: example/web:1
				  postgres:
				    image: postgres:99-probe
				""");

		assertThat(DevComposeFile.imageOf(file, "postgres")).isEqualTo("postgres:99-probe");
	}

	@Test
	void failsWhenFileIsMissing() {
		Path missing = this.dir.resolve("absent.yaml");

		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.imageOf(missing, "postgres"))
			.withMessageContaining("개발용 Compose 파일이 없다")
			.withMessageContaining(missing.toString());
	}

	@Test
	void failsWhenServiceIsMissing() throws IOException {
		Path file = write("compose.yaml", """
				services:
				  web:
				    image: example/web:1
				""");

		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.imageOf(file, "postgres"))
			.withMessageContaining("서비스 'postgres' 가 없다");
	}

	@Test
	void failsWhenImageIsMissingOrBlank() throws IOException {
		Path noImage = write("no-image.yaml", """
				services:
				  postgres:
				    profiles: [dev]
				""");
		Path blankImage = write("blank-image.yaml", """
				services:
				  postgres:
				    image: ""
				""");

		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.imageOf(noImage, "postgres"))
			.withMessageContaining("image 가 없다");
		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.imageOf(blankImage, "postgres"))
			.withMessageContaining("image 가 없다");
	}

	@Test
	void failsWhenPathPropertyIsAbsent() {
		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.pathFrom(null))
			.withMessageContaining("yozmnews.dev-compose-file");
		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.pathFrom("  "))
			.withMessageContaining("yozmnews.dev-compose-file");
	}

	@Test
	void failsWhenImageIsInterpolated() throws IOException {
		Path braced = write("braced.yaml", """
				services:
				  postgres:
				    image: "postgres:${PG:-18}"
				""");
		Path bare = write("bare.yaml", """
				services:
				  postgres:
				    image: "postgres:$PG"
				""");

		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.imageOf(braced, "postgres"))
			.withMessageContaining("보간식");
		assertThatIllegalStateException().isThrownBy(() -> DevComposeFile.imageOf(bare, "postgres"))
			.withMessageContaining("보간식");
	}

	private Path write(String name, String yaml) throws IOException {
		return Files.writeString(this.dir.resolve(name), yaml);
	}

}
