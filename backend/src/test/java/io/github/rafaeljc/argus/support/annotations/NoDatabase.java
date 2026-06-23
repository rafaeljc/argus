package io.github.rafaeljc.argus.support.annotations;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.test.context.ActiveProfiles;

@Target(TYPE)
@Retention(RUNTIME)
@ActiveProfiles("no-db")
public @interface NoDatabase {}
