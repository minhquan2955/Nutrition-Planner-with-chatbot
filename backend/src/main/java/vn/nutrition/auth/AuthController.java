package vn.nutrition.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final AuthService auth;
    private final SessionService sessions;
    public AuthController(AuthService auth,SessionService sessions) {this.auth=auth;this.sessions=sessions;}
    public record Register(@NotBlank @Email(message="Email không hợp lệ.") @Size(max=255) String email,
        @NotBlank @Size(max=72) String password,@NotBlank(message="Vui lòng nhập tên hiển thị.") @Size(max=100) String displayName) {
        public Register {email=AuthService.email(email);}
    }
    public record Login(@NotBlank @Size(max=255) String email,@NotBlank @Size(max=72) String password) {}
    public record ChangePassword(@NotBlank @Size(max=72) String currentPassword,@NotBlank @Size(max=72) String newPassword) {}
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @PostMapping("/register") @ResponseStatus(HttpStatus.CREATED)
    public Account register(@Valid @RequestBody Register input) {return auth.register(input.email(),input.password(),input.displayName());}
    @PostMapping("/login") public Account login(@Valid @RequestBody Login input,HttpServletRequest req,HttpServletResponse res) {return auth.login(input.email(),input.password(),req,res);}
    @GetMapping("/me") public Account me(@AuthenticationPrincipal Account account) {return account;}
    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest req,HttpServletResponse res) {sessions.revoke(req);sessions.clear(res);}
    @PostMapping("/change-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void change(@AuthenticationPrincipal Account account,@Valid @RequestBody ChangePassword input,HttpServletResponse res) {auth.changePassword(account,input.currentPassword(),input.newPassword(),res);}
}
