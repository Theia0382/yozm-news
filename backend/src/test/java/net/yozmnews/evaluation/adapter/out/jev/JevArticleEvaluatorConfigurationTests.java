package net.yozmnews.evaluation.adapter.out.jev;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import net.yozmnews.evaluation.AskedTag;
import net.yozmnews.evaluation.FormatChoice;
import net.yozmnews.evaluation.FormatOption;
import net.yozmnews.evaluation.ImportanceLevel;
import net.yozmnews.evaluation.ImportanceScale;
import net.yozmnews.evaluation.TagQuestions;
import net.yozmnews.evaluation.TopicCategory;
import net.yozmnews.evaluation.adapter.out.fake.FakeArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.ArticleEvaluator;
import net.yozmnews.evaluation.domain.evaluator.EvaluationInput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * 평가기 결선 — 키 유무와 profile 에 따라 Jev · 로컬 대체물 가운데 하나만 등록하고, 틀린 설정은 기동을 막는다. 로컬 대체물의 결선도
 * 함께 띄워 둘이 겹치지 않음을 본다. {@code ApplicationContextRunner} 는 {@code application.yaml} 을 읽지 않으므로 키를 주는 다리는
 * 모델 · 기본 주소도 준다.
 * <p>
 * 지키는 규칙: platform-R18 · docs/specs/evaluation.md §7
 */
class JevArticleEvaluatorConfigurationTests {

	@RegisterExtension
	static WireMockExtension server = WireMockExtension.newInstance()
		.options(wireMockConfig().dynamicPort().gzipDisabled(true))
		.build();

	private static final String KEY = "tsk-configuration-only-0123";

	private static final String LOCAL = "spring.profiles.active=local,web,worker";

	private static final String ROLES = "spring.profiles.active=web,worker";

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
		.withPropertyValues("spring.profiles.default=web,worker")
		.withUserConfiguration(JevArticleEvaluatorConfiguration.class, fakeConfiguration());

	@Test
	void usesTheFakeInLocalDevelopmentWithoutAKey() {
		this.runner.withPropertyValues(LOCAL).run(context -> assertSingle(context, FakeArticleEvaluator.class));
	}

	@Test
	void usesJevInLocalDevelopmentWithAKey() {
		this.runner.withPropertyValues(LOCAL, "yozmnews.jev.api-key=" + KEY, "yozmnews.jev.model=jev-latest",
				"yozmnews.jev.base-url=https://api.typesafe.ai")
			.run(context -> assertSingle(context, JevArticleEvaluator.class));
	}

	@Test
	void usesJevWithAKeyOutsideLocalDevelopment() {
		this.runner.withPropertyValues(ROLES, "yozmnews.jev.api-key=" + KEY, "yozmnews.jev.model=jev-latest",
				"yozmnews.jev.base-url=https://api.typesafe.ai")
			.run(context -> assertSingle(context, JevArticleEvaluator.class));
	}

	@Test
	void registersNoEvaluatorWithoutAKeyOutsideLocalDevelopment() {
		this.runner.withPropertyValues(ROLES)
			.run(context -> assertThat(context).hasNotFailed().doesNotHaveBean(ArticleEvaluator.class));
	}

	@Test
	void treatsAnEmptyKeyAsNoKey() {
		this.runner.withPropertyValues(LOCAL, "yozmnews.jev.api-key=")
			.run(context -> assertSingle(context, FakeArticleEvaluator.class));
	}

	@Test
	void refusesAKeyWithASpaceWithoutTellingIt() {
		this.runner.withPropertyValues(ROLES, "yozmnews.jev.api-key=tsk secret-part", "yozmnews.jev.model=jev-latest",
				"yozmnews.jev.base-url=https://api.typesafe.ai")
			.run(context -> {
				String failure = failure(context);
				assertThat(failure).contains("yozmnews.jev.api-key(환경 변수 YOZMNEWS_JEV_API_KEY)가 틀렸다 — "
						+ "Jev API 키에는 공백 · 제어 문자 · ASCII 밖의 문자를 쓸 수 없다");
				assertThat(failure).doesNotContain("secret-part");
			});
	}

