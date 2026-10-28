package net.yozmnews.source.adapter.out.rss;

import java.io.IOException;

/**
 * 응답 본문이 크기 상한을 넘었다.
 */
final class BodyTooLargeException extends IOException {

	private static final long serialVersionUID = 1L;

	BodyTooLargeException(long max) {
		super("본문이 " + max + "바이트를 넘는다");
	}

}
