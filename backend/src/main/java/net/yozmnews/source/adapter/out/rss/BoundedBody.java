package net.yozmnews.source.adapter.out.rss;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 본문을 상한까지만 읽는다 — 상한을 넘는 바이트를 읽는 순간 멈춘다.
 */
final class BoundedBody {

	private static final int BUFFER_SIZE = 8_192;

	private BoundedBody() {
	}

	/**
	 * @param max 상한 — 같으면 통과
	 * @throws BodyTooLargeException 읽은 바이트가 상한을 넘는 순간
	 */
	static byte[] read(InputStream in, long max) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		byte[] buffer = new byte[BUFFER_SIZE];
		long total = 0;
		int read;
		while ((read = in.read(buffer)) != -1) {
			total += read;
			if (total > max) {
				throw new BodyTooLargeException(max);
			}
			out.write(buffer, 0, read);
		}
		return out.toByteArray();
	}

}
