package vn.nutrition.common;

import java.util.Map;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public final Map<String, String> fieldErrors;
    public ApiException(int status, String code, String message) { this(status, code, message, Map.of()); }
    public ApiException(int status, String code, String message, Map<String, String> fieldErrors) {
        super(message); this.status=status; this.code=code; this.fieldErrors=fieldErrors;
    }
    public static ApiException field(String field, String message) {
        return new ApiException(400,"VALIDATION_ERROR","Vui lòng kiểm tra thông tin đã nhập.",Map.of(field,message));
    }
}
