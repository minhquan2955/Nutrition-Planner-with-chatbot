# Danh sách và đặc tả 30 bảng cơ sở dữ liệu

Hệ thống website tư vấn dinh dưỡng cá nhân hóa tích hợp Chatbot RAG.

Đây là thiết kế đề xuất dựa trên mô hình lớp đã thống nhất, không phải kết quả trích xuất từ cơ sở dữ liệu đã triển khai. Các kiểu và độ dài dữ liệu là phương án thiết kế để sử dụng trong báo cáo và làm cơ sở triển khai PostgreSQL.

## Quy ước

- PK: khóa chính; FK: khóa ngoại; UQ: giá trị duy nhất.
- Các trường mặc định NOT NULL, trừ những trường ghi NULL. Trường thuộc khóa chính luôn NOT NULL.
- TIMESTAMPTZ dùng cho thời điểm; DATE dùng cho ngày sinh và ngày thực đơn. Thời gian được xử lý theo UTC; ngày nghiệp vụ theo Asia/Ho_Chi_Minh.
- Mỗi hàng hồ sơ, nguyên liệu, công thức, thực đơn và tài liệu RAG đại diện cho một phiên bản cụ thể.
- Các lớp User và Admin dùng chung bảng users, phân biệt bằng role. Vector embedding lưu trong rag_chunks.

## Danh mục bảng

| STT | Tên bảng | Nội dung |
|---|---|---|
| 1 | users | Tài khoản |
| 2 | sessions | Phiên đăng nhập |
| 3 | nutrition_profiles | Phiên bản hồ sơ dinh dưỡng |
| 4 | dietary_preferences | Điều kiện và sở thích ăn uống |
| 5 | allergies | Nguyên liệu cần loại trừ do dị ứng |
| 6 | profile_disliked_foods | Thực phẩm không thích |
| 7 | nutrition_rule_versions | Bộ quy tắc tính toán |
| 8 | calculation_snapshots | Kết quả tính toán dinh dưỡng |
| 9 | data_sources | Nguồn dữ liệu thực phẩm |
| 10 | ingredients | Nguyên liệu và thực phẩm |
| 11 | nutrient_values | Thành phần dinh dưỡng |
| 12 | recipes | Công thức món ăn |
| 13 | recipe_ingredients | Nguyên liệu của công thức |
| 14 | meal_plan_jobs | Tác vụ tạo thực đơn |
| 15 | meal_plans | Phiên bản thực đơn |
| 16 | meal_plan_days | Ngày trong thực đơn |
| 17 | meals | Bữa ăn |
| 18 | meal_plan_items | Món ăn trong bữa |
| 19 | progress_records | Nhật ký cân nặng |
| 20 | water_entries | Nhật ký uống nước |
| 21 | meal_consumptions | Nhật ký món đã dùng |
| 22 | rag_documents | Phiên bản tài liệu RAG |
| 23 | rag_chunks | Đoạn tri thức và embedding |
| 24 | chat_conversations | Hội thoại |
| 25 | chat_messages | Tin nhắn |
| 26 | chat_sources | Nguồn trích dẫn |
| 27 | chat_feedback | Phản hồi của người dùng |
| 28 | shared_context_snapshots | Ngữ cảnh được chia sẻ |
| 29 | shared_message_snapshots | Tin nhắn được chia sẻ |
| 30 | audit_logs | Nhật ký hệ thống |

## 1. users — Tài khoản

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| email | VARCHAR(255) | UQ; NOT NULL | Email đăng nhập; chuẩn hóa trước khi lưu. |
| password_hash | VARCHAR(255) | NOT NULL | Mật khẩu đã băm. |
| display_name | VARCHAR(255) | NOT NULL | Tên hiển thị. |
| role | VARCHAR(32) | NOT NULL | Vai trò tài khoản: USER hoặc ADMIN. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |

**Ràng buộc:** Chuẩn hóa email trước khi lưu. role chỉ nhận USER hoặc ADMIN.

