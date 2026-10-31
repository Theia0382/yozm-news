package net.yozmnews;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import net.yozmnews.platform.adapter.in.job.EventResubmissionJob;
import net.yozmnews.source.adapter.in.job.FeedHealthCheckJob;
import net.yozmnews.source.adapter.in.job.PollDueFeedsJob;
import net.yozmnews.source.adapter.in.job.PollFeedJob;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.config.ScheduledTask;
import org.springframework.scheduling.config.ScheduledTaskHolder;

/**
 * 역할 조합 테스트가 함께 쓰는 관측 — 띄운 컨텍스트 · DB · HTTP 에서 역할에 걸리는 구성요소를 읽는다
 */
final class RoleProbe {

	/** 작업 진입점 — 작업 역할에서만 빈이다 */
	static final List<Class<?>> JOB_ENTRY_POINTS = List.of(PollDueFeedsJob.class, PollFeedJob.class,
			FeedHealthCheckJob.class, EventResubmissionJob.class);

	/** Spring Modulith 멈춤 감시 작업의 설명 접두 — 작업은 이 구성의 람다다 */
	private static final String STALENESS_MONITOR =
			"org.springframework.modulith.events.config.StalenessMonitorConfiguration";

	private static final HttpClient HTTP = HttpClient.newHttpClient();

	private RoleProbe() {
	}

	/** 작업 진입점 타입마다 빈 개수 */
	static Map<Class<?>, Integer> jobEntryPointBeans(ApplicationContext context) {
		return JOB_ENTRY_POINTS.stream()
			.collect(Collectors.toMap(type -> type, type -> context.getBeanNamesForType(type).length));
	}

	/**
	 * 저장소에 등록된 반복 작업 ID — 컨텍스트마다 DB 컨테이너가 따로라 이 프로세스가 등록한 것이다. 열이 고정 길이 문자열이라 뒤
	 * 공백을 지운다
	 */
	static Set<String> recurringJobIds(JdbcClient jdbc) {
		return Set.copyOf(jdbc.sql("select rtrim(id) from jobrunr_recurring_jobs").query(String.class).list());
	}

	/** 스케줄링에 등록된 작업 전부 */
	static List<ScheduledTask> scheduledTasks(ApplicationContext context) {
		return context.getBeansOfType(ScheduledTaskHolder.class)
			.values()
			.stream()
			.flatMap(holder -> holder.getScheduledTasks().stream())
			.toList();
	}

	/** 스케줄링에 등록된 멈춤 감시 작업 — 작업의 실행 대상은 감싸여 있어 설명으로 가려낸다 */
	static List<ScheduledTask> stalenessMonitorTasks(ApplicationContext context) {
		return scheduledTasks(context).stream().filter(task -> task.toString().startsWith(STALENESS_MONITOR)).toList();
	}

	static HttpResponse<String> get(int port, String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
		return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
