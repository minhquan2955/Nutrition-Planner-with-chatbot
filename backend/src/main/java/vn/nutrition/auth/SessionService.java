package vn.nutrition.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class SessionService {
    public static final String COOKIE="NUTRITION_SESSION";
    private final JdbcTemplate db;
    private final boolean secure;
    private final SecureRandom random=new SecureRandom();
    public SessionService(JdbcTemplate db,@Value("${app.cookie-secure}") boolean secure) { this.db=db;this.secure=secure; }
    private String token(HttpServletRequest request) {
        if(request.getCookies()!=null) for(Cookie c:request.getCookies()) if(c.getName().equals(COOKIE)) return c.getValue();
        return null;
    }
    private String hash(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public Optional<Account> find(HttpServletRequest request) {
        String token=token(request);
        if(token==null || token.length()!=43) return Optional.empty();
        return db.query("SELECT u.id,u.email,u.display_name,u.role,s.id AS session_context FROM sessions s JOIN users u ON u.id=s.user_id WHERE s.token_hash=? AND s.revoked_at IS NULL AND s.expires_at>now() AND u.status='ACTIVE'",
            (rs,n)->new Account(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("display_name"),rs.getString("role"),rs.getObject("session_context",UUID.class)),hash(token)).stream().findFirst();
    }
    public UUID create(UUID userId,HttpServletRequest request,HttpServletResponse response) {
        revoke(request);
        byte[] bytes=new byte[32];random.nextBytes(bytes);
        String token=Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id=UUID.randomUUID();
        db.update("INSERT INTO sessions(id,user_id,token_hash,expires_at) VALUES(?,?,?,now()+interval '8 hours')",id,userId,hash(token));
        cookie(response,token,Duration.ofHours(8));
        return id;
    }
    public void revoke(HttpServletRequest request) {
        String token=token(request);
        if(token!=null) db.update("UPDATE sessions SET revoked_at=now() WHERE token_hash=? AND revoked_at IS NULL",hash(token));
    }
    public void revokeAll(UUID userId) { db.update("UPDATE sessions SET revoked_at=now() WHERE user_id=? AND revoked_at IS NULL",userId); }
    public void clear(HttpServletResponse response) { cookie(response,"",Duration.ZERO); }
    private void cookie(HttpServletResponse response,String token,Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE,ResponseCookie.from(COOKIE,token).path("/").httpOnly(true).secure(secure).sameSite("Lax").maxAge(age).build().toString());
    }
}
