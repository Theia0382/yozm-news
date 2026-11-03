package net.yozmnews.evaluation.application;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import net.yozmnews.evaluation.domain.threshold.ThresholdRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * 시험 설정 — 태그 기준 · 저장 기준 저장소를 감싸, 지금 기준을 읽는 순간({@code current}) 그 연결의 격리 수준 · 읽기 전용 여부를
 * {@code current_setting} 으로 DB 에 묻고 적는다. 집계와 같은 트랜잭션 연결로 묻는다 — 코드가 건 트랜잭션 정의를 다시 읽지 않는다.
 */
@TestConfiguration(proxyBeanMethods = false)
class TransactionSettings {

	/** 물은 순서대로 — {@code isolation} · {@code readOnly} */
	final List<Map<String, String>> seen = new CopyOnWriteArrayList<>();

	@Bean
	static BeanPostProcessor recordTransactionSettings(ObjectProvider<JdbcClient> jdbc,
			ObjectProvider<TransactionSettings> settings) {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String name) {
				if (!(bean instanceof ThresholdRepository delegate)) {
					return bean;
				}
				return Proxy.newProxyInstance(ThresholdRepository.class.getClassLoader(),
						new Class<?>[] { ThresholdRepository.class }, (proxy, method, args) -> {
							if (method.getName().equals("current")) {
								settings.getObject().seen.add(Map.of("isolation",
										setting(jdbc.getObject(), "transaction_isolation"), "readOnly",
										setting(jdbc.getObject(), "transaction_read_only")));
							}
							try {
								return method.invoke(delegate, args);
							}
							catch (InvocationTargetException ex) {
								throw ex.getCause();
							}
						});
			}
		};
	}

	private static String setting(JdbcClient jdbc, String name) {
		return jdbc.sql("select current_setting(:name)").param("name", name).query(String.class).single();
	}

}
