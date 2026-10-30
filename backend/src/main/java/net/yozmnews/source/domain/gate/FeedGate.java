package net.yozmnews.source.domain.gate;

import java.net.URI;
import java.util.List;
import java.util.Objects;

import net.yozmnews.source.SourceId;
import net.yozmnews.source.domain.fetch.FeedDescriptor;
import net.yozmnews.source.domain.fetch.FetchFailure;
import net.yozmnews.source.domain.fetch.FetchFailureReason;
import net.yozmnews.source.domain.fetch.FetchResult;
import net.yozmnews.source.domain.profile.CollectionProfile;
import net.yozmnews.source.domain.registry.Activation;
import net.yozmnews.source.domain.registry.Feed;
import net.yozmnews.source.domain.registry.PollTarget;
import net.yozmnews.source.domain.registry.ProfileResolution;

/**
 * 피드 켜기 관문의 판정 — docs/specs/source.md §4 · source-R5 · source-R9.
 * <p>
 * 요청 전에는 설정 판정({@link PollTarget#of})만 하고, 받은 결과는 {@link ActivationCriteria} 에 견주며, 저장 직전에 다시 얻은 프로필로
 * 설정 판정을 되풀이한다. 피드를 켜는 전이는 {@link Feed#enable} 이 한다.
 */
public final class FeedGate {

	/** 조건부 요청 값 없이 보낸 요청에 온 304 — 수집 결과의 분류와 같은 이유로 본다 */
	private static final int NOT_MODIFIED = 304;

	private FeedGate() {
	}

	/** 요청 전에 판정한다 — 설정 판정에서 거절하거나 이미 켜져 있으면 요청하지 않는다 */
	public static Precheck precheck(Feed feed, ProfileResolution resolution) {
		return switch (PollTarget.of(resolution, feed.feedKey())) {
			case PollTarget.Unavailable unavailable ->
				new Precheck.Decided(new FeedActivation.Refused(unavailable.reason()));
			case PollTarget.Ready ready -> feed.enabled() ? new Precheck.Decided(new FeedActivation.Activated(null))
					: new Precheck.Fetch(feed.sourceId(), ready.descriptor());
		};
	}

	/** 받은 결과를 미리보기와 판정으로 옮긴다 */
	public static PreviewOutcome preview(FetchResult result, CollectionProfile profile) {
		return switch (result) {
			case FetchResult.Fetched fetched -> {
				FeedPreview preview = FeedPreview.of(fetched, profile);
				List<Shortfall> shortfalls = ActivationCriteria.judge(preview);
				yield shortfalls.isEmpty() ? new PreviewOutcome.Passed(preview)
						: new PreviewOutcome.BelowCriteria(preview, shortfalls);
			}
			case FetchResult.NotModified notModified -> new PreviewOutcome.Unfetched(
					FetchFailure.status(FetchFailureReason.UNEXPECTED_STATUS, NOT_MODIFIED));
			case FetchResult.Failed failed -> new PreviewOutcome.Unfetched(failed.failure());
		};
	}

	/**
	 * 저장 직전에 판정한다. 통과한 미리보기이고, 다시 얻은 프로필에 피드가 있고, 그 URL 이 미리본 URL 과 같을 때만 피드를 켠다.
	 * @param current 저장 직전에 다시 얻은 프로필
	 * @param previewed 미리본 피드 URL
	 */
	public static FeedActivation enable(Feed feed, ProfileResolution current, URI previewed, PreviewOutcome outcome) {
		Objects.requireNonNull(previewed, "previewed");
		return switch (outcome) {
			case PreviewOutcome.BelowCriteria below -> new FeedActivation.BelowCriteria(below.preview(),
					below.shortfalls());
			case PreviewOutcome.Unfetched unfetched -> new FeedActivation.Unfetched(unfetched.failure());
			case PreviewOutcome.Passed passed -> switch (PollTarget.of(current, feed.feedKey())) {
				case PollTarget.Unavailable unavailable -> new FeedActivation.Refused(unavailable.reason());
				case PollTarget.Ready ready -> ready.feed().url().equals(previewed)
						? activate(feed, current, passed.preview())
						: new FeedActivation.TargetChanged(previewed, ready.feed().url());
			};
		};
	}

	private static FeedActivation activate(Feed feed, ProfileResolution current, FeedPreview preview) {
		return switch (feed.enable(current)) {
			case Activation.Activated activated -> new FeedActivation.Activated(preview);
			case Activation.Refused refused -> new FeedActivation.Refused(refused.reason());
		};
	}

	/** 요청 전 판정의 결과 */
	public sealed interface Precheck {

		/** 요청 없이 결과가 정해졌다 */
		record Decided(FeedActivation activation) implements Precheck {

			public Decided {
				Objects.requireNonNull(activation, "activation");
			}

		}

		/** 꺼진 피드라 받아 봐야 한다 */
		record Fetch(SourceId source, FeedDescriptor descriptor) implements Precheck {

			public Fetch {
				Objects.requireNonNull(source, "source");
				Objects.requireNonNull(descriptor, "descriptor");
			}

		}

	}

}