## 2. sessions — Phiên đăng nhập

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| expires_at | TIMESTAMPTZ | NOT NULL | Thời điểm hết hạn phiên. |
| revoked_at | TIMESTAMPTZ | NULL | Thời điểm thu hồi phiên hoặc quyền chia sẻ. |

## 3. nutrition_profiles — Phiên bản hồ sơ dinh dưỡng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| version | INTEGER | NOT NULL | Mã hoặc số phiên bản. |
| is_current | BOOLEAN | NOT NULL | Xác định hồ sơ hiện tại. |
| birth_date | DATE | NOT NULL | Ngày sinh. |
| sex_for_formula | VARCHAR(32) | NOT NULL | Nhóm giới tính dùng trong công thức tính. |
| height_cm | NUMERIC(12,3) | NOT NULL | Chiều cao, đơn vị cm. |
| weight_kg | NUMERIC(12,3) | NOT NULL | Cân nặng, đơn vị kg. |
| activity_level | VARCHAR(32) | NOT NULL | Mức độ vận động. |
| goal | VARCHAR(32) | NOT NULL | Mục tiêu duy trì, giảm cân, tăng cân hoặc tăng cơ. |
| pregnant | BOOLEAN | NOT NULL | Thông tin tự khai báo về mang thai. |
| breastfeeding | BOOLEAN | NOT NULL | Thông tin tự khai báo về cho con bú. |
| requires_therapeutic_diet | BOOLEAN | NOT NULL | Có cần chế độ ăn điều trị hay không. |

**Ràng buộc:** UNIQUE(user_id, version). Chỉ mục UNIQUE(user_id) WHERE is_current = true để mỗi tài khoản có tối đa một hồ sơ hiện tại. Chiều cao và cân nặng phải lớn hơn 0.

## 4. dietary_preferences — Điều kiện và sở thích ăn uống

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| profile_id | UUID | FK → nutrition_profiles.id; UQ; NOT NULL | Phiên bản hồ sơ dinh dưỡng được tham chiếu. |
| diet_type | VARCHAR(32) | NOT NULL | Chế độ ăn thông thường, chay có trứng/sữa hoặc thuần chay. |
| daily_budget | NUMERIC(12,3) | NOT NULL | Ngân sách ăn uống mỗi ngày, đơn vị VND. |
| meals_per_day | INTEGER | NOT NULL | Số bữa mỗi ngày, từ 3 đến 5. |

**Ràng buộc:** profile_id là duy nhất. meals_per_day từ 3 đến 5; daily_budget không âm.

## 5. allergies — Nguyên liệu cần loại trừ do dị ứng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| ingredient_id | UUID | FK → ingredients.id; NOT NULL | Phiên bản nguyên liệu được tham chiếu. |
| profile_id | UUID | FK → nutrition_profiles.id; NOT NULL | Phiên bản hồ sơ dinh dưỡng được tham chiếu. |
| severity | VARCHAR(32) | NOT NULL | Mức độ dị ứng do người dùng khai báo. |

**Ràng buộc:** UNIQUE(profile_id, ingredient_id). Khi kiểm tra các phiên bản khác nhau của cùng nguyên liệu, đối chiếu theo ingredient_key.

## 6. profile_disliked_foods — Thực phẩm không thích

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| preference_id | UUID | PK ghép; FK → dietary_preferences.id; NOT NULL | Bản ghi điều kiện và sở thích ăn uống. |
| ingredient_id | UUID | PK ghép; FK → ingredients.id; NOT NULL | Phiên bản nguyên liệu được tham chiếu. |

**Ràng buộc:** Khóa chính ghép (preference_id, ingredient_id). Khi loại trừ thực phẩm qua các phiên bản, đối chiếu theo ingredient_key.

