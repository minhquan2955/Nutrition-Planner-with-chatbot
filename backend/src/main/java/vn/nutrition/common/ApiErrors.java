package vn.nutrition.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrors {
    private static final Logger LOG = LoggerFactory.getLogger(ApiErrors.class);
    public record Error(String code, String message, Map<String,String> fieldErrors, String traceId) {}
    public static Error body(String code, String message, Map<String,String> fields) {
        return new Error(code,message,fields,UUID.randomUUID().toString());
    }
    public static void write(HttpServletResponse response, ObjectMapper json, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), body(code,message,Map.of()));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> business(ApiException e) {
        var error = e.traceId == null ? body(e.code, e.getMessage(), e.fieldErrors)
            : new Error(e.code, e.getMessage(), e.fieldErrors, e.traceId.toString());
        if (e.traceId != null && e.status >= 500) LOG.warn("Request {} failed ({})", error.traceId(), error.code());
        return ResponseEntity.status(e.status).body(error);
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> invalid(MethodArgumentNotValidException e) {
        Map<String,String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR","Vui lòng kiểm tra thông tin đã nhập.",fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Error> malformed() { return ResponseEntity.badRequest().body(body("INVALID_JSON","Dữ liệu không đúng định dạng hoặc chứa trường không được hỗ trợ.",Map.of())); }
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<Error> invalidParameter(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR", "Tham số yêu cầu không hợp lệ.", Map.of()));
    }
    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<Error> missingParameter(MissingServletRequestParameterException e) {
        return business(ApiException.field(e.getParameterName(), "Nhập thông tin bắt buộc."));
    }
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<Error> missingPart(MissingServletRequestPartException e) {
        return business(ApiException.field(e.getRequestPartName(), "Chọn tệp để tải lên."));
    }
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Error> uploadTooLarge() {
        return business(new ApiException(413, "PDF_TOO_LARGE", "Tệp PDF phải có kích thước tối đa 20 MB."));
    }
    @ExceptionHandler(MultipartException.class)
    ResponseEntity<Error> malformedUpload() {
        return business(ApiException.field("file", "Dữ liệu tải tệp lên không hợp lệ."));
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected() {
        // Never echo SQL, input profiles, credentials or stack traces to the client.
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR","Không thể xử lý yêu cầu. Vui lòng thử lại.",Map.of()));
    }
}
