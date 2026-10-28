package net.yozmnews.source.adapter.out.rss;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 피드 수집 설정({@code yozmnews.collection}).
 *
 * @param contact 수집 요청의 {@code User-Agent} 에 밝히는 운영자 연락처(이메일 주소 · http · https URL) — 필수. 없으면 앱이
 * 뜨지 않는다
 * @param allowedNetworks 차단 대역 가운데 수집이 연결해도 되는 대역(CIDR) — 같은 망의 피드를 받는 셀프 호스팅용. 없으면
 * 내부 주소를 모두 막는다
 */
@ConfigurationProperties("yozmnews.collection")
public record CollectionProperties(@Nullable String contact, @Nullable List<String> allowedNetworks) {
}
