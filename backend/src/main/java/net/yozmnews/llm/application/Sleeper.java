package net.yozmnews.llm.application;

import java.time.Duration;

/**
 * 생성 호출을 다시 보내기 전에 기다린다 — 운영은 스레드를 재우고, 시험은 기다린 시간을 적는다 · docs/specs/llm.md llm-R20
 */
@FunctionalInterface
interface Sleeper {

	/**
	 * @throws InterruptedException 기다리다 중단됐을 때
	 */
	void sleep(Duration duration) throws InterruptedException;

}
