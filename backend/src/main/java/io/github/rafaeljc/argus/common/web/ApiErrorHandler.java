package io.github.rafaeljc.argus.common.web;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.FieldError;
import io.github.rafaeljc.argus.common.domain.RateLimitExceededException;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class ApiErrorHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiErrorHandler.class);

    private static final String CODE_VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String CODE_MALFORMED_REQUEST = "MALFORMED_REQUEST";
    private static final String CODE_UNSUPPORTED_MEDIA_TYPE = "UNSUPPORTED_MEDIA_TYPE";
    private static final String CODE_METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";
    private static final String CODE_FORBIDDEN = "FORBIDDEN";
    private static final String CODE_INTERNAL_ERROR = "INTERNAL_ERROR";

    private static final String FALLBACK_DETAIL = "invalid";
    private static final String TRACE_ID_KEY = "traceId";

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ErrorEnvelope> handleDomain(DomainException ex) {
        LOG.info("domain error code={} traceId={} message={}", ex.code(), traceId(), ex.getMessage());
        return response(ex.status(), ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorEnvelope> handleRateLimitExceeded(RateLimitExceededException ex) {
        LOG.info("rate limit exceeded code={} traceId={} retryAfterSeconds={}",
                ex.code(), traceId(), ex.retryAfterSeconds());
        return response(ex.status(), ex.code(), ex.getMessage(), ex.details(), ex.headers());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorEnvelope> handleBeanValidation(MethodArgumentNotValidException ex) {
        List<FieldError> details = ex.getBindingResult().getFieldErrors().stream()
                .map(ApiErrorHandler::toFieldError)
                .toList();
        LOG.info("validation error traceId={} count={}", traceId(), details.size());
        return response(422, CODE_VALIDATION_ERROR, "Request validation failed", details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorEnvelope> handleMalformed(HttpMessageNotReadableException ex) {
        LOG.info("malformed request traceId={}", traceId());
        return response(400, CODE_MALFORMED_REQUEST, "Malformed request body", List.of());
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorEnvelope> handleParamValidation(HandlerMethodValidationException ex) {
        List<FieldError> details = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> toFieldError(result, error)))
                .toList();
        LOG.info("param validation error traceId={} count={}", traceId(), details.size());
        return response(422, CODE_VALIDATION_ERROR, "Request validation failed", details);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorEnvelope> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        LOG.info("type mismatch traceId={}", traceId());
        return response(400, CODE_MALFORMED_REQUEST, "Malformed request", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException ex) {
        LOG.info("unsupported media type traceId={}", traceId());
        return response(415, CODE_UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorEnvelope> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        LOG.info("method not allowed traceId={}", traceId());
        return response(405, CODE_METHOD_NOT_ALLOWED, "Method not allowed", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorEnvelope> handleAccessDenied(AccessDeniedException ex) {
        LOG.warn("access denied traceId={}", traceId());
        return response(403, CODE_FORBIDDEN, "Forbidden", List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ErrorEnvelope> handleDataAccess(DataAccessException ex) {
        LOG.error("data access error traceId={}", traceId(), ex);
        return response(500, CODE_INTERNAL_ERROR, "Internal error", List.of());
    }

    @ExceptionHandler(Throwable.class)
    public ResponseEntity<ErrorEnvelope> handleFallback(Throwable ex) {
        LOG.error("unhandled exception traceId={}", traceId(), ex);
        return response(500, CODE_INTERNAL_ERROR, "Internal error", List.of());
    }

    private static ResponseEntity<ErrorEnvelope> response(
            int status, String code, String message, List<FieldError> details) {
        return response(status, code, message, details, Map.of());
    }

    private static ResponseEntity<ErrorEnvelope> response(
            int status, String code, String message, List<FieldError> details, Map<String, String> headers) {
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        headers.forEach(builder::header);
        return builder.body(new ErrorEnvelope(new ApiError(code, message, details)));
    }

    private static FieldError toFieldError(org.springframework.validation.FieldError binding) {
        return new FieldError(
                toSnakeCase(binding.getField()),
                normalizeCode(binding.getCode()),
                binding.getDefaultMessage() == null ? FALLBACK_DETAIL : binding.getDefaultMessage());
    }

    private static FieldError toFieldError(ParameterValidationResult result, MessageSourceResolvable error) {
        String field = toSnakeCase(result.getMethodParameter().getParameterName());
        String[] codes = error.getCodes();
        String code = normalizeCode(codes == null || codes.length == 0 ? null : codes[codes.length - 1]);
        String message = error.getDefaultMessage() == null ? FALLBACK_DETAIL : error.getDefaultMessage();
        return new FieldError(field, code, message);
    }

    private static String normalizeCode(String code) {
        return code == null ? FALLBACK_DETAIL : toSnakeCase(code);
    }

    private static String toSnakeCase(String camel) {
        return camel.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static String traceId() {
        return MDC.get(TRACE_ID_KEY);
    }
}
