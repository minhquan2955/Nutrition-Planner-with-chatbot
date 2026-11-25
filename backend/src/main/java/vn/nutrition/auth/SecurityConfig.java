package vn.nutrition.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.filter.OncePerRequestFilter;
import vn.nutrition.common.ApiErrors;
import java.io.IOException;
import java.util.List;

@Configuration
public class SecurityConfig {
    @Bean PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean SecurityFilterChain security(HttpSecurity http,SessionService sessions,ObjectMapper json,@Value("${app.cookie-secure}") boolean secure) throws Exception {
        CookieCsrfTokenRepository csrf=new CookieCsrfTokenRepository();
        csrf.setCookieCustomizer(cookie->cookie.httpOnly(true).secure(secure).sameSite("Lax").path("/"));
        return http.sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .csrf(c->c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .requestCache(c->c.disable()).formLogin(c->c.disable()).httpBasic(c->c.disable()).logout(c->c.disable())
            .authorizeHttpRequests(a->a
                .requestMatchers("/api/v1/health","/api/v1/auth/csrf","/api/v1/auth/register","/api/v1/auth/login","/api/v1/openapi/**","/api/v1/docs","/swagger-ui/**").permitAll()
                .requestMatchers("/api/v1/auth/me","/api/v1/auth/logout","/api/v1/auth/change-password","/api/v1/me/**","/api/v1/ingredients").authenticated()
                .requestMatchers("/api/v1/foods","/api/v1/recipes","/api/v1/meal-plan-jobs/**","/api/v1/meal-plans/**").authenticated()
                .requestMatchers("/api/v1/conversations","/api/v1/conversations/**").authenticated()
                .requestMatchers("/api/v1/admin/**").hasRole("ADMIN").anyRequest().denyAll())
            .exceptionHandling(e->e
                .authenticationEntryPoint((req,res,ex)->ApiErrors.write(res,json,401,"UNAUTHENTICATED","Vui lòng đăng nhập để tiếp tục."))
                .accessDeniedHandler((req,res,ex)->ApiErrors.write(res,json,403,"FORBIDDEN","Yêu cầu không được phép hoặc mã bảo vệ CSRF không hợp lệ.")))
            .addFilterBefore(new OncePerRequestFilter() {
                @Override protected void doFilterInternal(HttpServletRequest req,HttpServletResponse res,FilterChain chain) throws ServletException,IOException {
                    var account=sessions.find(req).orElse(null);
                    if(account!=null) {
                        boolean mutation=!List.of("GET","HEAD","OPTIONS").contains(req.getMethod());
                        boolean authEntry=List.of("/api/v1/auth/login","/api/v1/auth/register").contains(req.getRequestURI());
                        // A tab must act on the same session it loaded, even if another tab replaced the shared cookie.
                        if(mutation && !authEntry && !account.sessionContext().toString().equals(req.getHeader("X-Session-Context"))) {
                            ApiErrors.write(res,json,409,"SESSION_CHANGED","Phiên đăng nhập đã thay đổi. Vui lòng tải lại trang trước khi tiếp tục.");return;
                        }
                        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(account,null,List.of(new SimpleGrantedAuthority("ROLE_"+account.role()))));
                    }
                    chain.doFilter(req,res);
                }
            },UsernamePasswordAuthenticationFilter.class).build();
    }
}
