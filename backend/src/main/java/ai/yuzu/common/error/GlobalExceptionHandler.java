package ai.yuzu.common.error;

import ai.yuzu.common.time.NaturalTime;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/** v0.0.1 🍊 Converts every exception thrown by a REST handler into an {@link ApiError} response. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final NaturalTime time;

    /** v0.0.1 🍊 Injects the natural-time renderer used to stamp errors. */
    public GlobalExceptionHandler(NaturalTime time) {
        this.time = time;
    }

    /** v0.0.1 🍊 Expected domain failures keep their code, message and details. */
    @ExceptionHandler(YuzuException.class)
    public ResponseEntity<ApiError> handleYuzu(YuzuException e) {
        if (e.code().status().is5xxServerError()) {
            log.warn("Request failed with {}: {}", e.code(), e.getMessage(), e);
        }
        return ResponseEntity.status(e.code().status()).body(ApiError.of(e, time.now()));
    }

    /** v0.0.1 🍊 Bean-validation failures on request bodies become BAD_REQUEST with field errors. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleInvalidBody(MethodArgumentNotValidException e) {
        Map<String, Object> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return badRequest("Some fields are invalid.", Map.of("fields", fields));
    }

    /** v0.0.1 🍊 Parameter-level validation and parsing failures become BAD_REQUEST. */
    @ExceptionHandler({ConstraintViolationException.class, HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class,
            IllegalArgumentException.class})
    public ResponseEntity<ApiError> handleBadInput(Exception e) {
        return badRequest(e.getMessage(), Map.of());
    }

    /** v0.0.1 🍊 Unknown routes become NOT_FOUND. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoRoute(NoResourceFoundException e) {
        ErrorCode code = ErrorCode.NOT_FOUND;
        return ResponseEntity.status(code.status())
                .body(new ApiError(code.name(), code.defaultMessage(), Map.of(), null, time.now()));
    }

    /** v0.0.1 🍊 Anything unexpected becomes INTERNAL and is logged with its stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception e) {
        log.error("Unexpected error", e);
        ErrorCode code = ErrorCode.INTERNAL;
        return ResponseEntity.status(code.status())
                .body(new ApiError(code.name(), code.defaultMessage(), Map.of("type", e.getClass().getSimpleName()),
                        null, time.now()));
    }

    /** v0.0.1 🍊 Shared BAD_REQUEST builder. */
    private ResponseEntity<ApiError> badRequest(String message, Map<String, Object> details) {
        ErrorCode code = ErrorCode.BAD_REQUEST;
        return ResponseEntity.status(code.status())
                .body(new ApiError(code.name(), message == null ? code.defaultMessage() : message, details, null,
                        time.now()));
    }
}
