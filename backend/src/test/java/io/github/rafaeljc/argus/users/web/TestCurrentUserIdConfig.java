package io.github.rafaeljc.argus.users.web;

import io.github.rafaeljc.argus.common.domain.UserId;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@TestConfiguration
public class TestCurrentUserIdConfig implements WebMvcConfigurer {

    public static final String HEADER = "X-Test-User-Id";

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(new HeaderCurrentUserIdResolver());
    }

    private static final class HeaderCurrentUserIdResolver implements HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(MethodParameter parameter) {
            return parameter.hasParameterAnnotation(CurrentUserId.class)
                    && UserId.class.equals(parameter.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parameter,
                ModelAndViewContainer mavContainer,
                NativeWebRequest webRequest,
                WebDataBinderFactory binderFactory) {
            String header = webRequest.getHeader(HEADER);
            if (header == null || header.isBlank()) {
                throw new IllegalStateException(
                        "Test stub: missing " + HEADER + " — set it on the request via TestRestTemplate.");
            }
            return new UserId(UUID.fromString(header));
        }
    }
}
