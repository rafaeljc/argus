package io.github.rafaeljc.argus.common.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.rafaeljc.argus.common.domain.DomainException;
import io.github.rafaeljc.argus.common.domain.FieldError;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

class ApiErrorHandlerTest {

    private final ApiErrorHandler handler = new ApiErrorHandler();

    private static final class FakeDomainException extends DomainException {
        private final String code;
        private final int status;
        private final List<FieldError> details;

        FakeDomainException(String code, int status, String message, List<FieldError> details) {
            super(message);
            this.code = code;
            this.status = status;
            this.details = details;
        }

        @Override
        public String code() {
            return code;
        }

        @Override
        public int status() {
            return status;
        }

        @Override
        public List<FieldError> details() {
            return details;
        }
    }

    @Test
    void handleDomain_readsCodeStatusMessageAndDetails() {
        FieldError fe = new FieldError("ticker", "delisted", "AAPL is delisted");
        DomainException ex = new FakeDomainException("TICKER_DELISTED", 422, "ticker delisted", List.of(fe));

        ResponseEntity<ErrorEnvelope> response = handler.handleDomain(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        ApiError error = response.getBody().error();
        assertThat(error.code()).isEqualTo("TICKER_DELISTED");
        assertThat(error.message()).isEqualTo("ticker delisted");
        assertThat(error.details()).containsExactly(fe);
    }

    @Test
    void handleDomain_honoursCustomStatus() {
        DomainException ex = new FakeDomainException("ACCOUNT_SUSPENDED", 403, "suspended", List.of());

        ResponseEntity<ErrorEnvelope> response = handler.handleDomain(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().error().code()).isEqualTo("ACCOUNT_SUSPENDED");
    }

    @Test
    void handleBeanValidation_returns422WithSnakeCaseFieldAndLowercaseCode() {
        BindingResult binding = mock(BindingResult.class);
        org.springframework.validation.FieldError bindingError =
                new org.springframework.validation.FieldError(
                        "createTransactionRequest", "tradeDate", null, false,
                        new String[] {"NotNull"}, null, "must not be null");
        when(binding.getFieldErrors()).thenReturn(List.of(bindingError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ErrorEnvelope> response = handler.handleBeanValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        ApiError error = response.getBody().error();
        assertThat(error.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(error.details()).hasSize(1);
        FieldError detail = error.details().get(0);
        assertThat(detail.field()).isEqualTo("trade_date");
        assertThat(detail.code()).isEqualTo("not_null");
        assertThat(detail.message()).isEqualTo("must not be null");
    }

    @Test
    void handleBeanValidation_missingDefaultMessage_fallsBackToInvalid() {
        BindingResult binding = mock(BindingResult.class);
        org.springframework.validation.FieldError bindingError =
                new org.springframework.validation.FieldError(
                        "req", "value", null, false, new String[] {"NotBlank"}, null, null);
        when(binding.getFieldErrors()).thenReturn(List.of(bindingError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ErrorEnvelope> response = handler.handleBeanValidation(ex);

        assertThat(response.getBody().error().details().get(0).message()).isEqualTo("invalid");
    }

    @Test
    void handleBeanValidation_missingErrorCode_fallsBackToInvalid() {
        BindingResult binding = mock(BindingResult.class);
        org.springframework.validation.FieldError bindingError =
                new org.springframework.validation.FieldError(
                        "req", "value", null, false, null, null, "bad");
        when(binding.getFieldErrors()).thenReturn(List.of(bindingError));
        MethodArgumentNotValidException ex = mock(MethodArgumentNotValidException.class);
        when(ex.getBindingResult()).thenReturn(binding);

        ResponseEntity<ErrorEnvelope> response = handler.handleBeanValidation(ex);

        assertThat(response.getBody().error().details().get(0).code()).isEqualTo("invalid");
    }

    @Test
    void handleParamValidation_outOfRangeParam_returns422WithSnakeCaseFieldAndLowercaseCode() {
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterName()).thenReturn("perPage");
        MessageSourceResolvable error = mock(MessageSourceResolvable.class);
        when(error.getCodes()).thenReturn(new String[] {"Max.list.perPage", "Max.perPage", "Max.int", "Max"});
        when(error.getDefaultMessage()).thenReturn("must be less than or equal to 200");
        ParameterValidationResult result = mock(ParameterValidationResult.class);
        when(result.getMethodParameter()).thenReturn(parameter);
        when(result.getResolvableErrors()).thenReturn(List.of(error));
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getParameterValidationResults()).thenReturn(List.of(result));

        ResponseEntity<ErrorEnvelope> response = handler.handleParamValidation(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(422);
        ApiError responseError = response.getBody().error();
        assertThat(responseError.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(responseError.details()).hasSize(1);
        FieldError detail = responseError.details().get(0);
        assertThat(detail.field()).isEqualTo("per_page");
        assertThat(detail.code()).isEqualTo("max");
        assertThat(detail.message()).isEqualTo("must be less than or equal to 200");
    }

    @Test
    void handleParamValidation_missingDefaultMessage_fallsBackToInvalid() {
        MethodParameter parameter = mock(MethodParameter.class);
        when(parameter.getParameterName()).thenReturn("page");
        MessageSourceResolvable error = mock(MessageSourceResolvable.class);
        when(error.getCodes()).thenReturn(new String[] {"Min"});
        when(error.getDefaultMessage()).thenReturn(null);
        ParameterValidationResult result = mock(ParameterValidationResult.class);
        when(result.getMethodParameter()).thenReturn(parameter);
        when(result.getResolvableErrors()).thenReturn(List.of(error));
        HandlerMethodValidationException ex = mock(HandlerMethodValidationException.class);
        when(ex.getParameterValidationResults()).thenReturn(List.of(result));

        ResponseEntity<ErrorEnvelope> response = handler.handleParamValidation(ex);

        assertThat(response.getBody().error().details().get(0).message()).isEqualTo("invalid");
    }

    @Test
    void handleTypeMismatch_returns400MalformedRequest() {
        MethodArgumentTypeMismatchException ex = mock(MethodArgumentTypeMismatchException.class);

        ResponseEntity<ErrorEnvelope> response = handler.handleTypeMismatch(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(response.getBody().error().details()).isEmpty();
    }

    @Test
    void handleMalformed_returns400MalformedRequest() {
        HttpMessageNotReadableException ex =
                new HttpMessageNotReadableException("bad json", mock(HttpInputMessage.class));

        ResponseEntity<ErrorEnvelope> response = handler.handleMalformed(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody().error().code()).isEqualTo("MALFORMED_REQUEST");
        assertThat(response.getBody().error().details()).isEmpty();
    }

    @Test
    void handleUnsupportedMediaType_returns415() {
        HttpMediaTypeNotSupportedException ex =
                new HttpMediaTypeNotSupportedException(MediaType.TEXT_PLAIN, List.of(MediaType.APPLICATION_JSON));

        ResponseEntity<ErrorEnvelope> response = handler.handleUnsupportedMediaType(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(415);
        assertThat(response.getBody().error().code()).isEqualTo("UNSUPPORTED_MEDIA_TYPE");
    }

    @Test
    void handleMethodNotSupported_returns405() {
        HttpRequestMethodNotSupportedException ex = new HttpRequestMethodNotSupportedException("DELETE");

        ResponseEntity<ErrorEnvelope> response = handler.handleMethodNotSupported(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(405);
        assertThat(response.getBody().error().code()).isEqualTo("METHOD_NOT_ALLOWED");
    }

    @Test
    void handleAccessDenied_returns403Forbidden() {
        AccessDeniedException ex = new AccessDeniedException("nope");

        ResponseEntity<ErrorEnvelope> response = handler.handleAccessDenied(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(403);
        assertThat(response.getBody().error().code()).isEqualTo("FORBIDDEN");
    }

    @Test
    void handleDataAccess_returns500InternalError() {
        DataAccessException ex = new DataAccessResourceFailureException("db down");

        ResponseEntity<ErrorEnvelope> response = handler.handleDataAccess(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody().error().code()).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void handleFallback_unknownThrowable_returns500WithGenericMessageAndNoStackTrace() {
        Throwable ex = new IllegalStateException("internal detail that must not leak");

        ResponseEntity<ErrorEnvelope> response = handler.handleFallback(ex);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        ApiError error = response.getBody().error();
        assertThat(error.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(error.message()).doesNotContain("internal detail");
        assertThat(error.details()).isEmpty();
    }
}