## 7. nutrition_rule_versions — Bộ quy tắc tính toán

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| version | VARCHAR(50) | UQ; NOT NULL | Mã hoặc số phiên bản. |
| formula_name | VARCHAR(255) | NOT NULL | Tên công thức tính toán. |
| parameters_json | JSONB | NOT NULL | Hệ số vận động, điều chỉnh kcal, tỷ lệ macro và các tham số khác. |
| source_reference | TEXT | NOT NULL | Nguồn tham khảo và căn cứ của bộ quy tắc. |

**Ràng buộc:** version là duy nhất. Các bộ quy tắc đã được sử dụng cần được lưu để đối chiếu lịch sử.

## 8. calculation_snapshots — Kết quả tính toán dinh dưỡng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| rule_version_id | UUID | FK → nutrition_rule_versions.id; NOT NULL | Phiên bản bộ quy tắc được sử dụng. |
| profile_id | UUID | FK → nutrition_profiles.id; NOT NULL | Phiên bản hồ sơ dinh dưỡng được tham chiếu. |
| calculated_at | TIMESTAMPTZ | NOT NULL | Thời điểm tính toán. |
| bmi | NUMERIC(12,3) | NOT NULL | Chỉ số BMI. |
| estimated_bmr | NUMERIC(12,3) | NOT NULL | Năng lượng nghỉ ước tính, kcal/ngày. |
| tdee | NUMERIC(12,3) | NOT NULL | Tổng năng lượng tiêu hao ước tính, kcal/ngày. |
| target_kcal | NUMERIC(12,3) | NOT NULL | Kcal mục tiêu mỗi ngày. |
| protein_g | NUMERIC(12,3) | NOT NULL | Lượng protein, đơn vị g. |
| carbohydrate_g | NUMERIC(12,3) | NOT NULL | Lượng carbohydrate, đơn vị g. |
| fat_g | NUMERIC(12,3) | NOT NULL | Lượng chất béo, đơn vị g. |
| water_ml | NUMERIC(12,3) | NOT NULL | Lượng nước tham khảo, ml/ngày. |

## 9. data_sources — Nguồn dữ liệu thực phẩm

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| title | VARCHAR(255) | NOT NULL | Tiêu đề hoặc tên nguồn. |
| publisher | VARCHAR(255) | NOT NULL | Tác giả hoặc đơn vị phát hành. |
| reference | TEXT | NOT NULL | URL hoặc thông tin xuất bản. |
| accessed_at | TIMESTAMPTZ | NOT NULL | Thời điểm truy cập, đối chiếu nguồn. |

## 10. ingredients — Nguyên liệu và thực phẩm

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| source_id | UUID | FK → data_sources.id; NOT NULL | Nguồn cung cấp dữ liệu. |
| ingredient_key | UUID | NOT NULL | Định danh ổn định của nguyên liệu qua các phiên bản. |
| version | INTEGER | NOT NULL | Mã hoặc số phiên bản. |
| name | VARCHAR(255) | NOT NULL | Tên nguyên liệu hoặc món ăn. |
| category | VARCHAR(100) | NOT NULL | Nhóm thực phẩm. |
| preparation_state | VARCHAR(100) | NOT NULL | Trạng thái sống, chín hoặc cách chế biến. |
| allergen_flags | JSONB | NOT NULL | Danh sách mã dị nguyên được kiểm soát; có thể là mảng rỗng. |
| price_per100g | NUMERIC(12,3) | NOT NULL | Giá ước tính trên 100 g nguyên liệu. |
| price_recorded_at | TIMESTAMPTZ | NOT NULL | Thời điểm ghi nhận giá. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |

**Ràng buộc:** UNIQUE(ingredient_key, version). allergen_flags cụ thể hóa thuộc tính dị nguyên trong biểu đồ lớp; chưa tách thành bảng danh mục riêng. Giá không âm.

