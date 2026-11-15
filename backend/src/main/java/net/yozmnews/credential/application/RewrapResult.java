package net.yozmnews.credential.application;

/**
 * 재암호화 한 회차의 결과.
 *
 * @param rewrapped DEK 를 다시 감싼 키의 수
 * @param failed 다시 감싸지 못한 키의 수 — 다음 회차가 다시 잡는다
 * @param remaining 회차 뒤에도 활성 버전이 아닌 키의 수
 */
public record RewrapResult(int rewrapped, int failed, long remaining) {
}
