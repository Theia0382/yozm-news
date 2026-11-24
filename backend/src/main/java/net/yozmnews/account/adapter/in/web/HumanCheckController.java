package net.yozmnews.account.adapter.in.web;

import java.util.Objects;

import net.yozmnews.account.application.HumanCheck;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.WebRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 봇 차단 설정 조회 — docs/specs/account.md §3 · account-R33
 */
@WebRole
@RestController
class HumanCheckController {

	private final HumanCheck humanCheck;

	HumanCheckController(HumanCheck humanCheck) {
		this.humanCheck = Objects.requireNonNull(humanCheck, "humanCheck");
	}

	@PublicApi(reason = "로그인 · 복구 · 가입 화면이 봇 차단 위젯을 띄울지 정한다")
	@GetMapping("/api/human-check")
	HumanCheckResponse humanCheck() {
		return HumanCheckResponse.of(this.humanCheck.setting());
	}

}
