package net.yozmnews.source.application;

import java.util.Optional;

import net.yozmnews.platform.AppClock;
import net.yozmnews.source.domain.fetch.ArticleSource;
import net.yozmnews.source.domain.fetch.FeedDescriptor;
import net.yozmnews.source.domain.fetch.FetchState;
import net.yozmnews.source.domain.gate.FeedGate;
import net.yozmnews.source.domain.gate.ProfilePreview;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.profile.FeedKey;
import net.yozmnews.source.domain.profile.FeedSpec;
import net.yozmnews.source.domain.profile.ProfileParse;
import net.yozmnews.source.domain.profile.ProfileParser;
import org.springframework.stereotype.Service;

/**
 * 등록 전 운영자 프로필 원문의 미리보기 — docs/specs/source.md §4 · source-R9 · source-R58.
 * <p>
 * DB 를 읽지도 쓰지도 않는다. 소스 행이 없어 같은 소스의 요청 시작 시각을 예약하지 않는다.
 */
@Service
class ProfilePreviewer {

	private final ProfileParser parser;

	private final ArticleSource articleSource;

	private final AppClock clock;

	ProfilePreviewer(ProfileParser parser, ArticleSource articleSource, AppClock clock) {
		this.parser = parser;
		this.articleSource = articleSource;
		this.clock = clock;
	}

	/**
	 * 원문의 피드 하나를 한 번 받아 판정한다 — 원문이 검증에 걸리거나 피드 식별자가 없으면 받지 않는다.
	 * @throws net.yozmnews.platform.RealTimeRequiredException 받을 피드가 있는데 앱의 시각이 실제 시간이 아닐 때(측정용 시계)
	 */
	ProfilePreview preview(String text, FeedKey key) {
		return switch (this.parser.parseOperatorProfile(text)) {
			case ProfileParse.Invalid invalid -> new ProfilePreview.Invalid(invalid.problems());
			case ProfileParse.Parsed parsed -> preview(parsed.profile(), key);
		};
	}

	private ProfilePreview preview(CollectionProfile profile, FeedKey key) {
		Optional<FeedSpec> feed = profile.feed(key);
		if (feed.isEmpty()) {
			return new ProfilePreview.FeedNotInProfile(key);
		}
		this.clock.requireRealTime();
		return new ProfilePreview.Previewed(FeedGate.preview(
				this.articleSource.fetch(new FeedDescriptor(feed.get().url(), profile), FetchState.NONE), profile));
	}

}
