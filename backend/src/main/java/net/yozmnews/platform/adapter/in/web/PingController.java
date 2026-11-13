package net.yozmnews.platform.adapter.in.web;

import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.WebRole;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 계약 확인 엔드포인트 — docs/specs/platform.md §4 · platform-R7
 */
@WebRole
@RestController
@PublicApi(reason = "배포 확인 — 사용자 데이터를 담지 않는다")
class PingController {

	@GetMapping("/api/ping")
	PingResponse ping() {
		return new PingResponse("pong");
	}

}
