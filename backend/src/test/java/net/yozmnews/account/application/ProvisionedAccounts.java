package net.yozmnews.account.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

import net.yozmnews.account.domain.account.DisplayName;
import net.yozmnews.account.domain.account.EmailAddress;
import net.yozmnews.account.domain.account.Onboarding;
import net.yozmnews.account.domain.credential.PasswordPolicy;
import net.yozmnews.platform.UserId;
import org.springframework.context.ApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 시험 도우미 — 계정을 만드는 정본({@link AccountProvisioning})으로 계정을 만든다. 다른 패키지의 시험(관심 설정 · 알림 등)이 계정 쪽의
 * 비공개 구성 요소에 닿지 않고 같은 길로 계정을 만들게 한다. 계정 생김 이벤트의 처리를 기다리는 것은 부르는 쪽의 몫이다.
 */
public final class ProvisionedAccounts {

	private ProvisionedAccounts() {
	}

	/**
	 * 계정을 하나 만든다 — 계정을 만드는 트랜잭션 하나.
	 * @param password 정규화 전 비밀번호 — 규칙을 지켜야 한다
	 * @return 만든 계정 — 같은 이메일의 계정이 있으면 빈 값
	 */
	public static Optional<UserId> provision(ApplicationContext context, Onboarding onboarding, String email,
			String displayName, String password, Instant now) {
		AccountProvisioning provisioning = context.getBean(AccountProvisioning.class);
		TransactionTemplate transaction = context.getBean(TransactionTemplate.class);
		ProvisionResult result = Objects.requireNonNull(transaction.execute(status -> provisioning.provision(onboarding,
				EmailAddress.parse(email).orElseThrow(), new DisplayName(displayName), PasswordPolicy.normalize(password),
				now)));
		return switch (result) {
			case ProvisionResult.Created created -> Optional.of(created.user());
			case ProvisionResult.EmailTaken taken -> Optional.empty();
		};
	}

}
