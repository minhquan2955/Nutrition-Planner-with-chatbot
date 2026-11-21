ALTER TABLE nutrition_profiles
    ALTER COLUMN self_declared_healthy DROP NOT NULL,
    ALTER COLUMN pregnant DROP NOT NULL,
    ALTER COLUMN breastfeeding DROP NOT NULL,
    ALTER COLUMN requires_therapeutic_diet DROP NOT NULL;
