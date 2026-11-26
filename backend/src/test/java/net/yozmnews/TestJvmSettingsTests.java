package net.yozmnews;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;

import com.sun.management.HotSpotDiagnosticMXBean;
import com.sun.management.VMOption;
import org.junit.jupiter.api.Test;

/**
 * 테스트 JVM 의 메모리 설정 — 메모리가 모자라면 JVM 이 끝나 Gradle 이 실패로 끝낸다(docs/development/infrastructure.md "테스트 JVM").
 */
class TestJvmSettingsTests {

	@Test
	void anOutOfMemoryErrorEndsTheTestJvm() {
		HotSpotDiagnosticMXBean diagnostics = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
		// 대조 — 빌드 설정이 JVM 을 띄운 인자로 준 옵션(힙 상한)을 이 길로 읽는다. 값은 build.gradle.kts 가 정하므로 여기 다시 적지 않는다
		assertThat(diagnostics.getVMOption("MaxHeapSize").getOrigin()).isEqualTo(VMOption.Origin.VM_CREATION);

		VMOption option = diagnostics.getVMOption("ExitOnOutOfMemoryError");
		assertThat(option.getValue()).isEqualTo("true");
		// 값이 JVM 을 띄운 인자에서 왔다(환경 변수 JAVA_TOOL_OPTIONS 등 다른 출처가 아니다)
		assertThat(option.getOrigin()).isEqualTo(VMOption.Origin.VM_CREATION);
	}

}
