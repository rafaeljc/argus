package io.github.rafaeljc.argus;

import org.springframework.boot.SpringApplication;

public class TestArgusApplication {

	public static void main(String[] args) {
		SpringApplication.from(ArgusApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
