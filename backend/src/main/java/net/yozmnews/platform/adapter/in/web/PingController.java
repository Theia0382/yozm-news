package net.yozmnews.platform.adapter.in.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 계약 파이프라인(명세 → 프론트엔드 생성 타입)을 확인하는 엔드포인트. 화면에는 쓰지 않고, 사용자 데이터를 담지 않는다.
 */
@RestController
class PingController {

	@GetMapping("/api/ping")
	PingResponse ping() {
		return new PingResponse("pong");
	}

}
