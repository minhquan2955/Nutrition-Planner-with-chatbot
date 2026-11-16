package vn.nutrition.auth;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="app.demo-enabled",havingValue="true")
public class DemoAccounts implements ApplicationRunner {
    private final AuthService auth;
    private final JdbcTemplate db;
    private final String userPassword,adminPassword;
    public DemoAccounts(AuthService auth,JdbcTemplate db,@Value("${app.demo-user-password}") String userPassword,@Value("${app.demo-admin-password}") String adminPassword) {
        this.auth=auth;this.db=db;this.userPassword=userPassword;this.adminPassword=adminPassword;
    }
    @Override public void run(ApplicationArguments args) {
        create("user@example.test","Người dùng mẫu",userPassword,"USER");
        create("admin@example.test","Quản trị viên mẫu",adminPassword,"ADMIN");
    }
    private void create(String email,String name,String password,String role) {
        if(db.queryForObject("SELECT count(*) FROM users WHERE email=?",Integer.class,email)>0) return;
        var account=auth.register(email,password,name);
        db.update("UPDATE users SET role=? WHERE id=?",role,account.id());
    }
}
