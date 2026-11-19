package net.yozmnews;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.InstantSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.List;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaCodeUnitAccess;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * 시계 정본 — main 클래스가 벽시계를 시계 결선 밖에서 직접 읽지 않는지 본다. 앱의 지금은 {@code AppClock} bean 하나에서 읽는다.
 * <p>
 * 호출과 메서드 참조({@code Instant::now} 류)를 함께 본다. 실제 경과를 재는 단조 시계({@code System.nanoTime})는 지금을 읽는 곳이 아니라 규칙
 * 밖이다. 지키는 규칙: platform-R26
 */
class ClockSourceTests {

	/** main 소스에서 실제 시간의 원천을 만드는 유일한 클래스 */
	private static final String CLOCK_CONFIGURATION = "net.yozmnews.platform.application.ClockConfiguration";

	private static final JavaClasses CLASSES = new ClassFileImporter()
		.withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
		.importPackages("net.yozmnews");

	/** 인자 없이 또는 시간대만으로 지금을 읽는 java.time 형 */
	private static final List<Class<?>> NOW_TYPES = List.of(Instant.class, LocalDate.class, LocalDateTime.class,
			LocalTime.class, ZonedDateTime.class, OffsetDateTime.class, OffsetTime.class, Year.class, YearMonth.class,
			MonthDay.class);

	private static final DescribedPredicate<JavaAccess<?>> WALL_CLOCK_READ = DescribedPredicate.describe(
			"read the wall clock", ClockSourceTests::readsWallClock);

	@Test
	void mainClassesReadTheWallClockOnlyThroughTheClockConfiguration() {
		// 대상이 있는지 먼저 센다 — 같은 판정이 허용 클래스의 실제 시간 원천 호출 둘(실제 시간의 시계 · 측정용 시계)을 잡는다
		assertThat(CLASSES.get(CLOCK_CONFIGURATION).getAccessesFromSelf()).filteredOn(WALL_CLOCK_READ).hasSize(2);
		noClasses().that()
			.doNotHaveFullyQualifiedName(CLOCK_CONFIGURATION)
			.should()
			.accessTargetWhere(WALL_CLOCK_READ)
			.check(CLASSES);
	}

	private static boolean readsWallClock(JavaAccess<?> access) {
		if (!(access instanceof JavaCodeUnitAccess<?> codeUnitAccess)) {
			return false;
		}
		String owner = codeUnitAccess.getTargetOwner().getFullName();
		String name = codeUnitAccess.getName();
		List<String> parameters = codeUnitAccess.getTarget()
			.getRawParameterTypes()
			.stream()
			.map(type -> type.getFullName())
			.toList();
		if (owner.equals(InstantSource.class.getName())) {
			return name.equals("system");
		}
		if (owner.equals(Clock.class.getName())) {
			return name.equals("systemUTC") || name.equals("systemDefaultZone") || name.equals("system");
		}
		if (owner.equals(System.class.getName())) {
			return name.equals("currentTimeMillis");
		}
		if (owner.equals(Date.class.getName())) {
			return name.equals("<init>") && parameters.isEmpty();
		}
		boolean nowType = NOW_TYPES.stream().anyMatch(type -> type.getName().equals(owner));
		return nowType && name.equals("now")
				&& (parameters.isEmpty() || parameters.equals(List.of(ZoneId.class.getName())));
	}

}
