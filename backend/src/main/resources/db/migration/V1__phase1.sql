CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE users (
 id UUID PRIMARY KEY, email VARCHAR(255) NOT NULL UNIQUE,
 password_hash VARCHAR(255) NOT NULL, display_name VARCHAR(255) NOT NULL,
 role VARCHAR(32) NOT NULL CHECK (role IN ('USER','ADMIN')),
 status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE','LOCKED')),
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
 CHECK (email = lower(trim(email)))
);
CREATE TABLE sessions (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 token_hash VARCHAR(64) NOT NULL UNIQUE, expires_at TIMESTAMPTZ NOT NULL,
 revoked_at TIMESTAMPTZ, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user_idx ON sessions(user_id);
CREATE TABLE nutrition_profiles (
 id UUID PRIMARY KEY, user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 version INTEGER NOT NULL CHECK (version > 0), is_current BOOLEAN NOT NULL,
 birth_date DATE NOT NULL, sex_for_formula VARCHAR(32) NOT NULL CHECK (sex_for_formula IN ('MALE','FEMALE')),
 height_cm NUMERIC(12,3) NOT NULL CHECK (height_cm > 0), weight_kg NUMERIC(12,3) NOT NULL CHECK (weight_kg > 0),
 activity_level VARCHAR(32) NOT NULL CHECK (activity_level IN ('SEDENTARY','LIGHT','MODERATE','ACTIVE','VERY_ACTIVE')),
 goal VARCHAR(32) NOT NULL CHECK (goal IN ('MAINTAIN','LOSE','GAIN','MUSCLE')),
 self_declared_healthy BOOLEAN NOT NULL, pregnant BOOLEAN NOT NULL, breastfeeding BOOLEAN NOT NULL,
 requires_therapeutic_diet BOOLEAN NOT NULL, unresolved_allergies VARCHAR(1000) NOT NULL DEFAULT '',
 created_at TIMESTAMPTZ NOT NULL DEFAULT now(), UNIQUE(user_id,version)
);
CREATE UNIQUE INDEX one_current_profile ON nutrition_profiles(user_id) WHERE is_current;
CREATE TABLE dietary_preferences (
 id UUID PRIMARY KEY, profile_id UUID NOT NULL UNIQUE REFERENCES nutrition_profiles(id) ON DELETE CASCADE,
 diet_type VARCHAR(32) NOT NULL CHECK (diet_type IN ('OMNIVORE','VEGETARIAN','VEGAN')),
 daily_budget NUMERIC(12,3) NOT NULL CHECK (daily_budget >= 0),
 meals_per_day INTEGER NOT NULL CHECK (meals_per_day BETWEEN 3 AND 5)
);
-- Phase 1 uses ingredient identities for exclusions. Nutrient/recipe tables come in Phase 2.
CREATE TABLE data_sources (
 id UUID PRIMARY KEY, title VARCHAR(255) NOT NULL, publisher VARCHAR(255) NOT NULL,
 reference TEXT NOT NULL, accessed_at TIMESTAMPTZ NOT NULL
);
CREATE TABLE ingredients (
 id UUID PRIMARY KEY, source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE RESTRICT,
 ingredient_key UUID NOT NULL, version INTEGER NOT NULL CHECK(version > 0),
 name VARCHAR(255) NOT NULL, category VARCHAR(100) NOT NULL, preparation_state VARCHAR(100) NOT NULL,
 allergen_flags JSONB NOT NULL DEFAULT '[]', price_per100g NUMERIC(12,3) NOT NULL CHECK(price_per100g >= 0),
 price_recorded_at TIMESTAMPTZ NOT NULL, status VARCHAR(32) NOT NULL CHECK(status IN ('ACTIVE','INACTIVE')),
 UNIQUE(ingredient_key,version)
);
CREATE TABLE allergies (
 id UUID PRIMARY KEY, ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
 profile_id UUID NOT NULL REFERENCES nutrition_profiles(id) ON DELETE CASCADE,
 severity VARCHAR(32) NOT NULL DEFAULT 'UNSPECIFIED' CHECK(severity='UNSPECIFIED'), UNIQUE(profile_id,ingredient_id)
);
CREATE TABLE profile_disliked_foods (
 preference_id UUID NOT NULL REFERENCES dietary_preferences(id) ON DELETE CASCADE,
 ingredient_id UUID NOT NULL REFERENCES ingredients(id) ON DELETE RESTRICT,
 PRIMARY KEY(preference_id,ingredient_id)
);
CREATE TABLE nutrition_rule_versions (
 id UUID PRIMARY KEY, version VARCHAR(50) NOT NULL UNIQUE, formula_name VARCHAR(255) NOT NULL,
 parameters_json JSONB NOT NULL, source_reference TEXT NOT NULL
);
CREATE TABLE calculation_snapshots (
 id UUID PRIMARY KEY, rule_version_id UUID NOT NULL REFERENCES nutrition_rule_versions(id) ON DELETE RESTRICT,
 profile_id UUID NOT NULL UNIQUE REFERENCES nutrition_profiles(id) ON DELETE CASCADE,
 calculated_at TIMESTAMPTZ NOT NULL, age_years INTEGER NOT NULL,
 bmi NUMERIC(12,3) NOT NULL, estimated_bmr NUMERIC(12,3) NOT NULL, tdee NUMERIC(12,3) NOT NULL,
 target_kcal NUMERIC(12,3) NOT NULL, protein_g NUMERIC(12,3) NOT NULL,
 carbohydrate_g NUMERIC(12,3) NOT NULL, fat_g NUMERIC(12,3) NOT NULL, water_ml NUMERIC(12,3) NOT NULL
);
INSERT INTO nutrition_rule_versions VALUES (
 '10000000-0000-0000-0000-000000000001','phase1-v1','Mifflin–St Jeor (REE/RMR ước tính)',
 '{"activity":{"SEDENTARY":1.2,"LIGHT":1.375,"MODERATE":1.55,"ACTIVE":1.725,"VERY_ACTIVE":1.9},"goal":{"MAINTAIN":1,"LOSE":0.9,"GAIN":1.1,"MUSCLE":1.1},"sex":{"MALE":5,"FEMALE":-161},"weightCoefficient":10,"heightCoefficient":6.25,"ageCoefficient":5,"proteinRatio":0.2,"carbRatio":0.5,"fatRatio":0.3,"muscleProteinRatio":0.25,"muscleCarbRatio":0.45,"proteinKcalPerGram":4,"carbKcalPerGram":4,"fatKcalPerGram":9,"waterMlPerKg":30}',
 'Mifflin et al., 1990, doi:10.1093/ajcn/51.2.241; https://pubmed.ncbi.nlm.nih.gov/2305711/. Công thức năng lượng nghỉ có nguồn nghiên cứu. Hệ số vận động, mục tiêu, macro và nước là tham số do nhóm chọn cho đồ án, không phải khuyến nghị y khoa.'
);
