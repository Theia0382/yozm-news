package net.yozmnews;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 시험 중계 — 루프백의 대상 포트로 TCP 연결을 잇고, 이미 맺은 연결을 신호 없이 멈출 수 있다.
 * <p>
 * {@link #stallExisting()} 뒤 그때까지 맺은 연결은 양방향 전달을 멈춘다. 소켓은 연 채라 상대는 끊김(FIN · RST)을 받지 않는다 — 네트워크 경로가
 * 바뀌어 상대의 신호 없이 죽은 연결과 부르는 쪽에서 같아 보인다. 그 뒤 새로 맺는 연결은 그대로 잇는다. {@link #close()} 가 소켓과 전달 스레드를
 * 거둔다.
 */
public final class StallingRelay implements AutoCloseable {

	private final ServerSocket server;

	private final int target;

	private final List<Socket> sockets = new CopyOnWriteArrayList<>();

	private final List<AtomicBoolean> stalls = new CopyOnWriteArrayList<>();

	private final AtomicInteger accepted = new AtomicInteger();

	private final CountDownLatch closed = new CountDownLatch(1);

	/**
	 * @param target 루프백에서 잇는 대상 포트
	 */
	public StallingRelay(int target) throws IOException {
		this.target = target;
		this.server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
		Thread acceptor = new Thread(this::accept, "stalling-relay-accept");
		acceptor.setDaemon(true);
		acceptor.start();
	}

	/** 중계가 받는 포트 */
	public int port() {
		return this.server.getLocalPort();
	}

	/** 지금까지 받은 연결 수 */
	public int acceptedConnections() {
		return this.accepted.get();
	}

	/** 지금까지 맺은 연결의 전달을 멈춘다 — 그 뒤 새 연결은 그대로 잇는다 */
	public void stallExisting() {
		this.stalls.forEach(stall -> stall.set(true));
	}

	@Override
	public void close() throws IOException {
		this.closed.countDown();
		this.server.close();
		for (Socket socket : this.sockets) {
			socket.close();
		}
	}

	private void accept() {
		while (!this.server.isClosed()) {
			try {
				Socket client = this.server.accept();
				Socket upstream = new Socket(InetAddress.getLoopbackAddress(), this.target);
				this.sockets.add(client);
				this.sockets.add(upstream);
				AtomicBoolean stall = new AtomicBoolean();
				this.stalls.add(stall);
				this.accepted.incrementAndGet();
				pump(client, upstream, stall);
				pump(upstream, client, stall);
			}
			catch (IOException ex) {
				// 닫힌 뒤의 accept 실패 — 끝낸다
			}
		}
	}

	private void pump(Socket from, Socket to, AtomicBoolean stall) {
		Thread thread = new Thread(() -> {
			byte[] buffer = new byte[8192];
			try {
				InputStream in = from.getInputStream();
				OutputStream out = to.getOutputStream();
				int read;
				while ((read = in.read(buffer)) >= 0) {
					if (stall.get()) {
						// 받은 것을 넘기지 않고 닫힐 때까지 쥔다 — 상대에게 끊김을 알리지 않는다
						this.closed.await();
						return;
					}
					out.write(buffer, 0, read);
					out.flush();
				}
			}
			catch (IOException ex) {
				// 소켓이 닫혔다 — 끝낸다
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}, "stalling-relay-pump");
		thread.setDaemon(true);
		thread.start();
	}

}
