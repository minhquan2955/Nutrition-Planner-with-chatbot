package vn.nutrition.profile;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ProfileInput(
    @NotNull @Min(0) Integer expectedVersion,
    @NotNull(message="Vui lòng nhập ngày sinh.") LocalDate birthDate,
    @NotNull @Pattern(regexp="MALE|FEMALE",message="Vui lòng chọn nhóm dùng cho công thức.") String sexForFormula,
    @NotNull @DecimalMin(value="0",inclusive=false,message="Chiều cao phải lớn hơn 0.") @DecimalMax(value="300",message="Chiều cao vượt miền tính toán 300 cm.") @Digits(integer=3,fraction=3) BigDecimal heightCm,
    @NotNull @DecimalMin(value="0",inclusive=false,message="Cân nặng phải lớn hơn 0.") @DecimalMax(value="500",message="Cân nặng vượt miền tính toán 500 kg.") @Digits(integer=3,fraction=3) BigDecimal weightKg,
    @NotNull @Pattern(regexp="SEDENTARY|LIGHT|MODERATE|ACTIVE|VERY_ACTIVE") String activityLevel,
    @NotNull @Pattern(regexp="MAINTAIN|LOSE|GAIN|MUSCLE") String goal,
    @NotNull Boolean selfDeclaredHealthy,@NotNull Boolean pregnant,@NotNull Boolean breastfeeding,@NotNull Boolean requiresTherapeuticDiet,
    @NotNull @Pattern(regexp="OMNIVORE|VEGETARIAN|VEGAN") String dietType,
    @NotNull @DecimalMin(value="0",message="Ngân sách không được âm.") @Digits(integer=9,fraction=3) BigDecimal dailyBudget,
    @NotNull @Min(value=3,message="Số bữa từ 3 đến 5.") @Max(value=5,message="Số bữa từ 3 đến 5.") Integer mealsPerDay,
    @NotNull @Size(max=100) List<@NotNull UUID> allergyIngredientIds,
    @NotNull @Size(max=100) List<@NotNull UUID> dislikedIngredientIds,
    @NotNull @Size(max=1000) String unresolvedAllergies
) {}