	@Test
	void refusesABlankModel() {
		this.runner.withPropertyValues(ROLES, "yozmnews.jev.api-key=" + KEY, "yozmnews.jev.model= ",
				"yozmnews.jev.base-url=https://api.typesafe.ai")
			.run(context -> assertThat(failure(context))
				.contains("yozmnews.jev.model(환경 변수 YOZMNEWS_JEV_MODEL)이 비었다"));
	}

	@Test
	void refusesABaseUrlThatIsNotAPlainWebAddress() {
		for (String url : List.of("ftp://api.typesafe.ai", "http:/v1", "https://api.typesafe.ai/?region=kr",
				"https://api.typesafe.ai/#top")) {
			this.runner.withPropertyValues(ROLES, "yozmnews.jev.api-key=" + KEY, "yozmnews.jev.model=jev-latest",
					"yozmnews.jev.base-url=" + url)
				.run(context -> assertThat(failure(context)).as(url)
					.contains("yozmnews.jev.base-url(환경 변수 YOZMNEWS_JEV_BASE_URL)은 호스트가 있고 "
							+ "질의 · 조각이 없는 http · https 주소여야 한다"));
		}
	}

	@Test
	void hidesTheKeyInTheSettings() {
		this.runner.withPropertyValues(ROLES, "yozmnews.jev.api-key=" + KEY, "yozmnews.jev.model=jev-latest",
				"yozmnews.jev.base-url=https://api.typesafe.ai")
			.run(context -> assertThat(context.getBean(JevProperties.class)).hasToString(
					"JevProperties[apiKey=숨김, model=jev-latest, baseUrl=https://api.typesafe.ai]"));
	}

	@Test
	void sendsTheConfiguredModelToTheConfiguredAddress() {
		server.stubFor(post("/v1/systemone").willReturn(aResponse().withStatus(503)));
		TagQuestions questions = new TagQuestions(
				List.of(new TopicCategory("economy", "경제", "경제", List.of(new AskedTag("economy.macro", "거시", "거시", "거시인가")))),
				List.of(new AskedTag("domestic", "국내", "국내", "국내인가")),
				new FormatChoice("형식은", List.of(new FormatOption("straight", "스트레이트", "사실 보도", false))),
				new ImportanceScale("중요한가", List.of(new ImportanceLevel(1, "작다"))));

		this.runner.withPropertyValues(ROLES, "yozmnews.jev.api-key=" + KEY, "yozmnews.jev.model=jev-1.13.0",
				"yozmnews.jev.base-url=" + server.baseUrl())
			.run(context -> context.getBean(ArticleEvaluator.class)
				.evaluate(new EvaluationInput("제목", null, List.of()), questions));

		server.verify(1, postRequestedFor(urlEqualTo("/v1/systemone"))
			.withRequestBody(matchingJsonPath("$.model", equalTo("jev-1.13.0"))));
	}

	/** 로컬 대체물의 결선 — 다른 패키지의 패키지 안 클래스라 이름으로 읽는다 */
	private static Class<?> fakeConfiguration() {
		try {
			return Class.forName("net.yozmnews.evaluation.adapter.out.fake.FakeArticleEvaluatorConfiguration");
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static void assertSingle(AssertableApplicationContext context, Class<?> type) {
		assertThat(context).hasNotFailed().hasSingleBean(ArticleEvaluator.class);
		assertThat(context.getBean(ArticleEvaluator.class)).isInstanceOf(type);
	}

	/** 기동 실패의 예외 사슬 전체(문구 포함) */
	private static String failure(AssertableApplicationContext context) {
		assertThat(context).hasFailed();
		StringWriter trace = new StringWriter();
		context.getStartupFailure().printStackTrace(new PrintWriter(trace));
		return trace.toString();
	}

}
