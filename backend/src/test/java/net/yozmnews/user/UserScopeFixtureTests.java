package net.yozmnews.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import net.yozmnews.TestcontainersConfiguration;
import net.yozmnews.UserScopeRules;
import net.yozmnews.UserScopedEndpoints;
import net.yozmnews.YozmnewsApplication;
import net.yozmnews.platform.AllowedBeforePasswordChange;
import net.yozmnews.platform.PublicApi;
import net.yozmnews.platform.UserId;
import net.yozmnews.user.application.PasswordHashing;
import net.yozmnews.user.domain.account.AccountRepository;
import net.yozmnews.user.domain.credential.PasswordCredentialRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 격리 틀 자체의 시험 — 세션 사용자 인자로 사용자별 데이터를 가르는 시험 처리기에서 A 가 쓴 것을 B 가 읽지 못하고, 등록 가드의 판정이 일부러 틀린
 * 처리기(공개도 등록도 아님 · 경로 · 질의에서 userId · 본문 · 질의 레코드의 UserId · 묶기 애너테이션을 단 UserId · 빈 공개 이유 · 빈
 * 비밀번호 변경 전 허용 이유 · 공개 처리기의 허용 표시 · 낡은 등록)를 모두 잡는다.
 * <p>
 * 지키는 규칙: user-R5 · user-R6
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(classes = { YozmnewsApplication.class, UserScopeFixtureTests.NotesController.class,
		UserScopeFixtureTests.CarelessController.class })
@AutoConfigureMockMvc
class UserScopeFixtureTests {

	/** 이 시험 컨텍스트의 등록 — 앱의 등록에 사용자별 메모를 더하고, 낡은 등록 하나를 일부러 둔다 */
	private static final Map<String, Class<?>> REGISTRY = registry();

	@Autowired
	MockMvc mockMvc;

	@Autowired
	AccountRepository accounts;

	@Autowired
	PasswordCredentialRepository credentials;

	@Autowired
	PasswordHashing hashing;

	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	RequestMappingHandlerMapping mapping;

	@Test
	void userACannotReadUserBsData() throws Exception {
		SignedInUsers users = new SignedInUsers(this.mockMvc, this.accounts, this.credentials, this.hashing);
		SignedInUsers.SignedInUser a = users.signIn("isolation-a@example.com");
		SignedInUsers.SignedInUser b = users.signIn("isolation-b@example.com");

		this.mockMvc
			.perform(post("/api/test/notes").with(a).contentType(MediaType.APPLICATION_JSON).content("""
					{"text":"a 의 메모"}"""))
			.andExpect(status().isOk());

		this.mockMvc.perform(get("/api/test/notes").with(b)).andExpect(status().isOk()).andExpect(content().json("[]"));
		this.mockMvc.perform(get("/api/test/notes").with(a))
			.andExpect(status().isOk())
			.andExpect(content().json("""
					["a 의 메모"]"""));
		this.mockMvc.perform(get("/api/test/notes")).andExpect(status().isUnauthorized());
	}

	private static Map<String, Class<?>> registry() {
		Map<String, Class<?>> registry = new HashMap<>(UserScopedEndpoints.ENDPOINTS);
		registry.put("GET /api/test/notes", UserScopeFixtureTests.class);
		registry.put("POST /api/test/notes", UserScopeFixtureTests.class);
		registry.put("GET /api/test/blank-before-change", UserScopeFixtureTests.class);
		registry.put("GET /api/test/gone", UserScopeFixtureTests.class);
		return Map.copyOf(registry);
	}