## 11. nutrient_values — Thành phần dinh dưỡng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| ingredient_id | UUID | FK → ingredients.id; UQ; NOT NULL | Phiên bản nguyên liệu được tham chiếu. |
| basis_grams | NUMERIC(12,3) | NOT NULL | Khối lượng làm cơ sở tính, bằng 100 g. |
| kcal | NUMERIC(12,3) | NOT NULL | Năng lượng, đơn vị kcal. Giá trị trên 100 g nguyên liệu. |
| protein_g | NUMERIC(12,3) | NOT NULL | Lượng protein, đơn vị g. Giá trị trên 100 g nguyên liệu. |
| carbohydrate_g | NUMERIC(12,3) | NOT NULL | Lượng carbohydrate, đơn vị g. Giá trị trên 100 g nguyên liệu. |
| fat_g | NUMERIC(12,3) | NOT NULL | Lượng chất béo, đơn vị g. Giá trị trên 100 g nguyên liệu. |

**Ràng buộc:** ingredient_id là duy nhất. basis_grams = 100; các giá trị dinh dưỡng không âm.

## 12. recipes — Công thức món ăn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| recipe_key | UUID | NOT NULL | Định danh ổn định của món ăn qua các phiên bản. |
| version | INTEGER | NOT NULL | Mã hoặc số phiên bản. |
| name | VARCHAR(255) | NOT NULL | Tên nguyên liệu hoặc món ăn. |
| instructions | TEXT | NOT NULL | Hướng dẫn chế biến. |
| diet_tags | JSONB | NOT NULL | Danh sách chế độ ăn phù hợp. |
| standard_servings | NUMERIC(12,3) | NOT NULL | Số khẩu phần chuẩn của toàn bộ công thức. |
| finished_weight_g | NUMERIC(12,3) | NOT NULL | Khối lượng thành phẩm, đơn vị g. |
| estimated_cost | NUMERIC(12,3) | NOT NULL | Chi phí ước tính của toàn bộ công thức. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |

**Ràng buộc:** UNIQUE(recipe_key, version). standard_servings và finished_weight_g lớn hơn 0; estimated_cost không âm.

## 13. recipe_ingredients — Nguyên liệu của công thức

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| ingredient_id | UUID | FK → ingredients.id; NOT NULL | Phiên bản nguyên liệu được tham chiếu. |
| recipe_id | UUID | FK → recipes.id; NOT NULL | Phiên bản công thức món ăn được tham chiếu. |
| quantity_g | NUMERIC(12,3) | NOT NULL | Khối lượng nguyên liệu trong toàn bộ công thức, đơn vị g. |

**Ràng buộc:** UNIQUE(recipe_id, ingredient_id). quantity_g > 0.

## 14. meal_plan_jobs — Tác vụ tạo thực đơn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| calculation_id | UUID | FK → calculation_snapshots.id; NOT NULL | Kết quả tính toán dinh dưỡng được sử dụng. |
| profile_id | UUID | FK → nutrition_profiles.id; NOT NULL | Phiên bản hồ sơ dinh dưỡng được tham chiếu. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |
| start_date | DATE | NOT NULL | Ngày bắt đầu thực đơn được yêu cầu. |
| duration_days | INTEGER | NOT NULL | Số ngày, chỉ nhận 1 hoặc 7. |
| attempt_count | INTEGER | NOT NULL | Số lượt đã gọi AI, từ 0 đến 3. |
| requested_at | TIMESTAMPTZ | NOT NULL | Thời điểm gửi yêu cầu. |
| deadline_at | TIMESTAMPTZ | NOT NULL | Thời hạn xử lý tác vụ. |
| model_name | VARCHAR(100) | NOT NULL | Model AI được sử dụng. |
| prompt_version | VARCHAR(50) | NOT NULL | Phiên bản prompt. |
| error_code | VARCHAR(100) | NULL | Mã lỗi khi xử lý thất bại. |

**Ràng buộc:** status thuộc QUEUED, RUNNING, SUCCEEDED, FAILED, TIMED_OUT. duration_days chỉ bằng 1 hoặc 7; attempt_count từ 0 đến 3. Tác vụ hết hạn không được ghi kết quả đến muộn.

