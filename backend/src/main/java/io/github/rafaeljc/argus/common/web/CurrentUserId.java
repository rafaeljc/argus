package io.github.rafaeljc.argus.common.web;

import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import org.springframework.security.core.annotation.AuthenticationPrincipal;

// Injects the authenticated user's UserId. Composed over @AuthenticationPrincipal: the SpEL
// expression invokes userId() on the auth module's session principal at runtime, so this module
// never imports the principal type and stays within its allowed dependency boundary.
@Target(PARAMETER)
@Retention(RUNTIME)
@AuthenticationPrincipal(expression = "userId()")
public @interface CurrentUserId {}
