package vn.nutrition.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.nutrition.auth.Account;
import vn.nutrition.calculation.NutritionCalculator;
import vn.nutrition.calculation.NutritionCalculator.Metrics;
import vn.nutrition.common.ApiException;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;

@Service
public class ProfileService {
    private final JdbcTemplate db;
    private final ObjectMapper json;
    private static final ZoneId ZONE=ZoneId.of("Asia/Ho_Chi_Minh");
    public ProfileService(JdbcTemplate db,ObjectMapper json) {this.db=db;this.json=json;}
    public record View(UUID id,int version,ProfileInput profile,boolean adult,Metrics metrics,
        String ruleVersion,String formulaName,String sourceReference,Instant calculatedAt) {}
    private record Rule(UUID id,String version,String formula,String source,JsonNode parameters) {}
    private JsonNode parse(String value) {
        try{return json.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid nutrition rule",e);}
    }
    private int lockCurrentVersion(Account account) {
        String status=db.queryForObject("SELECT status FROM users WHERE id=? FOR UPDATE",String.class,account.id());
        if(!"ACTIVE".equals(status)) throw new ApiException(401,"UNAUTHENTICATED","Tài khoản không hoạt động.");
        return db.queryForObject("SELECT COALESCE(MAX(version),0) FROM nutrition_profiles WHERE user_id=? AND is_current",Integer.class,account.id());
    }
    private void checkVersion(int expected,int current) {
        if(expected!=current) throw new ApiException(409,"PROFILE_CONFLICT","Hồ sơ đã thay đổi ở một phiên khác. Tải lại hồ sơ trước khi tiếp tục.");
    }
    @Transactional
    public void delete(Account account,int expectedVersion) {
        int current=lockCurrentVersion(account);
        if(current==0) throw new ApiException(404,"PROFILE_NOT_FOUND","Bạn chưa có hồ sơ dinh dưỡng.");
        checkVersion(expectedVersion,current);
        db.update("UPDATE nutrition_profiles SET is_current=false WHERE user_id=? AND is_current",account.id());
    }
    @Transactional
    public View save(Account account,ProfileInput p) {
        LocalDate today=LocalDate.now(ZONE);
        int age=Period.between(p.birthDate(),today).getYears();
        if(p.birthDate().isAfter(today) || age>120) throw ApiException.field("birthDate","Ngày sinh cần trong quá khứ và tuổi không quá 120.");
        checkVersion(p.expectedVersion(),lockCurrentVersion(account));
        int nextVersion=db.queryForObject("SELECT COALESCE(MAX(version),0)+1 FROM nutrition_profiles WHERE user_id=?",Integer.class,account.id());
        Rule rule=db.queryForObject("SELECT * FROM nutrition_rule_versions WHERE version='phase1-v1'",(rs,n)->new Rule(
            rs.getObject("id",UUID.class),rs.getString("version"),rs.getString("formula_name"),rs.getString("source_reference"),parse(rs.getString("parameters_json"))));
        Metrics metrics=age>=18?NutritionCalculator.calculate(p,age,rule.parameters()):null;
        UUID profileId=UUID.randomUUID();
        db.update("UPDATE nutrition_profiles SET is_current=false WHERE user_id=? AND is_current",account.id());
        db.update("""
          INSERT INTO nutrition_profiles(id,user_id,version,is_current,birth_date,sex_for_formula,height_cm,weight_kg,
            activity_level,goal)
          VALUES(?,?,?,true,?,?,?,?,?,?)
          """,profileId,account.id(),nextVersion,p.birthDate(),p.sexForFormula(),p.heightCm(),p.weightKg(),p.activityLevel(),p.goal());
        if(metrics!=null) db.update("""
            INSERT INTO calculation_snapshots(id,rule_version_id,profile_id,calculated_at,age_years,bmi,estimated_bmr,tdee,target_kcal,protein_g,carbohydrate_g,fat_g,water_ml)
            VALUES(?,?,?,now(),?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),rule.id(),profileId,age,metrics.bmi(),metrics.estimatedBmr(),metrics.tdee(),metrics.targetKcal(),metrics.proteinG(),metrics.carbohydrateG(),metrics.fatG(),metrics.waterMl());
        return get(account);
    }
    @Transactional(readOnly=true)
    public View get(Account account) {
        var rows=db.query("""
            SELECT p.id,p.version,p.birth_date,p.sex_for_formula,p.height_cm,p.weight_kg,p.activity_level,p.goal,
              c.bmi,c.estimated_bmr,c.tdee,c.target_kcal,c.protein_g,c.carbohydrate_g,c.fat_g,c.water_ml,c.calculated_at,
              r.version AS rule_version,r.formula_name,r.source_reference
            FROM nutrition_profiles p
            LEFT JOIN calculation_snapshots c ON c.profile_id=p.id LEFT JOIN nutrition_rule_versions r ON r.id=c.rule_version_id
            WHERE p.user_id=? AND p.is_current
            """,(rs,n)->{
                int version=rs.getInt("version");
                LocalDate birthDate=rs.getObject("birth_date",LocalDate.class);
                ProfileInput input=new ProfileInput(version,birthDate,rs.getString("sex_for_formula"),rs.getBigDecimal("height_cm"),
                    rs.getBigDecimal("weight_kg"),rs.getString("activity_level"),rs.getString("goal"));
                Timestamp calculatedAt=rs.getTimestamp("calculated_at");
                Metrics metrics=calculatedAt==null?null:new Metrics(rs.getBigDecimal("bmi"),rs.getBigDecimal("estimated_bmr"),rs.getBigDecimal("tdee"),rs.getBigDecimal("target_kcal"),rs.getBigDecimal("protein_g"),rs.getBigDecimal("carbohydrate_g"),rs.getBigDecimal("fat_g"),rs.getBigDecimal("water_ml"));
                boolean adult=Period.between(birthDate,LocalDate.now(ZONE)).getYears()>=18;
                return new View(rs.getObject("id",UUID.class),version,input,adult,metrics,
                    rs.getString("rule_version"),rs.getString("formula_name"),rs.getString("source_reference"),calculatedAt==null?null:calculatedAt.toInstant());
            },account.id());
        if(rows.isEmpty()) throw new ApiException(404,"PROFILE_NOT_FOUND","Bạn chưa có hồ sơ dinh dưỡng.");
        return rows.getFirst();
    }
}
