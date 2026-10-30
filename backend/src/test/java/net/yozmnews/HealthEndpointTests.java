package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.CompositeHealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthDescriptor;
import org.springframework.boot.health.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.health.contributor.Status;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;

/**
 * HTTP 로 노출하는 관리 엔드포인트 — 실제 PostgreSQL 로 띄운 앱에 HTTP 로 묻는다
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthEndpointTests {

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@LocalServerPort
	int port;

	@Autowired
	HealthEndpoint healthEndpoint;

	@Test
	void healthRespondsWithStatusOnly() throws Exception {
		HttpResponse<String> response = get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		JSONAssert.assertEquals("""
				{"groups":["liveness","readiness"],"status":"UP"}
				""", response.body(), JSONCompareMode.STRICT);
	}

	@Test
	void healthIncludesDatabaseAndIsUp() {
		HealthDescriptor health = this.healthEndpoint.health();

		assertThat(health).isInstanceOf(CompositeHealthDescriptor.class);
		assertThat(health.getStatus()).isEqualTo(Status.UP);
		Map<String, HealthDescriptor> components = ((CompositeHealthDescriptor) health).getComponents();
		assertThat(components).containsKey("db");
		assertThat(components.get("db").getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void onlyHealthIsExposedOverHttp() throws Exception {
		for (String path : new String[] { "/actuator/env", "/actuator/configprops", "/actuator/beans",
				"/actuator/info", "/actuator/modulith", "/actuator/health/db" }) {
			assertThat(get(path).statusCode()).as(path).isEqualTo(404);
		}

		HttpResponse<String> discovery = get("/actuator");
		assertThat(discovery.statusCode()).isEqualTo(200);
		Map<String, Object> links = JsonPath.read(discovery.body(), "$._links");
		assertThat(links.keySet()).containsExactlyInAnyOrder("self", "health", "health-path");
	}

	private HttpResponse<String> get(String path) throws Exception {
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + this.port + path)).build();
		return this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
