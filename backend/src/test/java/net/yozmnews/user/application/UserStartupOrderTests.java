package net.yozmnews.user.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.InstantSource;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.yozmnews.account.AccountDirectory;
import net.yozmnews.user.domain.interest.InterestSettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기동 단계 — 기존 계정 채우기는 모든 {@code SmartInitializingSingleton}(태그 체계 첫 기동 적재의 대역) 뒤에 돈다. 대역 적재 빈을
 * 기동 단계 빈보다 뒤에 등록해도 순서가 같다 — 순서를 기록해 본다. 단계는 웹 서버 시작 단계보다 작다.
 * <p>
 * 지키는 규칙: user-R44
 */
class UserStartupOrderTests {

	/** Spring Boot 4.1 의 서블릿 웹 서버 시작 단계({@code Integer.MAX_VALUE - 2048}) */
	private static final int WEB_SERVER_PHASE = 2147481599;

	@Test
	void startupWorkRunsAfterEverySmartInitializingSingletonEvenWhenRegisteredFirst() {
		List<String> order = new CopyOnWriteArrayList<>();
		AccountDirectory accounts = mock(AccountDirectory.class);
		when(accounts.page(anyLong(), anyInt())).thenAnswer(invocation -> {
			order.add("backfill");
			return List.of();
		});

		new ApplicationContextRunner()
			.withBean(InterestBackfill.class,
					() -> new InterestBackfill(accounts, mock(InterestSettingsRepository.class),
							mock(InterestDefaultsWriter.class), mock(TransactionTemplate.class),
							InstantSource.system()))
			.withBean("seeding", SmartInitializingSingleton.class, () -> () -> order.add("seeding"))
			.run(context -> {
				assertThat(context).hasNotFailed();
				assertThat(order).first().isEqualTo("seeding");
				assertThat(order).containsExactly("seeding", "backfill");
			});
	}

	@Test
	void theStartupPhaseComesBeforeTheWebServer() {
		assertThat(UserStartup.PHASE).isLessThan(WEB_SERVER_PHASE);
	}

}
