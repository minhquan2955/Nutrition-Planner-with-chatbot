package vn.nutrition.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nutrition.common.ApiException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {
    private final JdbcTemplate db;
    private final PasswordEncoder passwords;
    private final SessionService sessions;
    private final String dummyHash;
    public AuthService(JdbcTemplate db,PasswordEncoder passwords,SessionService sessions) {
        this.db=db;this.passwords=passwords;this.sessions=sessions;this.dummyHash=passwords.encode(UUID.randomUUID().toString());
    }
    public static String email(String value) { return value==null?null:value.trim().toLowerCase(Locale.ROOT); }
    private void validPassword(String value,String field) {
        if(value==null || value.length()<12 || value.getBytes(StandardCharsets.UTF_8).length>72)
            throw ApiException.field(field,"Mật khẩu cần ít nhất 12 ký tự và không quá 72 byte UTF-8.");
    }
    @Transactional
    public Account register(String email,String password,String name) {
        validPassword(password,"password");
        Account account=new Account(UUID.randomUUID(),email(email),name.strip(),"USER",null);
        try { db.update("INSERT INTO users(id,email,password_hash,display_name,role,status) VALUES(?,?,?,?,?,'ACTIVE')",account.id(),account.email(),passwords.encode(password),account.displayName(),account.role()); }
        catch(DuplicateKeyException e) { throw new ApiException(409,"EMAIL_EXISTS","Email này đã được đăng ký."); }
        return account;
    }
    private record Credentials(Account account,String hash,String status) {}
    @Transactional
    public Account login(String email,String password,HttpServletRequest request,HttpServletResponse response) {
        // Lock the account row so a concurrent password change cannot leave a newly created session alive.
        var rows=db.query("SELECT * FROM users WHERE email=? FOR UPDATE",(rs,n)->new Credentials(
            new Account(rs.getObject("id",UUID.class),rs.getString("email"),rs.getString("display_name"),rs.getString("role"),null),rs.getString("password_hash"),rs.getString("status")),email(email));
        Credentials found=rows.isEmpty()?null:rows.getFirst();
        boolean matches= password!=null && password.getBytes(StandardCharsets.UTF_8).length<=72 && passwords.matches(password,found==null?dummyHash:found.hash());
        if(found==null || !matches || !found.status().equals("ACTIVE")) throw new ApiException(401,"INVALID_CREDENTIALS","Email hoặc mật khẩu không đúng, hoặc tài khoản không hoạt động.");
        UUID context=sessions.create(found.account().id(),request,response);
        var account=found.account();return new Account(account.id(),account.email(),account.displayName(),account.role(),context);
    }
    @Transactional
    public void changePassword(Account account,String current,String next,HttpServletResponse response) {
        validPassword(next,"newPassword");
        String hash=db.queryForObject("SELECT password_hash FROM users WHERE id=? FOR UPDATE",String.class,account.id());
        if(current==null || current.getBytes(StandardCharsets.UTF_8).length>72 || !passwords.matches(current,hash)) throw ApiException.field("currentPassword","Mật khẩu hiện tại không đúng.");
        if(passwords.matches(next,hash)) throw ApiException.field("newPassword","Mật khẩu mới cần khác mật khẩu hiện tại.");
        db.update("UPDATE users SET password_hash=? WHERE id=?",passwords.encode(next),account.id());
        sessions.revokeAll(account.id());sessions.clear(response);
    }
}
