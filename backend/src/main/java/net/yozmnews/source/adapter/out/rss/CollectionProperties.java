package net.yozmnews.source.adapter.out.rss;

import java.util.List;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 피드 수집 설정({@code yozmnews.collection}) — docs/specs/source.md §7 · source-R29 · source-R34
 */
@ConfigurationProperties("yozmnews.collection")
public record CollectionProperties(@Nullable String contact, @Nullable List<String> allowedNetworks) {
}
