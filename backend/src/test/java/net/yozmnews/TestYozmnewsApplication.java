package net.yozmnews;

import org.springframework.boot.SpringApplication;

public class TestYozmnewsApplication {

	public static void main(String[] args) {
		SpringApplication.from(YozmnewsApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
