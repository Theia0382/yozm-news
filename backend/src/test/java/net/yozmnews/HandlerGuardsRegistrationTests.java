package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import net.yozmnews.account.domain.guard.LimitAxis;
import net.yozmnews.account.domain.guard.RequestLimits;
import net.yozmnews.platform.GuardedAction;
import net.yozmnews.platform.HandlerGuards;
import net.yozmnews.platform.PublicApis;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 요청 제한 · 봇 차단 표시의 등록 — 앱의 처리기를 열거해, 표시가 있는 처리기가 손으로 적은 연산(요청 제한 여덟 · 봇 차단 셋)과 그
 * 동작인지, 봇 차단 처리기가 같은 동작의 요청 제한을 갖는지, 사용자로 세는 동작의 표시가 세션 없는 공개 처리기에 달리지 않았는지 본다(달리면
 * 요청 제한이 그 요청에서 실패한다).
 * <p>
 * 지키는 규칙: platform-R25 · user-R38 · user-R41
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class HandlerGuardsRegistrationTests {

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mapping;

	@Test
	void exactlyTheEightGuardedOperationsCarryTheirActions() {
		Map<String, String> limited = new TreeMap<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : this.mapping.getHandlerMethods().entrySet()) {
			HandlerGuards.requestLimited(entry.getValue())
				.ifPresent(action -> limited.put(operation(entry.getKey()), action.name()));
		}

		assertThat(limited).containsExactlyInAnyOrderEntriesOf(Map.of("POST /api/session", "SIGN_IN",
				"POST /api/account-recoveries", "ACCOUNT_RECOVERY", "POST /api/invitation-checks", "INVITATION_CHECK",
				"POST /api/invitation-acceptances", "INVITATION_ACCEPTANCE", "POST /api/sign-ups", "SIGN_UP",
				"PUT /api/llm-keys/{provider}", "LLM_KEY_CHECK", "POST /api/llm-keys/{provider}/checks",
				"LLM_KEY_CHECK", "PUT /api/feedback/{eventId}", "FEEDBACK"));
	}

	@Test
	void actionsCountedByUserAreNeverOnPublicHandlers() {
		Map<String, GuardedAction> publicLimited = new TreeMap<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : this.mapping.getHandlerMethods().entrySet()) {
			if (PublicApis.isPublic(entry.getValue())) {
				HandlerGuards.requestLimited(entry.getValue())
					.ifPresent(action -> publicLimited.put(operation(entry.getKey()), action));
			}
		}
		List<String> misplaced = publicLimited.entrySet()
			.stream()
			.filter(entry -> countsByUser(entry.getValue()))
			.map(Map.Entry::getKey)
			.toList();

		// 하한 — 표시가 있는 공개 처리기 다섯을 실제로 훑었다(빈 열거가 통과하지 못한다)
		assertThat(publicLimited.keySet()).containsExactlyInAnyOrder("POST /api/session", "POST /api/account-recoveries",
				"POST /api/invitation-checks", "POST /api/invitation-acceptances", "POST /api/sign-ups");
		assertThat(misplaced).isEmpty();
		// 대조 — 사용자로 세는 동작이 실제로 있다(판정이 언제나 거짓이 아니다)
		assertThat(countsByUser(GuardedAction.LLM_KEY_CHECK)).isTrue();
	}

	@Test
	void exactlyTheThreeBotCheckedOperationsCarryTheSameActionAsTheirLimit() {
		Map<String, String> checked = new TreeMap<>();
		Map<String, String> limitOfChecked = new TreeMap<>();
		for (Map.Entry<RequestMappingInfo, HandlerMethod> entry : this.mapping.getHandlerMethods().entrySet()) {
			HandlerGuards.humanChecked(entry.getValue()).ifPresent(action -> {
				checked.put(operation(entry.getKey()), action.name());
				limitOfChecked.put(operation(entry.getKey()),
						HandlerGuards.requestLimited(entry.getValue()).map(GuardedAction::name).orElse("없음"));
			});
		}

		assertThat(checked).containsExactlyInAnyOrderEntriesOf(Map.of("POST /api/session", "SIGN_IN",
				"POST /api/account-recoveries", "ACCOUNT_RECOVERY", "POST /api/sign-ups", "SIGN_UP"));
		// 봇 차단 처리기는 같은 동작의 요청 제한을 가진다 — 검증(외부 요청) 앞에서 센다
		assertThat(limitOfChecked).isEqualTo(checked);
	}

	private static boolean countsByUser(GuardedAction action) {
		return RequestLimits.rulesOf(action).stream().anyMatch(rule -> rule.axis() == LimitAxis.SESSION_USER);
	}

	private static String operation(RequestMappingInfo info) {
		return info.getMethodsCondition().getMethods().iterator().next() + " "
				+ info.getPatternValues().iterator().next();
	}

}
