package net.yozmnews.source.domain.registry;

import java.util.Objects;

/**
 * 켜기의 결과.
 */
public sealed interface Activation {

	/** 켰다 */
	record Activated() implements Activation {
	}

	/** 켜지 않았다 — 상태는 그대로다 */
	record Refused(ActivationRefusal reason) implements Activation {

		public Refused {
			Objects.requireNonNull(reason, "reason");
		}

	}

}
