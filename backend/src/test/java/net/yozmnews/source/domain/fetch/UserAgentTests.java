package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * User-Agent 의 문법과 연락처 검사.
 */
class UserAgentTests {

	@Test
	void namesTheServiceTheProjectAndTheContact() {
		assertThat(new UserAgent("ops@example.org").headerValue())
			.isEqualTo("YozmNews (+https://github.com/Theia0382/yozm-news; ops@example.org)");
		assertThat(new UserAgent("https://news.example/contact").headerValue())
			.isEqualTo("YozmNews (+https://github.com/Theia0382/yozm-news; https://news.example/contact)");
	}

	@Test
	void acceptsAContactOfMaximumLength() {
		String contact = "a".repeat(188) + "@example.org";

		assertThat(contact).hasSize(200);
		assertThat(new UserAgent(contact).contact()).isEqualTo(contact);
	}

	@Test
	void rejectsAContactOverTheLimit() {
		String contact = "a".repeat(189) + "@example.org";

		assertThat(contact).hasSize(201);
		assertThatIllegalArgumentException().isThrownBy(() -> new UserAgent(contact));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "ops @example.org", "ops@example.org (me)", "ops@example.org;x", "ops\u0007@example.org",
			"ftp://x.example", "ops@", "운영자@example.org", "http://", "a\\b@example.org", "not a contact",
			"(ops@example.org", "ops@example.org)" })
	void rejectsAnInvalidContact(String contact) {
		assertThatIllegalArgumentException().isThrownBy(() -> new UserAgent(contact));
	}

}
