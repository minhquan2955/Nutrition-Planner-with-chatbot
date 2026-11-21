package vn.nutrition.profile;

import jakarta.validation.Valid;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import vn.nutrition.auth.Account;
import vn.nutrition.calculation.NutritionCalculator.Metrics;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ProfileController {
    private final ProfileService profiles;
    private final JdbcTemplate db;
    public ProfileController(ProfileService profiles,JdbcTemplate db) {this.profiles=profiles;this.db=db;}
    @GetMapping("/me/profile") public ProfileService.View get(@AuthenticationPrincipal Account account) {return profiles.get(account);}
    @PutMapping("/me/profile") public ProfileService.View save(@AuthenticationPrincipal Account account,@Valid @RequestBody ProfileInput input) {return profiles.save(account,input);}
    @DeleteMapping("/me/profile")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Account account,@RequestParam int expectedVersion) {
        profiles.delete(account,expectedVersion);
    }
    public record MetricsView(boolean adult,Metrics metrics,
        String ruleVersion,String formulaName,String sourceReference,Instant calculatedAt,int profileVersion) {}
    @GetMapping("/me/metrics") public MetricsView metrics(@AuthenticationPrincipal Account account) {
        var p=profiles.get(account);return new MetricsView(p.adult(),p.metrics(),p.ruleVersion(),p.formulaName(),p.sourceReference(),p.calculatedAt(),p.version());
    }
    public record Ingredient(UUID id,String name,String status) {}
    @GetMapping("/ingredients") public List<Ingredient> ingredients() {
        return db.query("SELECT id,name,status FROM ingredients ORDER BY name",(rs,n)->new Ingredient(rs.getObject("id",UUID.class),rs.getString("name"),rs.getString("status")));
    }
}
