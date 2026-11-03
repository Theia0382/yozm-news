package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import net.yozmnews.CodeFences.Block;
import org.junit.jupiter.api.Test;

class CodeFencesTests {

	@Test
	void extractsBacktickAndTildeFencesInDocumentOrder() {
		String markdown = """
				# 제목

				```yaml
				source:
				  name: 가
				```

				문단

				~~~sh
				echo hi
				~~~
				""";

		assertThat(CodeFences.extract(markdown)).containsExactly(new Block(3, "yaml", "source:\n  name: 가\n"),
				new Block(10, "sh", "echo hi\n"));
	}

	@Test
	void removesTheOpeningIndentFromAFenceInsideAListItem() {
		String markdown = """
				1. 단계

				   ```yaml
				   source:
				     name: 가
				   ```
				""";

		assertThat(CodeFences.extract(markdown)).containsExactly(new Block(3, "yaml", "source:\n  name: 가\n"));
	}

	@Test
	void takesTheFirstWordOfTheInfoStringOrNothing() {
		String markdown = """
				```
				a
				```
				```yaml title
				b
				```
				""";

		assertThat(CodeFences.extract(markdown)).containsExactly(new Block(1, "", "a\n"), new Block(4, "yaml", "b\n"));
	}

	@Test
	void keepsAShorterFenceInsideALongerOneAsContent() {
		String markdown = """
				````yaml
				```
				x
				````
				""";

		assertThat(CodeFences.extract(markdown)).containsExactly(new Block(1, "yaml", "```\nx\n"));
	}

	@Test
	void failsOnAnUnclosedFence() {
		assertThatIllegalArgumentException().isThrownBy(() -> CodeFences.extract("문단\n```yaml\nsource:\n"))
			.withMessageContaining("2번째 줄");
	}

	@Test
	void findsNothingWithoutFences() {
		assertThat(CodeFences.extract("문단\n\n    들여쓴 코드\n")).isEmpty();
	}

}