## 15. meal_plans — Phiên bản thực đơn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| previous_version_id | UUID | FK → meal_plans.id; UQ; NULL | Phiên bản thực đơn trước đó. |
| calculation_id | UUID | FK → calculation_snapshots.id; NOT NULL | Kết quả tính toán dinh dưỡng được sử dụng. |
| profile_id | UUID | FK → nutrition_profiles.id; NOT NULL | Phiên bản hồ sơ dinh dưỡng được tham chiếu. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| generation_job_id | UUID | FK → meal_plan_jobs.id; UQ; NULL | Tác vụ tạo ra phiên bản thực đơn. |
| plan_key | UUID | NOT NULL | Định danh chung của các phiên bản cùng thực đơn. |
| version | INTEGER | NOT NULL | Mã hoặc số phiên bản. |
| created_at | TIMESTAMPTZ | NOT NULL | Thời điểm tạo. |
| profile_snapshot_json | JSONB | NOT NULL | Bản chụp hồ sơ và ràng buộc đã sử dụng để tạo thực đơn. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |

**Ràng buộc:** UNIQUE(plan_key, version). generation_job_id và previous_version_id có UNIQUE riêng. Phiên bản trước phải cùng plan_key, cùng chủ sở hữu và không tạo chu trình. Chỉ lưu kết quả tạo thực đơn khi tất cả các ngày đều hợp lệ.

## 16. meal_plan_days — Ngày trong thực đơn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| meal_plan_id | UUID | FK → meal_plans.id; NOT NULL | Phiên bản thực đơn chứa ngày này. |
| date | DATE | NOT NULL | Ngày thực hiện thực đơn. |
| target_kcal | NUMERIC(12,3) | NOT NULL | Kcal mục tiêu mỗi ngày. |
| daily_budget | NUMERIC(12,3) | NOT NULL | Ngân sách ăn uống mỗi ngày, đơn vị VND. |
| is_applied | BOOLEAN | NOT NULL | Ngày thực đơn có đang được chọn áp dụng hay không. |

**Ràng buộc:** UNIQUE(meal_plan_id, date). Với cùng người dùng và ngày, chỉ một kế hoạch được chọn áp dụng.

## 17. meals — Bữa ăn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| plan_day_id | UUID | FK → meal_plan_days.id; NOT NULL | Ngày thực đơn chứa bữa ăn. |
| meal_slot | VARCHAR(50) | NOT NULL | Loại bữa: sáng, trưa, tối hoặc bữa phụ. |
| sequence_number | INTEGER | NOT NULL | Thứ tự bữa trong ngày. |

**Ràng buộc:** UNIQUE(plan_day_id, sequence_number). Thực đơn hợp lệ có từ 3 đến 5 bữa mỗi ngày.

## 18. meal_plan_items — Món ăn trong bữa

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| recipe_id | UUID | FK → recipes.id; NOT NULL | Phiên bản công thức món ăn được tham chiếu. |
| meal_id | UUID | FK → meals.id; NOT NULL | Bữa ăn chứa món này. |
| serving_multiplier | NUMERIC(12,3) | NOT NULL | Số khẩu phần chuẩn được sử dụng. |
| recipe_name_snapshot | VARCHAR(255) | NOT NULL | Tên món tại thời điểm tạo thực đơn. |
| ingredient_snapshot_json | JSONB | NOT NULL | Bản chụp nguyên liệu và định lượng. |
| kcal | NUMERIC(12,3) | NOT NULL | Năng lượng, đơn vị kcal. Tổng của món theo khẩu phần đã chọn. |
| protein_g | NUMERIC(12,3) | NOT NULL | Lượng protein, đơn vị g. Tổng của món theo khẩu phần đã chọn. |
| carbohydrate_g | NUMERIC(12,3) | NOT NULL | Lượng carbohydrate, đơn vị g. Tổng của món theo khẩu phần đã chọn. |
| fat_g | NUMERIC(12,3) | NOT NULL | Lượng chất béo, đơn vị g. Tổng của món theo khẩu phần đã chọn. |
| estimated_cost | NUMERIC(12,3) | NOT NULL | Chi phí của món theo khẩu phần đã chọn. |

