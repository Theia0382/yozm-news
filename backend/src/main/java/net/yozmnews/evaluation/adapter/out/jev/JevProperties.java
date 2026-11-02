package net.yozmnews.evaluation.adapter.out.jev;

import java.net.URI;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Jev 설정 — docs/specs/evaluation.md §7 · docs/operations/configuration.md.
 *
 * @param apiKey API 키 — 비밀 값, 없거나 비면 Jev 를 결선하지 않는다
 * @param model 모델 이름 또는 별칭
 * @param baseUrl 기본 주소 — System One 경로를 그 뒤에 붙인다
 */
@ConfigurationProperties("yozmnews.jev")
record JevProperties(@Nullable String apiKey, @Nullable String model, @Nullable URI baseUrl) {

	@Override
	public String toString() {
		return "JevProperties[apiKey=" + ((this.apiKey == null || this.apiKey.isEmpty()) ? "없음" : "숨김") + ", model="
				+ this.model + ", baseUrl=" + this.baseUrl + "]";
	}

}