	@Test
	void guardCatchesEveryCarelessHandler() {
		UserScopeRules.Verdict verdict = UserScopeRules.check(this.mapping, REGISTRY);

		assertThat(verdict.endpoints()).contains("GET /api/test/notes", "POST /api/test/notes",
				"GET /api/test/careless/{userId}", "POST /api/test/careless", "GET /api/test/careless-param",
				"GET /api/test/careless-query", "GET /api/test/careless-model", "GET /api/test/blank",
				"GET /api/test/blank-before-change", "GET /api/test/public-before-change");
		assertThat(verdict.violations()).containsExactlyInAnyOrder(
				"GET /api/test/careless/{userId} — 공개 표시와 격리 시험 등록 가운데 꼭 하나가 있어야 한다(공개 false · 등록 false)",
				"GET /api/test/careless/{userId} 의 인자 0 — 요청에서 이름 userId 를 받는다",
				"POST /api/test/careless — 공개 표시와 격리 시험 등록 가운데 꼭 하나가 있어야 한다(공개 false · 등록 false)",
				"POST /api/test/careless 의 인자 0 — 요청 레코드가 사용자 ID(UserId · userId)를 담는다",
				"GET /api/test/careless-param — 공개 표시와 격리 시험 등록 가운데 꼭 하나가 있어야 한다(공개 false · 등록 false)",
				"GET /api/test/careless-param 의 인자 0 — 요청에서 이름 userId 를 받는다",
				"GET /api/test/careless-query — 공개 표시와 격리 시험 등록 가운데 꼭 하나가 있어야 한다(공개 false · 등록 false)",
				"GET /api/test/careless-query 의 인자 0 — 요청 레코드가 사용자 ID(UserId · userId)를 담는다",
				"GET /api/test/careless-model — 공개 표시와 격리 시험 등록 가운데 꼭 하나가 있어야 한다(공개 false · 등록 false)",
				"GET /api/test/careless-model 의 인자 0 — UserId 에 애너테이션을 달면 요청에서 묶인다",
				"GET /api/test/blank — 공개 표시의 이유가 비었다",
				"GET /api/test/blank-before-change — 비밀번호 변경 전 허용 표시의 이유가 비었다",
				"GET /api/test/public-before-change — 공개 처리기에 비밀번호 변경 전 허용 표시를 달았다",
				"GET /api/test/gone — 등록했지만 그런 처리기가 없다(낡은 등록)");
	}

	// 중첩 클래스라 다른 시험 컨텍스트의 컴포넌트 스캔에는 걸리지 않는다 — 이 컨텍스트에만 명시해 등록한다
	@RestController
	static class NotesController {

		private final Map<UserId, List<String>> notes = new ConcurrentHashMap<>();

		@PostMapping("/api/test/notes")
		void add(UserId user, @RequestBody Note note) {
			this.notes.computeIfAbsent(user, key -> new CopyOnWriteArrayList<>()).add(note.text());
		}

		@GetMapping("/api/test/notes")
		List<String> list(UserId user) {
			return this.notes.getOrDefault(user, List.of());
		}

	}

	/** 일부러 틀린 처리기 — 등록 가드가 잡아야 한다 */
	@RestController
	static class CarelessController {

		@GetMapping("/api/test/careless/{userId}")
		String byPath(@PathVariable long userId) {
			return "careless";
		}

		@PostMapping("/api/test/careless")
		String byBody(@RequestBody Owned body) {
			return "careless";
		}

		// 애너테이션의 이름(value)이 인자 이름과 다를 때도 묶는 이름으로 판정한다
		@GetMapping("/api/test/careless-param")
		String byParam(@RequestParam("userId") long owner) {
			return "careless";
		}

		// 묶기 애너테이션 없는 레코드도 질의 값으로 묶인다
		@GetMapping("/api/test/careless-query")
		String byQuery(Owned query) {
			return "careless";
		}

		@GetMapping("/api/test/careless-model")
		String byModel(@ModelAttribute UserId user) {
			return "careless";
		}

		@PublicApi(reason = " ")
		@GetMapping("/api/test/blank")
		String blank() {
			return "blank";
		}

		@AllowedBeforePasswordChange(reason = " ")
		@GetMapping("/api/test/blank-before-change")
		String blankBeforeChange(UserId user) {
			return "blank";
		}

		@PublicApi(reason = "시험 — 공개 처리기")
		@AllowedBeforePasswordChange(reason = "시험 — 공개 처리기에는 달지 않는다")
		@GetMapping("/api/test/public-before-change")
		String publicBeforeChange() {
			return "public";
		}

	}

	record Note(String text) {
	}

	record Owned(UserId owner, String text) {
	}

}