**Ràng buộc:** Dinh dưỡng và chi phí là tổng của món theo khẩu phần được chọn. Giữ nguyên bản chụp lịch sử, không cập nhật hồi tố từ danh mục mới.

## 19. progress_records — Nhật ký cân nặng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| profile_id | UUID | FK → nutrition_profiles.id; NOT NULL | Phiên bản hồ sơ dinh dưỡng được tham chiếu. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| recorded_at | TIMESTAMPTZ | NOT NULL | Thời điểm ghi nhận. |
| weight_kg | NUMERIC(12,3) | NOT NULL | Cân nặng, đơn vị kg. |

**Ràng buộc:** weight_kg > 0. Hồ sơ được tham chiếu phải thuộc đúng user_id.

## 20. water_entries — Nhật ký uống nước

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| recorded_at | TIMESTAMPTZ | NOT NULL | Thời điểm ghi nhận. |
| amount_ml | NUMERIC(12,3) | NOT NULL | Lượng nước đã uống, đơn vị ml. |

**Ràng buộc:** amount_ml > 0.

## 21. meal_consumptions — Nhật ký món đã dùng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| recipe_id | UUID | FK → recipes.id; NOT NULL | Phiên bản công thức món ăn được tham chiếu. |
| meal_plan_item_id | UUID | FK → meal_plan_items.id; NULL | Món trong thực đơn; để trống nếu ăn ngoài kế hoạch. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| consumed_at | TIMESTAMPTZ | NOT NULL | Thời điểm dùng món. |
| actual_servings | NUMERIC(12,3) | NOT NULL | Số khẩu phần thực tế đã dùng. |
| recipe_snapshot_json | JSONB | NOT NULL | Bản chụp món và thông tin dinh dưỡng. |
| recorded_kcal | NUMERIC(12,3) | NOT NULL | Kcal tính theo khẩu phần thực tế đã ghi nhận. |

**Ràng buộc:** actual_servings > 0; recorded_kcal không âm. Nếu có meal_plan_item_id, bản ghi đó phải thuộc người dùng và phù hợp với món được ghi nhận.

## 22. rag_documents — Phiên bản tài liệu RAG

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| document_key | UUID | NOT NULL | Định danh chung của các phiên bản cùng tài liệu. |
| version | VARCHAR(50) | NOT NULL | Mã hoặc số phiên bản. |
| title | VARCHAR(255) | NOT NULL | Tiêu đề hoặc tên nguồn. |
| publisher | VARCHAR(255) | NOT NULL | Tác giả hoặc đơn vị phát hành. |
| publication_year | INTEGER | NULL | Năm xuất bản nếu xác định được. |
| source_url | TEXT | NULL | Đường dẫn nguồn nếu có. |
| file_path | TEXT | NOT NULL | Vị trí tệp trong kho lưu trữ nội bộ. |
| checksum | VARCHAR(128) | NOT NULL | Giá trị kiểm tra nội dung tệp. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |

**Ràng buộc:** UNIQUE(document_key, version). Không kích hoạt tài liệu chưa xử lý và kiểm duyệt thành công.

## 23. rag_chunks — Đoạn tri thức và embedding

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| document_id | UUID | FK → rag_documents.id; NOT NULL | Phiên bản tài liệu chứa đoạn tri thức. |
| chunk_index | INTEGER | NOT NULL | Thứ tự đoạn trong tài liệu. |
| content | TEXT | NOT NULL | Nội dung văn bản. |
| page_start | INTEGER | NOT NULL | Trang bắt đầu. |
| page_end | INTEGER | NOT NULL | Trang kết thúc. |
| embedding | VECTOR(d) | NOT NULL | Vector của đoạn; số chiều d theo model được chọn. |
| embedding_model | VARCHAR(100) | NOT NULL | Model tạo embedding. |
| embedding_dimensions | INTEGER | NOT NULL | Số chiều vector. |

