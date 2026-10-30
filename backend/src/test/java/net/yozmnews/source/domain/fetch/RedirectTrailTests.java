package net.yozmnews.source.domain.fetch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.net.URI;

import org.junit.jupiter.api.Test;

/**
 * 영구 이동 추적과 홉 상한.
 * <p>
 * 지키는 규칙: source-R27 · source-R28
 */
class RedirectTrailTests {

	private static final URI FIRST = URI.create("https://a.example/1");

	private static final URI SECOND = URI.create("https://a.example/2");

	private static final URI THIRD = URI.create("https://a.example/3");

	@Test
	void permanentChainEndsAtItsLastTarget() {
		RedirectTrail trail = new RedirectTrail(5);

		trail.follow(true, FIRST);
		trail.follow(true, SECOND);

		assertThat(trail.movedTo()).isEqualTo(SECOND);
	}

	@Test
	void temporaryHopStopsThePermanentChain() {
		RedirectTrail trail = new RedirectTrail(5);

		trail.follow(true, FIRST);
		trail.follow(false, SECOND);
		trail.follow(true, THIRD);

		assertThat(trail.movedTo()).isEqualTo(FIRST);
	}

	@Test
	void permanentAfterTemporaryIsNotAMove() {
		RedirectTrail trail = new RedirectTrail(5);

		trail.follow(false, FIRST);
		trail.follow(true, SECOND);

		assertThat(trail.movedTo()).isNull();
	}

	@Test
	void noHopIsNoMove() {
		assertThat(new RedirectTrail(5).movedTo()).isNull();
	}

	@Test
	void refusesTheHopBeyondTheLimit() {
		RedirectTrail trail = new RedirectTrail(5);
		for (int i = 1; i <= 4; i++) {
			assertThat(trail.follow(false, FIRST)).isTrue();
		}

		assertThat(trail.follow(true, SECOND)).isTrue();
		assertThat(trail.follow(true, THIRD)).isFalse();
		assertThat(trail.movedTo()).isNull();
	}

	@Test
	void zeroLimitRefusesTheFirstHop() {
		assertThat(new RedirectTrail(0).follow(true, FIRST)).isFalse();
	}

	@Test
	void rejectsANegativeLimit() {
		assertThatIllegalArgumentException().isThrownBy(() -> new RedirectTrail(-1));
	}

}
