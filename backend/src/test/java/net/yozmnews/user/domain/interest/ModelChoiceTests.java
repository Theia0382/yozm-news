package net.yozmnews.user.domain.interest;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import net.yozmnews.llm.LlmModel;
import net.yozmnews.llm.Provider;
import org.junit.jupiter.api.Test;

/**
 * 쓸 모델 — 고른 모델을 쓰거나, 못 쓰면 까닭 하나(앞의 것부터)와 쓸 수 있는 키가 있는 공급자(선언 순)의 첫 모델로 대체한다.
 * <p>
 * 지키는 규칙: user-R51
 */
class ModelChoiceTests {

	private static final LlmModel ASTRA = new LlmModel(Provider.OPENAI, "gpt-astra", 32000, true);

	private static final LlmModel SOL = new LlmModel(Provider.OPENAI, "gpt-sol", 32000, true);

	private static final LlmModel FABLE = new LlmModel(Provider.ANTHROPIC, "claude-fable", 32000, true);

	private static final LlmModel HAIKU = new LlmModel(Provider.ANTHROPIC, "claude-haiku", 32000, true);

	private static final List<LlmModel> ALLOWED = List.of(ASTRA, SOL, FABLE, HAIKU);

	private static final Set<Provider> BOTH = Set.of(Provider.OPENAI, Provider.ANTHROPIC);

	@Test
	void aChosenListedModelWithAValidKeyIsUsed() {
		ModelRef haiku = new ModelRef(Provider.ANTHROPIC, "claude-haiku");

		assertThat(ModelChoice.resolve(haiku, ALLOWED, BOTH, BOTH))
			.isEqualTo(new ModelResolution(haiku, HAIKU, ModelReason.CHOSEN));
	}

	@Test
	void nothingChosenFallsBackToTheFirstModelOfTheFirstUsableProvider() {
		assertThat(ModelChoice.resolve(null, ALLOWED, BOTH, BOTH))
			.isEqualTo(new ModelResolution(null, ASTRA, ModelReason.NOT_CHOSEN));
		assertThat(ModelChoice.resolve(null, ALLOWED, BOTH, Set.of(Provider.ANTHROPIC)))
			.isEqualTo(new ModelResolution(null, FABLE, ModelReason.NOT_CHOSEN));
	}

	@Test
	void aModelDroppedFromTheListFallsBack() {
		ModelRef gone = new ModelRef(Provider.ANTHROPIC, "claude-gone");

		assertThat(ModelChoice.resolve(gone, ALLOWED, BOTH, BOTH))
			.isEqualTo(new ModelResolution(gone, ASTRA, ModelReason.NOT_ALLOWED));
	}

	@Test
	void aChosenProviderWithoutAKeyFallsBackToTheOther() {
		ModelRef sol = new ModelRef(Provider.OPENAI, "gpt-sol");

		assertThat(ModelChoice.resolve(sol, ALLOWED, Set.of(Provider.ANTHROPIC), Set.of(Provider.ANTHROPIC)))
			.isEqualTo(new ModelResolution(sol, FABLE, ModelReason.NO_KEY));
	}

	@Test
	void anInvalidKeyOfTheChosenProviderFallsBackToAValidOneOrToNothing() {
		ModelRef sol = new ModelRef(Provider.OPENAI, "gpt-sol");

		assertThat(ModelChoice.resolve(sol, ALLOWED, BOTH, Set.of(Provider.ANTHROPIC)))
			.isEqualTo(new ModelResolution(sol, FABLE, ModelReason.KEY_INVALID));
		assertThat(ModelChoice.resolve(sol, ALLOWED, BOTH, Set.of()))
			.isEqualTo(new ModelResolution(sol, null, ModelReason.KEY_INVALID));
	}

	@Test
	void theEarliestReasonWins() {
		ModelRef gone = new ModelRef(Provider.OPENAI, "gpt-gone");

		assertThat(ModelChoice.resolve(gone, ALLOWED, Set.of(), Set.of()))
			.isEqualTo(new ModelResolution(gone, null, ModelReason.NOT_ALLOWED));
	}

	@Test
	void aUsableProviderWithoutAllowedModelsIsSkipped() {
		assertThat(ModelChoice.resolve(null, List.of(FABLE), BOTH, BOTH))
			.isEqualTo(new ModelResolution(null, FABLE, ModelReason.NOT_CHOSEN));
		assertThat(ModelChoice.resolve(null, List.of(FABLE), BOTH, Set.of(Provider.OPENAI)))
			.isEqualTo(new ModelResolution(null, null, ModelReason.NOT_CHOSEN));
	}

}