**Ràng buộc:** UNIQUE(document_id, chunk_index). page_end >= page_start. VECTOR(d) là ký hiệu thiết kế; thay d bằng số chiều thực tế khi triển khai và dùng model/index tương thích.

## 24. chat_conversations — Hội thoại

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| title | VARCHAR(255) | NOT NULL | Tiêu đề hoặc tên nguồn. |
| created_at | TIMESTAMPTZ | NOT NULL | Thời điểm tạo. |

## 25. chat_messages — Tin nhắn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| conversation_id | UUID | FK → chat_conversations.id; NOT NULL | Hội thoại chứa tin nhắn. |
| role | VARCHAR(32) | NOT NULL | Vai trò của tài khoản hoặc người gửi tin nhắn. |
| content | TEXT | NOT NULL | Nội dung văn bản. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |
| created_at | TIMESTAMPTZ | NOT NULL | Thời điểm tạo. |
| model_name | VARCHAR(100) | NULL | Model AI được sử dụng. |
| prompt_version | VARCHAR(50) | NULL | Phiên bản prompt. |
| response_time_ms | BIGINT | NULL | Thời gian xử lý phản hồi, đơn vị ms. |
| input_tokens | INTEGER | NULL | Số token đầu vào. |
| output_tokens | INTEGER | NULL | Số token đầu ra. |

**Ràng buộc:** Các trường xử lý AI có thể NULL đối với tin nhắn người dùng. Thời gian phản hồi và số token, khi có giá trị, không âm.

## 26. chat_sources — Nguồn trích dẫn

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| chunk_id | UUID | FK → rag_chunks.id; NOT NULL | Đoạn tài liệu được trích dẫn. |
| message_id | UUID | FK → chat_messages.id; NOT NULL | Tin nhắn hoặc câu trả lời được tham chiếu. |
| citation_index | INTEGER | NOT NULL | Thứ tự nguồn trong câu trả lời. |
| relevance_score | NUMERIC(12,3) | NOT NULL | Điểm liên quan do bộ truy xuất cung cấp. |
| quoted_text_snapshot | TEXT | NOT NULL | Bản chụp đoạn nội dung được trích dẫn. |

**Ràng buộc:** UNIQUE(message_id, citation_index). Trích dẫn phải thuộc nguồn thực sự đã truy xuất.

## 27. chat_feedback — Phản hồi của người dùng

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| assigned_admin_id | UUID | FK → users.id; NULL | Quản trị viên được phân công xử lý. |
| message_id | UUID | FK → chat_messages.id; NULL | Tin nhắn hoặc câu trả lời được tham chiếu. |
| user_id | UUID | FK → users.id; NOT NULL | Tài khoản sở hữu dữ liệu. |
| rating | INTEGER | NULL | Mức đánh giá. |
| reason | TEXT | NULL | Nội dung phản hồi. |
| status | VARCHAR(32) | NOT NULL | Trạng thái của bản ghi. |
| resolution | TEXT | NULL | Kết quả xử lý. |
| created_at | TIMESTAMPTZ | NOT NULL | Thời điểm tạo. |
| resolved_at | TIMESTAMPTZ | NULL | Thời điểm hoàn tất xử lý. |

**Ràng buộc:** Khi tạo phải có mức đánh giá hoặc nhận xét. assigned_admin_id, khi có giá trị, phải thuộc tài khoản ADMIN. message_id có thể NULL sau khi tin nhắn gốc bị xóa.

## 28. shared_context_snapshots — Ngữ cảnh được chia sẻ

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| feedback_id | UUID | FK → chat_feedback.id; UQ; NOT NULL | Phản hồi chứa ngữ cảnh chia sẻ. |
| profile_snapshot_json | JSONB | NULL | Phần hồ sơ được người dùng đồng ý chia sẻ. |
| consented_at | TIMESTAMPTZ | NOT NULL | Thời điểm đồng ý chia sẻ. |
| revoked_at | TIMESTAMPTZ | NULL | Thời điểm thu hồi phiên hoặc quyền chia sẻ. |

