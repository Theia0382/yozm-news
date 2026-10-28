package net.yozmnews.source.domain.fetch;

import java.net.URI;
import java.util.Objects;

import net.yozmnews.source.domain.profile.CollectionProfile;

/**
 * 수집할 피드.
 *
 * @param url 피드 URL — 요청할 수 있는 주소인지는 수집이 판정한다({@link FetchUrls#isFetchable})
 * @param profile 그 피드를 읽는 수집 프로필
 */
public record FeedDescriptor(URI url, CollectionProfile profile) {

	public FeedDescriptor {
		Objects.requireNonNull(url, "url");
		Objects.requireNonNull(profile, "profile");
	}

}
