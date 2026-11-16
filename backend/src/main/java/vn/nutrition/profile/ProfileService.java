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
    public record View(UUID id,int version,ProfileInput profile,boolean eligible,List<String> eligibilityReasons,
        boolean needsAllergyClarification,Metrics metrics,String ruleVersion,String formulaName,String sourceReference,Instant calculatedAt) {}
    private record Rule(UUID id,String version,String formula,String source,JsonNode parameters) {}
    private JsonNode parse(String value) {
        try{return json.readTree(value);}catch(JsonProcessingException e){throw new IllegalStateException("Invalid nutrition rule",e);}
    }
    private List<String> reasons(ProfileInput p) {
        List<String> reasons=new ArrayList<>();
        if(Period.between(p.birthDate(),LocalDate.now(ZONE)).getYears()<18) reasons.add("Chưa đủ 18 tuổi.");
        if(!p.selfDeclaredHealthy()) reasons.add("Chưa tự khai báo khỏe mạnh.");
        if(p.pregnant()) reasons.add("Đang mang thai.");
        if(p.breastfeeding()) reasons.add("Đang cho con bú.");
        if(p.requiresTherapeuticDiet()) reasons.add("Cần chế độ ăn điều trị.");
        return reasons;
    }
    private void validateIngredients(List<UUID> ids,String field) {
        if(new HashSet<>(ids).size()!=ids.size()) throw ApiException.field(field,"Nguyên liệu bị lặp.");
        for(UUID id:ids) {
            Integer count=db.queryForObject("SELECT count(*) FROM ingredients WHERE id=?",Integer.class,id);
            if(count==null || count!=1) throw ApiException.field(field,"Nguyên liệu chưa được xác định. Vui lòng chọn lại.");
        }
    }
    @Transactional
    public View save(Account account,ProfileInput p) {
        LocalDate today=LocalDate.now(ZONE);
        int age=Period.between(p.birthDate(),today).getYears();
        if(p.birthDate().isAfter(today) || age>120) throw ApiException.field("birthDate","Ngày sinh cần trong quá khứ và tuổi không quá 120.");
        String status=db.queryForObject("SELECT status FROM users WHERE id=? FOR UPDATE",String.class,account.id());
        if(!"ACTIVE".equals(status)) throw new ApiException(401,"UNAUTHENTICATED","Tài khoản không hoạt động.");
        var versions=db.queryForList("SELECT version FROM nutrition_profiles WHERE user_id=? AND is_current",Integer.class,account.id());
        int current=versions.isEmpty()?0:versions.getFirst();
        if(p.expectedVersion()!=current) throw new ApiException(409,"PROFILE_CONFLICT","Hồ sơ đã thay đổi ở một phiên khác. Tải lại hồ sơ trước khi lưu.");
        validateIngredients(p.allergyIngredientIds(),"allergyIngredientIds");
        validateIngredients(p.dislikedIngredientIds(),"dislikedIngredientIds");
        Rule rule=db.queryForObject("SELECT * FROM nutrition_rule_versions WHERE version='phase1-v1'",(rs,n)->new Rule(
            rs.getObject("id",UUID.class),rs.getString("version"),rs.getString("formula_name"),rs.getString("source_reference"),parse(rs.getString("parameters_json"))));
        List<String> reasons=reasons(p);
        Metrics metrics=reasons.isEmpty()?NutritionCalculator.calculate(p,age,rule.parameters()):null;
        UUID profileId=UUID.randomUUID(),preferenceId=UUID.randomUUID();
        db.update("UPDATE nutrition_profiles SET is_current=false WHERE user_id=? AND is_current",account.id());
        db.update("""
          INSERT INTO nutrition_profiles(id,user_id,version,is_current,birth_date,sex_for_formula,height_cm,weight_kg,
            activity_level,goal,self_declared_healthy,pregnant,breastfeeding,requires_therapeutic_diet,unresolved_allergies)
          VALUES(?,?,?,true,?,?,?,?,?,?,?,?,?,?,?)
          """,profileId,account.id(),current+1,p.birthDate(),p.sexForFormula(),p.heightCm(),p.weightKg(),p.activityLevel(),p.goal(),
            p.selfDeclaredHealthy(),p.pregnant(),p.breastfeeding(),p.requiresTherapeuticDiet(),p.unresolvedAllergies().strip());
        db.update("INSERT INTO dietary_preferences(id,profile_id,diet_type,daily_budget,meals_per_day) VALUES(?,?,?,?,?)",preferenceId,profileId,p.dietType(),p.dailyBudget(),p.mealsPerDay());
        for(UUID id:p.allergyIngredientIds()) db.update("INSERT INTO allergies(id,ingredient_id,profile_id,severity) VALUES(?,?,?,'UNSPECIFIED')",UUID.randomUUID(),id,profileId);
        for(UUID id:p.dislikedIngredientIds()) db.update("INSERT INTO profile_disliked_foods(preference_id,ingredient_id) VALUES(?,?)",preferenceId,id);
        if(metrics!=null) db.update("""
            INSERT INTO calculation_snapshots(id,rule_version_id,profile_id,calculated_at,age_years,bmi,estimated_bmr,tdee,target_kcal,protein_g,carbohydrate_g,fat_g,water_ml)
            VALUES(?,?,?,now(),?,?,?,?,?,?,?,?,?)
            """,UUID.randomUUID(),rule.id(),profileId,age,metrics.bmi(),metrics.estimatedBmr(),metrics.tdee(),metrics.targetKcal(),metrics.proteinG(),metrics.carbohydrateG(),metrics.fatG(),metrics.waterMl());
        return get(account);
    }
    @Transactional(readOnly=true)
    public View get(Account account) {
        var rows=db.query("""
            SELECT p.*,d.id AS preference_id,d.diet_type,d.daily_budget,d.meals_per_day,
              c.bmi,c.estimated_bmr,c.tdee,c.target_kcal,c.protein_g,c.carbohydrate_g,c.fat_g,c.water_ml,c.calculated_at,
              r.version AS rule_version,r.formula_name,r.source_reference
            FROM nutrition_profiles p JOIN dietary_preferences d ON d.profile_id=p.id
            LEFT JOIN calculation_snapshots c ON c.profile_id=p.id LEFT JOIN nutrition_rule_versions r ON r.id=c.rule_version_id
            WHERE p.user_id=? AND p.is_current
            """,(rs,n)->{
                UUID id=rs.getObject("id",UUID.class),preferenceId=rs.getObject("preference_id",UUID.class);
                var allergies=db.queryForList("SELECT ingredient_id FROM allergies WHERE profile_id=? ORDER BY ingredient_id",UUID.class,id);
                var dislikes=db.queryForList("SELECT ingredient_id FROM profile_disliked_foods WHERE preference_id=? ORDER BY ingredient_id",UUID.class,preferenceId);
                ProfileInput input=new ProfileInput(rs.getInt("version"),rs.getObject("birth_date",LocalDate.class),rs.getString("sex_for_formula"),rs.getBigDecimal("height_cm"),rs.getBigDecimal("weight_kg"),rs.getString("activity_level"),rs.getString("goal"),rs.getBoolean("self_declared_healthy"),rs.getBoolean("pregnant"),rs.getBoolean("breastfeeding"),rs.getBoolean("requires_therapeutic_diet"),rs.getString("diet_type"),rs.getBigDecimal("daily_budget"),rs.getInt("meals_per_day"),allergies,dislikes,rs.getString("unresolved_allergies"));
                Timestamp calculatedAt=rs.getTimestamp("calculated_at");
                Metrics metrics=calculatedAt==null?null:new Metrics(rs.getBigDecimal("bmi"),rs.getBigDecimal("estimated_bmr"),rs.getBigDecimal("tdee"),rs.getBigDecimal("target_kcal"),rs.getBigDecimal("protein_g"),rs.getBigDecimal("carbohydrate_g"),rs.getBigDecimal("fat_g"),rs.getBigDecimal("water_ml"));
                var reasons=reasons(input);
                return new View(id,rs.getInt("version"),input,reasons.isEmpty(),reasons,!input.unresolvedAllergies().isBlank(),
                    reasons.isEmpty()?metrics:null,rs.getString("rule_version"),rs.getString("formula_name"),rs.getString("source_reference"),calculatedAt==null?null:calculatedAt.toInstant());
            },account.id());
        if(rows.isEmpty()) throw new ApiException(404,"PROFILE_NOT_FOUND","Bạn chưa có hồ sơ dinh dưỡng.");
        return rows.getFirst();
    }
}