**Ràng buộc:** feedback_id là duy nhất. Chỉ lưu phần hồ sơ được chấp thuận; revoked_at phải chặn các lần đọc ngữ cảnh tiếp theo.

## 29. shared_message_snapshots — Tin nhắn được chia sẻ

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| original_message_id | UUID | FK → chat_messages.id; NULL | Tin nhắn gốc, có thể đã bị xóa. |
| context_id | UUID | FK → shared_context_snapshots.id; NOT NULL | Bản chụp ngữ cảnh được tham chiếu. |
| role | VARCHAR(32) | NOT NULL | Vai trò của tài khoản hoặc người gửi tin nhắn. |
| content | TEXT | NOT NULL | Nội dung văn bản. |
| original_created_at | TIMESTAMPTZ | NOT NULL | Thời điểm tạo tin nhắn gốc. |

**Ràng buộc:** Chỉ lưu tin nhắn được người dùng chấp thuận. Việc xem bản chụp vẫn phải kiểm tra quyền chia sẻ còn hiệu lực.

## 30. audit_logs — Nhật ký hệ thống

| Trường | Kiểu dữ liệu | Khóa và tính bắt buộc | Mô tả |
|---|---|---|---|
| id | UUID | PK; NOT NULL | Mã định danh bản ghi. |
| context_id | UUID | FK → shared_context_snapshots.id; NULL | Bản chụp ngữ cảnh được tham chiếu. |
| actor_id | UUID | FK → users.id; NULL | Tài khoản thực hiện thao tác. |
| action | VARCHAR(100) | NOT NULL | Hành động được ghi nhận. |
| target_type | VARCHAR(100) | NOT NULL | Loại đối tượng bị tác động. |
| target_id | UUID | NOT NULL | Mã đối tượng bị tác động; được diễn giải theo target_type. |
| purpose | TEXT | NOT NULL | Mục đích truy cập hoặc thực hiện thao tác. |
| occurred_at | TIMESTAMPTZ | NOT NULL | Thời điểm phát sinh thao tác. |

**Ràng buộc:** target_id không phải khóa ngoại đồng thời tới mọi bảng. Không lưu mật khẩu, khóa API hoặc hồ sơ/hội thoại nguyên văn trong nhật ký.

## Ràng buộc liên bảng và lưu lịch sử

- Hồ sơ, kết quả tính toán, tác vụ và thực đơn phải thuộc cùng người dùng; calculation_id phải tương ứng với profile_id. Có thể kiểm soát bằng khóa ngoại ghép phù hợp, trigger hoặc kiểm tra trong giao dịch nghiệp vụ.
- Thực đơn hoàn chỉnh có đúng một hoặc bảy ngày; mỗi ngày có ba đến năm bữa và ít nhất một món mỗi bữa. Cần kiểm tra số lượng hàng con và các ràng buộc kcal, ngân sách, dị ứng trước khi hoàn tất giao dịch.
- Không sửa hồi tố nội dung các phiên bản đã dùng làm căn cứ lịch sử. Ưu tiên ngừng sử dụng/thu hồi dữ liệu danh mục thay cho xóa mất các bản ghi được tham chiếu.
- Chính sách xóa cần được xác định cho từng FK. Những tham chiếu tùy chọn tới tin nhắn gốc hoặc tài khoản có thể được đặt NULL khi cần giữ lịch sử phù hợp với chính sách dữ liệu.
- Chỉ Admin được phân công mới được xử lý phản hồi theo quyền được cấp; việc đọc ngữ cảnh cần có quyền chia sẻ còn hiệu lực và được ghi audit.
- Phương án này chưa dùng ba bảng allergens, ingredient_allergens, profile_allergens. Dị nguyên của nguyên liệu được lưu trong allergen_flags; dị ứng với nguyên liệu cụ thể lưu trong allergies.
