package vn.nutrition.calculation;

import com.fasterxml.jackson.databind.JsonNode;
import vn.nutrition.profile.ProfileInput;
import java.math.BigDecimal;
import java.math.RoundingMode;
import vn.nutrition.common.ApiException;

public class NutritionCalculator {
    public record Metrics(BigDecimal bmi,BigDecimal estimatedBmr,BigDecimal tdee,BigDecimal targetKcal,
        BigDecimal proteinG,BigDecimal carbohydrateG,BigDecimal fatG,BigDecimal waterMl) {}
    private static BigDecimal value(JsonNode rule,String key) { return rule.required(key).decimalValue(); }
    private static BigDecimal round(BigDecimal number) { return number.setScale(3,RoundingMode.HALF_UP); }
    private static BigDecimal divide(BigDecimal left,BigDecimal right) { return left.divide(right,12,RoundingMode.HALF_UP); }
    public static Metrics calculate(ProfileInput p,int age,JsonNode rule) {
        BigDecimal heightM=p.heightCm().movePointLeft(2);
        BigDecimal bmi=divide(p.weightKg(),heightM.multiply(heightM));
        if(bmi.compareTo(new BigDecimal("999999999.999"))>0)
            throw ApiException.field("heightCm","Chiều cao quá nhỏ để tính chỉ số. Vui lòng kiểm tra lại đơn vị cm.");
        BigDecimal ree=p.weightKg().multiply(value(rule,"weightCoefficient"))
            .add(p.heightCm().multiply(value(rule,"heightCoefficient")))
            .subtract(BigDecimal.valueOf(age).multiply(value(rule,"ageCoefficient")))
            .add(value(rule.required("sex"),p.sexForFormula()));
        if(ree.signum()<=0) throw ApiException.field("weightKg","Số đo không phù hợp miền tính toán. Vui lòng kiểm tra chiều cao và cân nặng.");
        BigDecimal tdee=ree.multiply(value(rule.required("activity"),p.activityLevel()));
        BigDecimal kcal=tdee.multiply(value(rule.required("goal"),p.goal()));
        boolean muscle=p.goal().equals("MUSCLE");
        return new Metrics(round(bmi),round(ree),round(tdee),round(kcal),
            round(divide(kcal.multiply(value(rule,muscle?"muscleProteinRatio":"proteinRatio")),value(rule,"proteinKcalPerGram"))),
            round(divide(kcal.multiply(value(rule,muscle?"muscleCarbRatio":"carbRatio")),value(rule,"carbKcalPerGram"))),
            round(divide(kcal.multiply(value(rule,"fatRatio")),value(rule,"fatKcalPerGram"))),
            round(p.weightKg().multiply(value(rule,"waterMlPerKg"))));
    }
}
