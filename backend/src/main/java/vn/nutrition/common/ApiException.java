package vn.nutrition.common;

import java.util.Map;
import java.util.UUID;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public final Map<String, String> fieldErrors;
    public final UUID traceId;
    public ApiException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public ApiException(int status, String code, String message, Map<String, String> fieldErrors) {
        this(status, code, message, fieldErrors, null);
    }
    public ApiException(int status, String code, String message, Map<String, String> fieldErrors, UUID traceId) {
        super(message); this.status=status; this.code=code; this.fieldErrors=fieldErrors;
        this.traceId = traceId;
    }
    public static ApiException field(String field, String message) {
        return new ApiException(400,"VALIDATION_ERROR","Vui lòng kiểm tra thông tin đã nhập.",Map.of(field,message));
    }
}
