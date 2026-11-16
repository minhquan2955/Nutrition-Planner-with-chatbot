package vn.nutrition.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestControllerAdvice
public class ApiErrors {
    public record Error(String code, String message, Map<String,String> fieldErrors, String traceId) {}
    public static Error body(String code, String message, Map<String,String> fields) {
        return new Error(code,message,fields,UUID.randomUUID().toString());
    }
    public static void write(HttpServletResponse response, ObjectMapper json, int status, String code, String message) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        json.writeValue(response.getWriter(), body(code,message,Map.of()));
    }
    @ExceptionHandler(ApiException.class)
    ResponseEntity<Error> business(ApiException e) { return ResponseEntity.status(e.status).body(body(e.code,e.getMessage(),e.fieldErrors)); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Error> invalid(MethodArgumentNotValidException e) {
        Map<String,String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.putIfAbsent(f.getField(),f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(body("VALIDATION_ERROR","Vui lòng kiểm tra thông tin đã nhập.",fields));
    }
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Error> malformed() { return ResponseEntity.badRequest().body(body("INVALID_JSON","Dữ liệu không đúng định dạng hoặc chứa trường không được hỗ trợ.",Map.of())); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> unexpected() {
        // Never echo SQL, input profiles, credentials or stack traces to the client.
        return ResponseEntity.internalServerError().body(body("INTERNAL_ERROR","Không thể xử lý yêu cầu. Vui lòng thử lại.",Map.of()));
    }
}
