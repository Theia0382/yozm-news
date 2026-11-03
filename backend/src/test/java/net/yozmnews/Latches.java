package net.yozmnews;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** 시험 도우미 — 동시성 시험의 래치 기다리기 */
public final class Latches {

	private Latches() {
	}

	/** 래치를 30초까지 기다린다 — 끼어들면 인터럽트 표시만 남긴다 */
	public static void awaitQuietly(CountDownLatch latch) {
		try {
			latch.await(30, TimeUnit.SECONDS);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
