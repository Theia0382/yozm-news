package net.yozmnews;

import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.jdbc.autoconfigure.JdbcConnectionDetails;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	/** 시험 요청의 직접 연결 주소가 도는 칸 수 — 망 성능 시험용 예약 대역 198.18.0.0/15 의 주소 수 */
	private static final int TEST_ADDRESSES = 1 << 17;

	/**
	 * 테스트 DB — 컨텍스트마다 빈 데이터베이스 하나. 컨테이너는 테스트 JVM 이 함께 쓰고, 이미지는 개발용 Compose 의 정본을 쓴다
	 * (docs/development/infrastructure.md "테스트 DB" · "PostgreSQL 이미지 정본").
	 */
	@Bean
	JdbcConnectionDetails testDatabase() {
		return SharedPostgres.newDatabase();
	}

	/**
	 * MockMvc 요청마다 다른 직접 연결 주소 — 같은 주소의 반복 요청이 요청 제한(account-R30)에 걸리지 않게 한다. 망 성능 시험용 예약 대역
	 * 198.18.0.0/15 에서 차례로 고르고(사설 대역 · 루프백과 겹치지 않는다), 요청 제한을 보는 시험은 요청에서 주소 · 헤더를 직접 정해 덮어쓴다.
	 */
	@Bean
	MockMvcBuilderCustomizer distinctClientAddresses() {
		AtomicInteger next = new AtomicInteger();
		return builder -> builder.defaultRequest(MockMvcRequestBuilders.get("/").with(request -> {
			int n = Math.floorMod(next.getAndIncrement(), TEST_ADDRESSES);
			request.setRemoteAddr("198." + (18 + (n >>> 16)) + "." + ((n >>> 8) & 0xFF) + "." + (n & 0xFF));
			return request;
		}));
	}

}
