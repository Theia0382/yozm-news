package net.yozmnews.llm.livecheck;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import net.yozmnews.llm.ReasoningEffort;
import net.yozmnews.llm.adapter.out.sdk.SdkProviderClients;
import net.yozmnews.llm.domain.budget.OutputBudget;
import net.yozmnews.llm.domain.catalog.ModelCatalog;
import net.yozmnews.llm.domain.catalog.ModelCatalogParse;
import net.yozmnews.llm.domain.catalog.ModelCatalogParser;
import net.yozmnews.llm.domain.provider.GenerationCall;
import net.yozmnews.llm.domain.provider.GenerationOutcome;
import net.yozmnews.llm.domain.provider.LlmProviderClient;
import net.yozmnews.platform.SecretValue;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 공급자 확인(⑧) — 운영 포트(실제 주소)로 허용 모델 목록의 모델마다 생성 요청을 보내, 공급자가 맞춘 스키마 · 추론 강도 · 출력 상한을
 * 받는지와 응답의 종료 사유 · 토큰 · 소요 시간 · 오류의 꼴을 기록한다 · docs/qa/test-strategy.md ⑧.
 * <p>
 * 방식은 셋이다 — {@code dry-run}(요청 없이 요청 수와 최대 비용 추정), {@code smoke}(공급자마다 한 건), {@code full}(전부). 뺄 모델
 * ID 를 쉼표로 넘기면 그 모델의 요청을 계획에서 뺀다. 돈이 드는 방식은 계획에 남은 모델의 공급자 키(환경 변수
 * {@value #OPENAI_KEY} · {@value #ANTHROPIC_KEY} · {@value #OPENROUTER_KEY} 가운데 그 공급자의 것)가 모두 있어야 하고,
 * 보내기 전마다 쓴 비용 + 다음 요청의 최대 비용이 상한을 넘으면 멈춘다. 포트가 예상 밖의 예외를 던지면 그 요청은 예외 클래스 이름만
 * 적고 최대 비용으로 센 뒤 다음으로 넘어간다 — 멈추거나 끝나도 그때까지의 결과를 표 파일 하나로 남긴다. 출력에는 키 · 프롬프트 ·
 * 응답 본문 · 예외 문구를 찍지 않는다.
 */
public final class LlmLiveCheck {

	static final String OPENAI_KEY = "YOZMNEWS_LIVE_OPENAI_KEY";

	static final String ANTHROPIC_KEY = "YOZMNEWS_LIVE_ANTHROPIC_KEY";

	static final String OPENROUTER_KEY = "YOZMNEWS_LIVE_OPENROUTER_KEY";

	/** 확인 요청 하나의 마감 */
	private static final Duration DEADLINE = Duration.ofSeconds(180);

	/** 본문 몫 출력 토큰 — 작은 JSON 하나면 된다 */
	private static final int BODY_TOKENS = 200;

	/** 최대 비용 추정에 쓰는 입력 토큰 — 지시 · 사용자 구획 · 스키마를 넉넉히 덮는다 */
	private static final int INPUT_TOKENS_ESTIMATE = 1_000;

	/** 없는 모델의 오류를 보는 요청의 모델 ID */
	private static final String MISSING_MODEL = "yozm-no-such-model";

	private static final String INSTRUCTIONS = "Reply only with a JSON object of the requested shape. "
			+ "word: one English word. count: a small integer. tags: at most three short words. "
			+ "mood: good or bad, or leave it out.";

	private static final String USER_CONTENT = "Describe a calm morning.";

	private static final String PLAIN_INSTRUCTIONS = "Reply with one short English sentence.";

	/** 확인용 스키마 — 공급자 맞춤이 지우는 키워드($schema · minLength · maxLength · maxItems)와 선택 값 목록(mood)을 담는다 */
	private static final String SCHEMA = """
			{"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
			 "properties":{
			  "word":{"type":"string","minLength":1,"maxLength":20},
			  "count":{"type":"integer"},
			  "tags":{"type":"array","items":{"type":"string","maxLength":20},"maxItems":3},
			  "mood":{"type":"string","enum":["good","bad"]}},
			 "required":["word","count","tags"],"additionalProperties":false}
			""";

	/**
	 * 모델 단가(USD, 100만 토큰당 입력 · 출력) — 2026-11-17 확인. OpenAI 는 공식 가격 페이지의 Standard · 짧은 문맥 행, Anthropic 은
	 * 공식 모델 표, OpenRouter 는 공개 모델 목록({@code GET /api/v1/models} 의 {@code pricing}). 바뀌면 이 표를 고친다 — 표에 없는
	 * 모델은 돈이 드는 방식에서 거부한다.
	 */
	private static final Map<String, Price> PRICES = Map.ofEntries(
			Map.entry("gpt-6.1-terra", new Price("1.00", "5.00")),
			Map.entry("gpt-6.1-sol", new Price("2.00", "10.00")),
			Map.entry("gpt-6-luna", new Price("0.10", "0.50")),
			Map.entry("gpt-6-astra", new Price("10.00", "50.00")),
			Map.entry("claude-sonnet-5-5", new Price("2.00", "10.00")),
			Map.entry("claude-opus-5-5", new Price("4.00", "20.00")),
			Map.entry("claude-haiku-5-5", new Price("0.10", "0.50")),
			Map.entry("claude-fable-5-2", new Price("8.00", "40.00")),
			Map.entry("openai/gpt-6.1-terra", new Price("1.00", "5.00")),
			Map.entry("openai/gpt-6.1-sol", new Price("2.00", "10.00")),
			Map.entry("openai/gpt-6-luna", new Price("0.10", "0.50")),
			Map.entry("openai/gpt-6-astra", new Price("10.00", "50.00")),
			Map.entry("anthropic/claude-sonnet-5.5", new Price("2.00", "10.00")),
			Map.entry("anthropic/claude-opus-5.5", new Price("4.00", "20.00")),
			Map.entry("anthropic/claude-haiku-5.5", new Price("0.10", "0.50")),
			Map.entry("anthropic/claude-fable-5.2", new Price("8.00", "40.00")));

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private LlmLiveCheck() {
	}

	/**
	 * @param args 방식(dry-run · smoke · full), 비용 상한(USD), 결과 디렉터리, 뺄 모델 ID(쉼표로 구분 — 없으면 빈 문자열)
	 */
	public static void main(String[] args) {
		if (args.length != 4) {
			fail(64, "인자는 넷이다 — 방식(dry-run | smoke | full), 비용 상한(USD), 결과 디렉터리, 뺄 모델 ID");
		}
		String mode = args[0];
		BigDecimal budget = new BigDecimal(args[1]);
		Path reportDir = Path.of(args[2]);
		Set<String> skipped = Arrays.stream(args[3].split(",")).map(String::strip).filter(id -> !id.isEmpty())
			.collect(Collectors.toSet());
		List<Probe> plan = switch (mode) {
			case "dry-run", "full" -> fullPlan(catalog());
			case "smoke" -> smokePlan(catalog());
			default -> {
				fail(64, "모르는 방식 — " + mode);
				yield List.of();
			}
		};
		plan = plan.stream().filter(probe -> !skipped.contains(probe.model().id())).toList();
		if (!skipped.isEmpty()) {
			System.out.println("뺀 모델 — " + skipped.stream().sorted().toList());
		}
		BigDecimal worst = plan.stream().map(Probe::worstCost).reduce(BigDecimal.ZERO, BigDecimal::add);
		System.out.printf("방식 %s · 요청 %d 건 · 최대 비용 추정 $%s · 상한 $%s%n", mode, plan.size(), usd(worst), usd(budget));
		for (Probe probe : plan) {
			System.out.printf("  %-9s %-20s %-6s 스키마=%-5s 출력상한=%6d 최대 $%s%n", probe.model().provider(),
					probe.model().id(), probe.effort(), probe.schema() != null, probe.call().maxOutputTokens(),
					usd(probe.worstCost()));
		}
		if (mode.equals("dry-run")) {
			return;
		}
		List<String> unpriced = plan.stream()
			.map(probe -> probe.model().id())
			.filter(id -> !id.equals(MISSING_MODEL) && !PRICES.containsKey(id))
			.distinct()
			.toList();
		if (!unpriced.isEmpty()) {
			fail(3, "단가 표에 없는 모델 — " + unpriced + ". 비용 상한을 지킬 수 없어 보내지 않는다");
		}
		Map<Provider, SecretValue> keys = new EnumMap<>(Provider.class);
		List<String> missing = new ArrayList<>();
		for (Provider provider : plan.stream().map(probe -> probe.model().provider()).distinct().toList()) {
			String key = System.getenv(keyVariable(provider));
			if (key == null || key.isBlank()) {
				missing.add(keyVariable(provider));
			}
			else {
				keys.put(provider, SecretValue.of(key));
			}
		}
		if (!missing.isEmpty()) {
			fail(2, "키가 없다 — 계획에 남은 공급자의 환경 변수 " + String.join(" · ", missing) + " 가 있어야 한다");
		}
		run(plan, budget, reportDir, keys, mode);
	}

	/** 공급자의 키를 받는 환경 변수 */
	private static String keyVariable(Provider provider) {
		return switch (provider) {
			case OPENAI -> OPENAI_KEY;
			case ANTHROPIC -> ANTHROPIC_KEY;
			case OPENROUTER -> OPENROUTER_KEY;
		};
	}

	private static void run(List<Probe> plan, BigDecimal budget, Path reportDir, Map<Provider, SecretValue> keys,
			String mode) {
		SdkProviderClients clients = new SdkProviderClients();
		List<String> rows = new ArrayList<>();
		rows.add(String.join("\t", "provider", "model", "effort", "schema_enforced", "outcome", "stop_or_kind",
				"message", "input_tokens", "output_tokens", "elapsed_ms", "body_is_object", "required_keys",
				"cost_usd"));
		BigDecimal spent = BigDecimal.ZERO;
		int sent = 0;
		for (Probe probe : plan) {
			if (spent.add(probe.worstCost()).compareTo(budget) > 0) {
				System.out.printf("상한에 닿아 멈춘다 — 쓴 비용 $%s + 다음 최대 $%s > 상한 $%s (%d / %d 건 보냄)%n", usd(spent),
						usd(probe.worstCost()), usd(budget), sent, plan.size());
				break;
			}
			long begin = System.nanoTime();
			GenerationOutcome outcome;
			String row;
			try (LlmProviderClient client = clients.open(probe.model().provider(), keys.get(probe.model().provider()),
					DEADLINE)) {
				outcome = client.generate(probe.call());
			}
			catch (RuntimeException ex) {
				// 예외 문구에는 키 조각이 실릴 수 있어 클래스 이름만 남긴다. 보낸 뒤에 던졌을 수 있어 최대 비용으로 센다
				long elapsedMs = Duration.ofNanos(System.nanoTime() - begin).toMillis();
				sent++;
				spent = spent.add(probe.worstCost());
				row = exceptionRow(probe, ex, elapsedMs, probe.worstCost());
				rows.add(row);
				System.out.println("  " + row);
				continue;
			}
			long elapsedMs = Duration.ofNanos(System.nanoTime() - begin).toMillis();
			sent++;
			BigDecimal cost = probe.cost(outcome);
			spent = spent.add(cost);
			row = row(probe, outcome, elapsedMs, cost);
			rows.add(row);
			System.out.println("  " + row);
		}
		System.out.printf("보낸 요청 %d / %d 건 · 쓴 비용 추정 $%s%n", sent, plan.size(), usd(spent));
		write(reportDir, mode, rows);
	}

	private static String row(Probe probe, GenerationOutcome outcome, long elapsedMs, BigDecimal cost) {
		return switch (outcome) {
			case GenerationOutcome.Answered answered -> String.join("\t", probe.model().provider().key(),
					probe.model().id(), probe.effort().name(), String.valueOf(probe.call().enforcesSchema()),
					"ANSWERED", answered.stop().name(), "-", String.valueOf(answered.inputTokens()),
					String.valueOf(answered.outputTokens()), String.valueOf(elapsedMs),
					String.valueOf(bodyIsObject(answered.text())), String.valueOf(hasRequiredKeys(answered.text())),
					usd(cost));
			case GenerationOutcome.Failed failed -> String.join("\t", probe.model().provider().key(), probe.model().id(),
					probe.effort().name(), String.valueOf(probe.call().enforcesSchema()), "FAILED",
					failed.kind().name() + (failed.deadlinePassed() ? "(마감)" : ""), failed.message(), "-", "-",
					String.valueOf(elapsedMs), "-", "-", usd(cost));
		};
	}

	private static String exceptionRow(Probe probe, RuntimeException thrown, long elapsedMs, BigDecimal cost) {
		return String.join("\t", probe.model().provider().key(), probe.model().id(), probe.effort().name(),
				String.valueOf(probe.call().enforcesSchema()), "EXCEPTION", thrown.getClass().getSimpleName(), "-", "-",
				"-", String.valueOf(elapsedMs), "-", "-", usd(cost));
	}

	private static List<Probe> fullPlan(ModelCatalog catalog) {
		List<Probe> plan = new ArrayList<>();
		for (LlmModel model : catalog.all()) {
			for (ReasoningEffort effort : ReasoningEffort.values()) {
				plan.add(Probe.of(model, effort, SCHEMA));
			}
			plan.add(Probe.of(model, ReasoningEffort.LOW, null));
		}
		for (Provider provider : Provider.values()) {
			plan.add(Probe.of(new LlmModel(provider, MISSING_MODEL, 1, true), ReasoningEffort.LOW, null));
		}
		return plan;
	}

	private static List<Probe> smokePlan(ModelCatalog catalog) {
		List<Probe> plan = new ArrayList<>();
		for (Provider provider : Provider.values()) {
			catalog.of(provider).stream().findFirst().ifPresent(model -> plan.add(Probe.of(model, ReasoningEffort.LOW,
					SCHEMA)));
		}
		return plan;
	}

	private static ModelCatalog catalog() {
		try (InputStream in = LlmLiveCheck.class.getResourceAsStream("/llm-models/default.yaml")) {
			if (in == null) {
				fail(1, "기본 허용 모델 목록 파일을 찾지 못했다");
			}
			return switch (new ModelCatalogParser().parse(new String(in.readAllBytes(), StandardCharsets.UTF_8))) {
				case ModelCatalogParse.Parsed parsed -> parsed.catalog();
				case ModelCatalogParse.Invalid invalid -> {
					fail(1, "기본 허용 모델 목록이 틀렸다 — " + invalid.problems());
					yield null;
				}
			};
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static boolean bodyIsObject(String text) {
		return read(text).map(JsonNode::isObject).orElse(false);
	}

	private static boolean hasRequiredKeys(String text) {
		return read(text).map(node -> node.has("word") && node.has("count") && node.has("tags")).orElse(false);
	}

	private static Optional<JsonNode> read(String text) {
		try {
			return Optional.of(JSON.readTree(text));
		}
		catch (JacksonException ex) {
			return Optional.empty();
		}
	}

	private static void write(Path dir, String mode, List<String> rows) {
		String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC).format(Instant.now());
		Path file = dir.resolve("llm-" + stamp + "-" + mode + ".tsv");
		try {
			Files.createDirectories(dir);
			Files.write(file, rows, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		System.out.println("결과 표 — " + file);
	}

	private static String usd(BigDecimal value) {
		return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
	}

	private static void fail(int status, String message) {
		System.err.println(message);
		System.exit(status);
	}

	/** 100만 토큰당 입력 · 출력 단가(USD) */
	private record Price(BigDecimal input, BigDecimal output) {

		Price(String input, String output) {
			this(new BigDecimal(input), new BigDecimal(output));
		}

		BigDecimal of(long inputTokens, long outputTokens) {
			return this.input.multiply(BigDecimal.valueOf(inputTokens))
				.add(this.output.multiply(BigDecimal.valueOf(outputTokens)))
				.divide(BigDecimal.valueOf(1_000_000), 6, RoundingMode.HALF_UP);
		}

	}

	/** 확인 요청 하나 */
	private record Probe(LlmModel model, ReasoningEffort effort, String schema, GenerationCall call) {

		static Probe of(LlmModel model, ReasoningEffort effort, String schema) {
			boolean plain = schema == null;
			return new Probe(model, effort, schema, new GenerationCall(model.id(), model.structuredOutput(),
					plain ? PLAIN_INSTRUCTIONS : INSTRUCTIONS, USER_CONTENT, schema, effort,
					OutputBudget.total(BODY_TOKENS, effort)));
		}

		/** 보내기 전의 최대 비용 — 단가가 없는 모델(없는 모델 ID 등)은 과금되지 않는 오류로 본다 */
		BigDecimal worstCost() {
			Price price = PRICES.get(this.model.id());
			return (price == null) ? BigDecimal.ZERO : price.of(INPUT_TOKENS_ESTIMATE, this.call.maxOutputTokens());
		}

		/** 받은 토큰으로 낸 비용 — 실패는 과금되지 않은 것으로 본다(마감 초과는 예외 — 최대 비용으로 센다) */
		BigDecimal cost(GenerationOutcome outcome) {
			Price price = PRICES.get(this.model.id());
			return switch (outcome) {
				case GenerationOutcome.Answered answered ->
					(price == null) ? BigDecimal.ZERO : price.of(answered.inputTokens(), answered.outputTokens());
				case GenerationOutcome.Failed failed -> failed.deadlinePassed() ? worstCost() : BigDecimal.ZERO;
			};
		}

	}

}
