package vn.nutrition;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.nutrition.calculation.NutritionCalculator;
import vn.nutrition.profile.ProfileInput;
import java.math.BigDecimal;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class NutritionCalculatorTest {
    @Test void US04_TC04_female_muscle_uses_correct_sex_goal_and_macro_parameters() throws Exception {
        var p=new ProfileInput(0,LocalDate.of(1996,1,1),"FEMALE",new BigDecimal("160"),new BigDecimal("60"),"MODERATE","MUSCLE");
        var rule=new ObjectMapper().readTree("""
          {"activity":{"MODERATE":1.55},"goal":{"MUSCLE":1.1},"sex":{"FEMALE":-161},"weightCoefficient":10,"heightCoefficient":6.25,"ageCoefficient":5,
           "muscleProteinRatio":0.25,"muscleCarbRatio":0.45,"fatRatio":0.3,"proteinKcalPerGram":4,"carbKcalPerGram":4,"fatKcalPerGram":9,"waterMlPerKg":30}
          """);
        var m=NutritionCalculator.calculate(p,30,rule);
        assertThat(m).isNotNull();
        assertThat(m.bmi()).isEqualByComparingTo("23.438");
        assertThat(m.estimatedBmr()).isEqualByComparingTo("1289");
        assertThat(m.tdee()).isEqualByComparingTo("1997.95");
        assertThat(m.targetKcal()).isEqualByComparingTo("2197.745");
        assertThat(m.proteinG()).isEqualByComparingTo("137.359");
        assertThat(m.carbohydrateG()).isEqualByComparingTo("247.246");
        assertThat(m.fatG()).isEqualByComparingTo("73.258");
        assertThat(m.waterMl()).isEqualByComparingTo("1800");
        // The selected rule, rather than a hardcoded constant, must control the output.
        ((com.fasterxml.jackson.databind.node.ObjectNode)rule).put("waterMlPerKg",35);
        assertThat(NutritionCalculator.calculate(p,30,rule).waterMl()).isEqualByComparingTo("2100");
    }
}
