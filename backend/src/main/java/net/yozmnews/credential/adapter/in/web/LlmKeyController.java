package net.yozmnews.credential.adapter.in.web;

import java.util.Objects;

import jakarta.validation.Valid;
import net.yozmnews.credential.application.KeyOverview;
import net.yozmnews.credential.application.KeyRecheck;
import net.yozmnews.credential.application.KeyRegistration;
import net.yozmnews.credential.application.KeyRemoval;
import net.yozmnews.llm.Provider;
import net.yozmnews.platform.AllowedWithoutLlmKey;
import net.yozmnews.platform.SecretValue;
import net.yozmnews.platform.UserId;
import net.yozmnews.platform.WebRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 세션 사용자의 LLM 키 — 목록, 등록 · 교체, 다시 검증, 삭제. docs/specs/credential.md §3 · credential-R10 · credential-R12 ·
 * credential-R16 · credential-R17 · credential-R19
 * <p>
 * 요청의 키는 앞뒤 공백을 지워 곧바로 {@link SecretValue} 로 감싼다. 응답에 키를 싣지 않는다.
 */
@WebRole
@RestController
@AllowedWithoutLlmKey(reason = "키를 등록 · 확인 · 삭제하는 길이다")
class LlmKeyController {

	private final KeyOverview overview;

	private final KeyRegistration registration;

	private final KeyRecheck recheck;

	private final KeyRemoval removal;

	LlmKeyController(KeyOverview overview, KeyRegistration registration, KeyRecheck recheck, KeyRemoval removal) {
		this.overview = Objects.requireNonNull(overview, "overview");
		this.registration = Objects.requireNonNull(registration, "registration");
		this.recheck = Objects.requireNonNull(recheck, "recheck");
		this.removal = Objects.requireNonNull(removal, "removal");
	}

	@GetMapping("/api/llm-keys")
	LlmKeysResponse keys(UserId user) {
		return new LlmKeysResponse(this.overview.of(user).stream().map(ProviderKeyResponse::of).toList());
	}

	@PutMapping("/api/llm-keys/{provider}")
	KeyRegistrationResponse register(UserId user, @PathVariable Provider provider,
			@Valid @RequestBody KeyRegistrationRequest body) {
		return KeyRegistrationResponse
			.of(this.registration.register(user, provider, SecretValue.of(body.key().strip())));
	}

	@PostMapping("/api/llm-keys/{provider}/checks")
	KeyCheckResponse check(UserId user, @PathVariable Provider provider) {
		return KeyCheckResponse.of(this.recheck.recheck(user, provider));
	}

	@DeleteMapping("/api/llm-keys/{provider}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void remove(UserId user, @PathVariable Provider provider) {
		this.removal.remove(user, provider);
	}

}
