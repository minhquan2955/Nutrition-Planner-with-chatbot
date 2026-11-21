-- A source may have nutrition values without a price.
ALTER TABLE ingredients ALTER COLUMN price_per100g DROP NOT NULL;
ALTER TABLE ingredients ALTER COLUMN price_recorded_at DROP NOT NULL;
ALTER TABLE ingredients ADD CHECK ((price_per100g IS NULL) = (price_recorded_at IS NULL));

CREATE TABLE nutrient_values (
 ingredient_id UUID PRIMARY KEY REFERENCES ingredients(id) ON DELETE RESTRICT,
 basis_grams NUMERIC(12,3) NOT NULL DEFAULT 100 CHECK (basis_grams = 100),
 kcal NUMERIC(12,3) NOT NULL CHECK (kcal >= 0),
 protein_g NUMERIC(12,3) NOT NULL CHECK (protein_g >= 0),
 carbohydrate_g NUMERIC(12,3) NOT NULL CHECK (carbohydrate_g >= 0),
 fat_g NUMERIC(12,3) NOT NULL CHECK (fat_g >= 0),
 source_locator TEXT NOT NULL
);
CREATE TABLE recipes (
 id UUID PRIMARY KEY,
 recipe_key UUID NOT NULL,
 version INTEGER NOT NULL CHECK (version > 0),
 name VARCHAR(255) NOT NULL,
 instructions TEXT NOT NULL,
 standard_servings NUMERIC(12,3) NOT NULL CHECK (standard_servings > 0),
 source_id UUID NOT NULL REFERENCES data_sources(id) ON DELETE RESTRICT,
 source_locator TEXT NOT NULL,
 status VARCHAR(32) NOT NULL CHECK (status IN ('ACTIVE', 'INACTIVE')),
 UNIQUE (recipe_key, version)
);
CREATE TABLE recipe_ingredients (
 recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
 ingredient_id UUID NOT NULL REFERENCES nutrient_values(ingredient_id) ON DELETE RESTRICT,
 quantity_g NUMERIC(12,3) NOT NULL CHECK (quantity_g > 0),
 PRIMARY KEY (recipe_id, ingredient_id)
);

ALTER TABLE nutrition_profiles ADD UNIQUE (id, user_id);
ALTER TABLE calculation_snapshots ADD UNIQUE (id, profile_id);
CREATE TABLE meal_plan_jobs (
 id UUID PRIMARY KEY,
 user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
 profile_id UUID NOT NULL,
 calculation_id UUID NOT NULL,
 start_date DATE NOT NULL,
 status VARCHAR(32) NOT NULL CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
 attempt_count INTEGER NOT NULL DEFAULT 0 CHECK (attempt_count BETWEEN 0 AND 3),
 requested_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
 deadline_at TIMESTAMPTZ NOT NULL,
 finished_at TIMESTAMPTZ,
 model_name VARCHAR(100) NOT NULL,
 prompt_version VARCHAR(50) NOT NULL,
 catalog_version VARCHAR(50),
 input_tokens BIGINT NOT NULL DEFAULT 0 CHECK (input_tokens >= 0),
 output_tokens BIGINT NOT NULL DEFAULT 0 CHECK (output_tokens >= 0),
 error_code VARCHAR(100),
 FOREIGN KEY (profile_id, user_id) REFERENCES nutrition_profiles(id, user_id) ON DELETE CASCADE,
 FOREIGN KEY (calculation_id, profile_id) REFERENCES calculation_snapshots(id, profile_id) ON DELETE CASCADE
);
CREATE UNIQUE INDEX one_pending_meal_plan_job ON meal_plan_jobs(user_id) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX latest_meal_plan_job ON meal_plan_jobs(user_id, requested_at DESC, id DESC);
CREATE INDEX queued_meal_plan_job ON meal_plan_jobs(requested_at, id) WHERE status = 'QUEUED';

CREATE TABLE meal_plans (
 id UUID PRIMARY KEY,
 generation_job_id UUID NOT NULL UNIQUE REFERENCES meal_plan_jobs(id) ON DELETE CASCADE,
 explanation VARCHAR(280) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE meal_plan_days (
 id UUID PRIMARY KEY,
 meal_plan_id UUID NOT NULL UNIQUE REFERENCES meal_plans(id) ON DELETE CASCADE,
 date DATE NOT NULL,
 target_kcal NUMERIC(12,3) NOT NULL CHECK (target_kcal > 0)
);
CREATE TABLE meals (
 id UUID PRIMARY KEY,
 plan_day_id UUID NOT NULL REFERENCES meal_plan_days(id) ON DELETE CASCADE,
 meal_slot VARCHAR(50) NOT NULL CHECK (meal_slot IN ('BREAKFAST', 'LUNCH', 'DINNER')),
 sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 3),
 UNIQUE (plan_day_id, meal_slot),
 UNIQUE (plan_day_id, sequence_number)
);
CREATE TABLE meal_plan_items (
 meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
 recipe_id UUID NOT NULL REFERENCES recipes(id) ON DELETE RESTRICT,
 sequence_number INTEGER NOT NULL CHECK (sequence_number BETWEEN 1 AND 4),
 serving_multiplier NUMERIC(12,3) NOT NULL CHECK (serving_multiplier BETWEEN 0.25 AND 3),
 recipe_name_snapshot VARCHAR(255) NOT NULL,
 ingredient_snapshot_json JSONB NOT NULL,
 kcal NUMERIC(12,3) NOT NULL CHECK (kcal >= 0),
 PRIMARY KEY (meal_id, recipe_id),
 UNIQUE (meal_id, sequence_number)
);
