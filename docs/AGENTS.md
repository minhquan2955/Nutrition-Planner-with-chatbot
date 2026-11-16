# AGENTS.md — Website tư vấn dinh dưỡng cá nhân hóa tích hợp Chatbot RAG

Đồ án Thực tập cơ sở chuyên ngành, nhóm 9. Thời gian 16/11–25/12/2026. Giao diện tiếng Việt. Đơn vị: kg, cm, g, ml, kcal; ngân sách VND/ngày.

## 1. Cách dùng file này

- Đọc hết file trước khi sửa code.
- Khi các nguồn mâu thuẫn, ưu tiên: (1) yêu cầu của người dùng trong phiên làm việc, (2) file này, (3) `docs/PLAN.md` (đặc tả), (4) `docs/DB_TABLES.md` (thiết kế 30 bảng), (5) `docs/KE_HOACH_6_PHASE.docx` (lịch và user story).
- Gặp mâu thuẫn hoặc thiếu thông tin: xem mục 15. Không tự quyết; hỏi và ghi lại.
- Không đánh dấu một chức năng là "đã làm" nếu chưa chạy và chưa có test.

## 2. Mục tiêu và phạm vi

Xây hệ thống chạy được, demo được, đánh giá được: người dùng khai báo hồ sơ → hệ thống tính chỉ số → AI đề xuất thực đơn → Backend kiểm tra và lưu → người dùng hỏi Chatbot RAG có dẫn nguồn → xác nhận đổi món.

**Đối tượng cá nhân hóa:** từ 18 tuổi, tự khai báo khỏe mạnh, không mang thai/cho con bú, không cần chế độ ăn điều trị. Người ngoài nhóm này chỉ xem kiến thức chung; không tạo thực đơn, không đặt mục tiêu dinh dưỡng cá nhân.

**Ngoài phạm vi:** chẩn đoán, kê đơn, điều trị, tư vấn trực tiếp với bác sĩ, thanh toán, giao đồ ăn, app di động riêng, nhận dạng món bằng ảnh, OCR tài liệu scan, đăng nhập mạng xã hội, khôi phục mật khẩu qua email.

## 3. Tác nhân

| Mã | Tác nhân | Ghi chú |
|---|---|---|
| ACT-01 | Khách | Chưa xác thực, không phải role lưu trong DB |
| ACT-02 | Người dùng (`USER`) | Quản lý dữ liệu của mình |
| ACT-03 | Quản trị viên (`ADMIN`) | Tài khoản, danh mục, tài liệu RAG, phản hồi, vận hành |
| ACT-04 | OpenAI | Chỉ embedding và sinh nội dung theo yêu cầu của Backend. Không truy cập DB, không đổi thực đơn |

Chatbot, bộ kiểm tra thực đơn và PostgreSQL là thành phần bên trong, không phải tác nhân. Không thêm vai trò chuyên gia dinh dưỡng.

## 4. Kiến trúc và công nghệ

Ba tầng, **một Backend chia module**. Không tách nhiều dịch vụ độc lập.

| Tầng | Công nghệ |
|---|---|
| Presentation | Next.js, TypeScript, Tailwind CSS. Giao diện User và Admin |
| Logic | Java, Spring Boot, RESTful API, Spring Security (session phía server) |
| Data | PostgreSQL + pgvector. PDF lưu ở vùng không truy cập công khai |
| Tích hợp ngoài | OpenAI qua adapter riêng. Model sinh và embedding cấu hình bằng biến môi trường |

- Frontend và API cùng origin qua reverse proxy.
- Worker chạy trong ứng dụng Backend; trạng thái tác vụ lưu PostgreSQL.
- Chạy bằng Docker Compose.
- Module Backend: xác thực, hồ sơ, tính toán, thực phẩm/món ăn, thực đơn, tiến trình, Chatbot/RAG, quản trị.
- Không hardcode khóa API hoặc tên model trong nghiệp vụ.
- Đổi model embedding hoặc số chiều vector: tạo lại chỉ mục. Không trộn vector không tương thích.
- Thời điểm lưu theo UTC. Ngày thực đơn và nhật ký theo `Asia/Ho_Chi_Minh`.

## 5. Lịch và mức ưu tiên

Mốc: demo toàn luồng **11/12**, chốt code **18/12**, nộp **25/12/2026**.

| Phase | Ngày | Kết quả |
|---|---|---|
| 1 | 16–20/11 | Tài khoản, hồ sơ, chỉ số chạy từ trình duyệt |
| 2 | 23–27/11 | AI đề xuất thực đơn 1 ngày, Backend kiểm tra và lưu |
| 3 | 30/11–04/12 | Chatbot trả lời có nguồn và số trang từ PDF đã duyệt |
| 4 | 07–11/12 | Giải thích món, xác nhận thay món, xem phiên bản cũ |
| 5 | 14–18/12 | Chốt phạm vi, kiểm tra lỗi, cài thử trên máy thứ hai |
| 6 | 21–25/12 | Đo thực nghiệm, tổng duyệt, báo cáo, nộp |

**P0:** tài khoản, hồ sơ, chỉ số, dữ liệu món cần thiết, thực đơn 1 ngày, nạp và duyệt PDF, hỏi đáp có trích dẫn.
**P1:** nối Chatbot với thực đơn, xác nhận đổi món, xem lịch sử.
**P2:** thực đơn 7 ngày, nhật ký tiến trình, phản hồi, quản trị mở rộng.

Chỉ làm P2 khi P0 và P1 chạy trọn vẹn. Luồng demo/bảo vệ: đăng nhập → hồ sơ → tạo thực đơn → hỏi Chatbot → xem nguồn → xác nhận đổi món → mở lại phiên bản cũ.

User story: US01 đăng nhập; US02 hồ sơ; US03 danh mục (P0 cho dữ liệu mẫu, CRUD đầy đủ P2); US04 chỉ số; US05 nạp/duyệt PDF; US06 thực đơn 1 ngày; US07 hỏi đáp có nguồn; US08 lịch sử (P1) và thực đơn 7 ngày (P2); US09 giải thích và đổi món; US10 nhật ký (P2); US11 phản hồi (P2); US12 quyền riêng tư và khóa tài khoản (P2); US13 theo dõi vận hành (P2).

## 6. Hồ sơ và công thức tính

- Một tài khoản có một hồ sơ hiện tại và nhiều phiên bản lịch sử. Không ghi đè lịch sử khi hồ sơ đổi.
- Chế độ ăn ban đầu: ăn thông thường, chay có trứng/sữa, thuần chay.
- Dị ứng và thực phẩm loại trừ phải ánh xạ tới nguyên liệu chuẩn hóa. Dị ứng chưa ánh xạ được: yêu cầu làm rõ trước khi tạo thực đơn. Không bỏ qua.
- Thiếu hoặc sai miền giá trị: báo lỗi tại đúng trường.

**Công thức (có nguồn nghiên cứu):**
- BMI = kg / m².
- Năng lượng nghỉ (Mifflin–St Jeor) = `10×kg + 6,25×cm − 5×tuổi + hệ số`; hệ số `+5` hoặc `−161` theo `sex_for_formula`. Giao diện ghi rõ là **ước tính**. Báo cáo phân biệt "BMR" trong đề cương với REE/RMR của công thức gốc.

**Tham số mặc định do nhóm chọn (không phải khuyến nghị y khoa), lưu trong `nutrition_rule_versions`:**

| Tham số | Giá trị |
|---|---|
| Hệ số vận động | 1,2 / 1,375 / 1,55 / 1,725 / 1,9 (5 mức) |
| TDEE | năng lượng nghỉ × hệ số vận động |
| Kcal mục tiêu | duy trì = TDEE; giảm cân = 90% TDEE; tăng cân/tăng cơ = 110% TDEE |
| P/C/F | 20/50/30; tăng cơ 25/45/30 |
| Quy đổi | protein, carb 4 kcal/g; fat 9 kcal/g |
| Nước | 30 ml/kg/ngày |

Cân nặng mới trong nhật ký cập nhật hồ sơ và tạo phiên bản chỉ số mới. Thực đơn đã lưu giữ mục tiêu cũ; mục tiêu mới chỉ áp dụng khi tạo hoặc chỉnh kế hoạch.

## 7. Dữ liệu thực phẩm và bộ kiểm tra thực đơn

- Số liệu dinh dưỡng trên **100 g phần ăn được**, kèm trạng thái sống/chín/chế biến, nguồn, ngày cập nhật.
- Không trộn số liệu sống/chín. Không tự đổi ml sang g khi thiếu hệ số.
- Không bịa thành phần dinh dưỡng hoặc nguồn để đủ số lượng. Thiếu dữ liệu thì ghi nhận phần thiếu.
- Giá minh họa phải ghi rõ là giá giả lập.
- Dữ liệu demo tối thiểu: 100 thực phẩm, 50 món, 10 tài liệu có nguồn xác minh.

**Backend là nguồn quyết định** tổng kcal, macro, dị ứng, chi phí. Không dùng tổng do AI tự khai báo.

**Ràng buộc bắt buộc cho từng ngày (`MealPlanValidator`):**
1. Món và nguyên liệu tồn tại, đang được phép dùng.
2. Không chứa dị nguyên hoặc thực phẩm người dùng loại trừ.
3. Phù hợp chế độ ăn.
4. Đúng số bữa (3–5).
5. Chi phí tính toán không vượt ngân sách ngày.
6. Tổng kcal lệch không quá ±5% so với kcal mục tiêu của ngày.
7. Khẩu phần hợp lệ: 0,5–3 khẩu phần chuẩn, bước 0,25, trừ khi món có giới hạn riêng.

Ưu tiên mềm (không được dùng làm lý do từ chối thực đơn đã đạt ràng buộc bắt buộc): gần tỷ lệ macro, đa dạng món, ít lặp món trong tuần.

±5% là sai lệch giữa kcal tính từ danh mục và mục tiêu ước tính, không chứng minh độ chính xác sinh lý.

## 8. Luồng AI tạo và điều chỉnh thực đơn

1. Kiểm tra quyền, điều kiện sử dụng, phiên bản hồ sơ.
2. Backend lọc món ứng viên, lấy dinh dưỡng và chi phí.
3. Gửi danh mục ứng viên và ràng buộc cho AI.
4. AI trả ngày, bữa, `recipeId`, số khẩu phần, giải thích ngắn theo cấu trúc cố định (OpenAI Structured Outputs).
5. Backend kiểm tra định dạng, ID, rồi **tính lại** toàn bộ ràng buộc. Structured Outputs không thay thế bước này.
6. Chưa đạt: gửi lỗi cho AI sửa. Tối đa **3 lượt gọi/tác vụ**, gồm lượt đầu.
7. Chỉ lưu khi **tất cả các ngày** đạt.
8. Hết lượt hoặc quá **180 giây**: kết thúc với "chưa tìm được thực đơn đáp ứng yêu cầu". Không tự nới ngân sách, không bỏ dị ứng, không lưu kết quả chưa đạt.

- Tác vụ chạy nền. Tác vụ hết hạn không được ghi kết quả đến muộn.
- Thực đơn tuần không được báo thành công nếu chỉ tạo được một phần.
- Thời lượng thực đơn: 1 hoặc 7 ngày.

**Đổi món/khẩu phần:**
- Kiểm tra lại cả ngày, không chỉ món thay thế.
- Dùng phiên bản để phát hiện sửa đồng thời.
- Hồ sơ/danh mục đổi trong lúc xử lý: kiểm tra lại trước khi lưu.
- Thất bại: giữ nguyên bản trước.
- Lịch sử giữ bản chụp cũ. Thực đơn xung đột dị ứng hiện tại được đánh dấu "không còn phù hợp".
- Áp dụng lại hoặc sao chép thực đơn cũ: luôn kiểm tra theo hồ sơ hiện tại.

## 9. RAG và Chatbot

**Nạp tài liệu:**
- PDF có lớp văn bản, tối đa 20 MB. PDF scan: báo rõ là không đọc được.
- Lưu tên, tổ chức/tác giả, năm, URL (nếu có), phiên bản, checksum.
- Tách theo trang và đoạn: khởi điểm 500 token/đoạn, chồng lấn 100 token.
- Tạo embedding, lưu pgvector. Admin xem kết quả rồi mới kích hoạt.
- Tài liệu bị thu hồi không dùng cho câu trả lời mới (hiệu lực từ lượt hỏi mới). Lịch sử giữ dấu vết nguồn đã dùng.

**Trả lời:**
- Truy xuất tối đa 5 đoạn từ tài liệu đang hoạt động.
- Ngữ cảnh gửi AI: câu hỏi, lịch sử hội thoại giới hạn, phần hồ sơ cần thiết.
- Backend đối chiếu mỗi trích dẫn với đoạn thực sự được truy xuất. Không tự tạo tài liệu tham khảo.
- Không có bằng chứng phù hợp: nói rõ chưa đủ căn cứ.
- Số liệu thực đơn lấy từ Backend. Kiến thức dinh dưỡng lấy từ tài liệu RAG.
- Không chẩn đoán, kê đơn, đưa chế độ ăn điều trị.
- Nội dung tài liệu và tin nhắn không được đổi quyền truy cập hay ra lệnh sửa dữ liệu (chống prompt injection).
- Không gửi AI: tên, email, mật khẩu, toàn bộ lịch sử người dùng.
- Đề xuất thay món qua Chatbot chỉ đổi thực đơn khi người dùng bấm áp dụng và Backend kiểm tra đạt.
- "Có trích dẫn" và "trích dẫn thực sự hỗ trợ nội dung" được đánh giá riêng.

## 10. Quyền riêng tư và quyền Admin

- Backend kiểm tra vai trò và chủ sở hữu ở mọi API dữ liệu riêng. Client không được gửi `userId` để quyết định chủ sở hữu.
- Admin không xem mật khẩu, không duyệt tự do hồ sơ sức khỏe hoặc hội thoại.
- Phản hồi: người dùng xem trước và chọn chia sẻ bản chụp hồ sơ + tin nhắn liên quan. Admin chỉ đọc bản chụp đó. Mỗi lần đọc ghi `audit_logs` (người truy cập, thời gian, mục đích, đối tượng).
- Thu hồi chia sẻ (`revoked_at`) chặn mọi lần đọc sau đó.
- Xóa tài khoản: xóa hoặc ẩn danh dữ liệu cá nhân trong ứng dụng. Log kỹ thuật còn lại không chứa hồ sơ hay nội dung hội thoại.
- Tài khoản bị khóa: phiên đang mở bị chặn ở lần gọi API tiếp theo.
- Không ghi mật khẩu, token, khóa API, hồ sơ hoặc hội thoại nguyên văn vào log chung.

## 11. API

Tiền tố `/api/v1`, tài liệu hóa bằng OpenAPI.

| Nhóm | Trách nhiệm |
|---|---|
| `/auth/*` | Đăng ký, đăng nhập, đăng xuất, đổi mật khẩu |
| `/me/profile`, `/me/metrics` | Hồ sơ và chỉ số của người đang đăng nhập |
| `/foods`, `/recipes` | Tra cứu danh mục |
| `/meal-plan-jobs` | Tạo tác vụ, đọc trạng thái |
| `/meal-plans/*` | Xem, áp dụng, đổi món, điều chỉnh, lịch sử |
| `/progress/*` | Nhật ký và tổng hợp |
| `/conversations/*` | Hội thoại, tin nhắn, nguồn |
| `/feedback/*` | Phản hồi, chia sẻ, thu hồi |
| `/admin/*` | Danh mục, tài liệu, tài khoản, phản hồi |

- Tạo thực đơn trả `202` kèm `jobId`.
- Trạng thái tác vụ: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`. Chỉ `SUCCEEDED` trả `mealPlanId`.
- Lỗi dùng cấu trúc chung: `code`, `message`, `fieldErrors`, `traceId`.
- Màn hình bắt buộc: giới thiệu/xác thực; hồ sơ; tổng quan chỉ số; danh mục món; thực đơn ngày/tuần + lịch sử; tiến trình; Chatbot + nguồn; phản hồi; khu quản trị.
- Giao diện hoạt động ở 360, 768, 1440 px; dùng được bằng bàn phím; có trạng thái tải/rỗng/lỗi.

## 12. Cơ sở dữ liệu (30 bảng, PostgreSQL)

Nguồn đầy đủ: `docs/DB_TABLES.md` (cột, kiểu, ràng buộc). Đây là **thiết kế đề xuất**, không phải trích xuất từ DB đã triển khai.

**Quy ước:** khóa chính UUID. Thời điểm dùng `TIMESTAMPTZ`; ngày sinh và ngày thực đơn dùng `DATE`. Mặc định NOT NULL. Số thực dùng `NUMERIC(12,3)`. Mỗi hàng hồ sơ, nguyên liệu, công thức, thực đơn, tài liệu RAG là **một phiên bản**. User và Admin dùng chung `users`, phân biệt bằng `role`.

| Nhóm | Bảng |
|---|---|
| Tài khoản | `users`, `sessions` |
| Hồ sơ | `nutrition_profiles`, `dietary_preferences`, `allergies`, `profile_disliked_foods` |
| Tính toán | `nutrition_rule_versions`, `calculation_snapshots` |
| Danh mục | `data_sources`, `ingredients`, `nutrient_values`, `recipes`, `recipe_ingredients` |
| Thực đơn | `meal_plan_jobs`, `meal_plans`, `meal_plan_days`, `meals`, `meal_plan_items` |
| Tiến trình | `progress_records`, `water_entries`, `meal_consumptions` |
| RAG | `rag_documents`, `rag_chunks` |
| Hội thoại | `chat_conversations`, `chat_messages`, `chat_sources` |
| Phản hồi, quản trị | `chat_feedback`, `shared_context_snapshots`, `shared_message_snapshots`, `audit_logs` |

**Ràng buộc quan trọng:**
- `nutrition_profiles`: UNIQUE(user_id, version); UNIQUE(user_id) WHERE is_current = true; chiều cao, cân nặng > 0.
- `dietary_preferences`: `profile_id` UNIQUE; `meals_per_day` 3–5; `daily_budget` ≥ 0.
- `allergies`: UNIQUE(profile_id, ingredient_id). `profile_disliked_foods`: PK ghép (preference_id, ingredient_id). Đối chiếu giữa các phiên bản nguyên liệu theo `ingredient_key`.
- `ingredients`: UNIQUE(ingredient_key, version); `allergen_flags` JSONB (mã dị nguyên có kiểm soát, có thể rỗng); giá ≥ 0. Chưa có bảng `allergens`, `ingredient_allergens`, `profile_allergens`.
- `nutrient_values`: `ingredient_id` UNIQUE; `basis_grams` = 100; giá trị ≥ 0.
- `recipes`: UNIQUE(recipe_key, version); `standard_servings`, `finished_weight_g` > 0; `diet_tags` JSONB.
- `recipe_ingredients`: UNIQUE(recipe_id, ingredient_id); `quantity_g` > 0 (cho toàn bộ công thức).
- `meal_plan_jobs`: `duration_days` ∈ {1, 7}; `attempt_count` 0–3; có `deadline_at`, `model_name`, `prompt_version`, `error_code`.
- `meal_plans`: UNIQUE(plan_key, version); `previous_version_id` và `generation_job_id` có UNIQUE riêng; phiên bản trước phải cùng `plan_key`, cùng chủ sở hữu, không chu trình; `profile_snapshot_json` lưu bản chụp hồ sơ và ràng buộc.
- `meal_plan_days`: UNIQUE(meal_plan_id, date); `is_applied`: với cùng người dùng và ngày chỉ một kế hoạch được áp dụng.
- `meals`: UNIQUE(plan_day_id, sequence_number). `meal_plan_items`: lưu `recipe_name_snapshot`, `ingredient_snapshot_json`, kcal/macro/chi phí đã nhân khẩu phần; không cập nhật hồi tố.
- `meal_consumptions`: `meal_plan_item_id` NULL nếu ăn ngoài kế hoạch; lưu `recipe_snapshot_json`.
- `rag_documents`: UNIQUE(document_key, version); không kích hoạt khi chưa xử lý và duyệt thành công.
- `rag_chunks`: UNIQUE(document_id, chunk_index); `page_end` ≥ `page_start`; `embedding` VECTOR(d) với d theo model thật; lưu `embedding_model`, `embedding_dimensions`.
- `chat_messages`: `model_name`, `prompt_version`, `response_time_ms`, `input_tokens`, `output_tokens` có thể NULL (tin nhắn người dùng).
- `chat_sources`: UNIQUE(message_id, citation_index); có `quoted_text_snapshot`; trích dẫn phải thuộc đoạn đã truy xuất.
- `chat_feedback`: có `rating` hoặc `reason` khi tạo; `assigned_admin_id` phải là ADMIN; `message_id` có thể NULL.
- `shared_context_snapshots`: `feedback_id` UNIQUE. `shared_message_snapshots`: chỉ lưu tin nhắn đã được đồng ý.
- `audit_logs`: `target_id` là UUID đa hình (không FK); không lưu mật khẩu, khóa API, hồ sơ/hội thoại nguyên văn.

**Ràng buộc liên bảng:**
- Hồ sơ, kết quả tính, tác vụ, thực đơn phải cùng người dùng; `calculation_id` phải khớp `profile_id`. Kiểm soát bằng FK ghép, trigger hoặc kiểm tra trong giao dịch.
- Thực đơn hoàn chỉnh: đúng 1 hoặc 7 ngày; mỗi ngày 3–5 bữa; mỗi bữa ≥ 1 món. Kiểm tra trước khi commit giao dịch.
- Không sửa hồi tố bản ghi đã dùng làm căn cứ lịch sử. Ngừng sử dụng/thu hồi thay vì xóa bản ghi được tham chiếu.
- Chính sách xóa phải xác định cho từng FK. Tham chiếu tùy chọn tới tin nhắn gốc hoặc tài khoản có thể đặt NULL.

## 13. Yêu cầu phi chức năng (mục tiêu kiểm thử, chưa phải kết quả đạt)

| Mã | Mục tiêu |
|---|---|
| NFR-01 | 100% thực đơn đã lưu qua kiểm tra ràng buộc; kcal từng ngày trong ±5% |
| NFR-02 | Chatbot p95 hoàn tất < 5 giây (100 yêu cầu, 5 đồng thời). Đo riêng thời gian nhận nội dung đầu tiên; không dùng nó thay cho thời gian hoàn tất. Không áp dụng cho thực đơn tuần |
| NFR-03 | API thường p95 < 500 ms với 20 người dùng đồng thời, không gồm AI và xử lý tài liệu |
| NFR-04 | Băm mật khẩu bằng thuật toán phù hợp; cookie HttpOnly, Secure khi HTTPS; chống CSRF; kiểm tra quyền ở mọi API dữ liệu riêng |
| NFR-05 | Khóa API chỉ ở Backend; không ghi dữ liệu nhạy cảm vào log chung |
| NFR-06 | Chatbot timeout 30 giây, cho thử lại; tác vụ thực đơn tối đa 180 giây; không trả thành công giả, không ghi dữ liệu một phần |
| NFR-07 | 100% trích dẫn hiển thị ánh xạ tới nguồn đã truy xuất |
| NFR-08 | Responsive, bàn phím, nhãn form, trạng thái tải/rỗng/lỗi |
| NFR-09 | Giao dịch cho tạo/đổi thực đơn; FK; phiên bản; không ghi đè khi sửa đồng thời |
| NFR-10 | Tách module; migration; OpenAPI; `.env.example`; test tự động |
| NFR-11 | Ghi mã yêu cầu, phiên bản công thức, prompt, model, danh mục, trace ID, thời gian, token |
| NFR-12 | Hướng dẫn sao lưu/khôi phục PostgreSQL; khôi phục thử ít nhất một lần |

## 14. Kiểm thử

- Test tự động dùng **AI giả lập** để tái lập lỗi. Đánh giá thực nghiệm chạy riêng với OpenAI thật.
- Nhóm ca bắt buộc (TC-01…TC-15): xác thực và phân quyền; truy cập chéo tài khoản; hồ sơ thiếu/ngoài phạm vi; đối chiếu BMI/năng lượng nghỉ/TDEE với số tính tay; thực đơn 1/7 ngày; AI trả ID sai/khẩu phần sai/từ chối; ngân sách quá thấp; đổi món vượt kcal/ngân sách và sửa đồng thời; thêm dị ứng sau khi tạo thực đơn; tài liệu scan/thu hồi/kho rỗng; trích dẫn giả và prompt injection; Admin chỉ đọc ngữ cảnh đã chia sẻ; nhật ký tiến trình; timeout/khởi động lại/gửi trùng/kết quả AI đến muộn; responsive, hiệu năng, xóa dữ liệu, khôi phục sao lưu.
- Bốn ca tối thiểu cho `MealPlanValidator`: đạt, sai dị ứng, vượt ngân sách, sai kcal.
- Đánh giá thực: 30 hồ sơ mẫu; 50 câu hỏi RAG (có đáp án, thiếu nguồn, ngoài phạm vi, cố tác động quy tắc); 100 yêu cầu đo thời gian. Công bố model, cấu hình, số đồng thời, phân bố độ trễ.
- Đo hai tỷ lệ riêng: tỷ lệ tạo thành công trên tổng tác vụ, và tỷ lệ thực đơn đã lưu đáp ứng ràng buộc. Không đạt chỉ tiêu bằng cách từ chối mọi yêu cầu khó.
- Bảng truy vết: Yêu cầu → Use case → Module/API → Ca kiểm thử → Bằng chứng → Mục báo cáo. Ví dụ: `FR-06 → UC-Tạo thực đơn → Meal Planning → TC-05/06/07 → kết quả chạy → mục 4.3, 4.6`.

## 15. Chưa chốt (hỏi trước khi làm)

1. **Lớp AI.** `PLAN` mô tả adapter OpenAI nằm trong Spring Boot. Lịch phase 2–3 nhắc "Python, Guardrails/Pydantic" nối OpenAI. Chưa rõ có dịch vụ Python riêng hay không. Ràng buộc đã thống nhất ở cả hai tài liệu: Spring Boot kiểm tra và quyết định lưu; dịch vụ AI không truy cập DB.
2. **Phạm vi so với PLAN.** `PLAN` coi toàn bộ FR-01…FR-19 là phạm vi bàn giao; lịch phase xếp P0/P1/P2 và cho phép dời P2 sau 25/12. Làm theo P0 → P1 → P2.
3. **FR-01 — đã chốt ngày 16/11/2026:** người dùng yêu cầu đăng ký và đổi mật khẩu ngay Phase 1, ngoài đăng nhập/đăng xuất và 2 tài khoản mẫu.
4. **Model embedding và số chiều vector** chưa chọn.
5. **Công cụ migration — đã chốt:** Flyway SQL, build bằng Maven Wrapper, Java 21.
6. **Giá trị Phase 1:** `users.status`: ACTIVE/LOCKED. Định danh nguyên liệu dùng ACTIVE/INACTIVE; mọi dị ứng đều loại trừ, `severity=UNSPECIFIED` (không suy đoán mức độ). Dữ liệu mẫu dùng flag EGG, diet_tags OMNIVORE/VEGETARIAN/VEGAN. Các enum của thực đơn/RAG/chat/phản hồi và danh mục mở rộng vẫn cần chốt ở phase liên quan. Xem `docs/PHASE1.md`.
7. **`meal_plan_days.is_applied`.** Ràng buộc "một kế hoạch áp dụng cho mỗi (người dùng, ngày)" không thể viết thành index đơn trên bảng này vì không có `user_id`. Cần cột phi chuẩn hóa, trigger hoặc kiểm tra trong giao dịch.
8. Cấu trúc thư mục, tên package, lệnh build/test/lint: đã điền ở mục 16.

## 16. Lệnh và cấu trúc repo (điền khi khởi tạo)

```
# Chạy toàn bộ:          docker compose up -d --build (copy .env.example thành .env trước)
# Backend build/test:    cd backend; ./mvnw.cmd verify (Linux/macOS: ./mvnw verify)
# Backend test 1 class:  ./mvnw.cmd test "-Dtest=Phase1ApiTest"
# Frontend:              cd frontend; npm ci; npm run dev
# Frontend checks:       npm run typecheck; npm run lint; npm run build
# Browser tests:         npx playwright install chromium; npm run test:e2e
# Migration:             Flyway tự chạy khi Spring Boot khởi động; backend/src/main/resources/db/migration
# DB cho test:           docker compose up -d db; test dùng schema phase1_test, cổng 5433
# Package Java:          vn.nutrition (auth, profile, calculation, common)
```

Đã tạo: `frontend/`, `backend/`, `infra/`, `scripts/`, `docs/`, `docker-compose.yml`, `.env.example`, `README.md`. Tài liệu gốc giữ tên `PLAN (1).md`, `Danh_sach_30_bang_CSDL.md`, `Ke_hoach_TTCS_6_phase_va_User_Story.docx`. Chi tiết chạy, API, sao lưu/khôi phục xem README.

## 17. Quy tắc làm việc cho agent

- Làm theo phase hiện tại. Không bắt đầu P2 khi P0/P1 chưa đạt.
- Mọi quy tắc nghiệp vụ (kcal, ngân sách, dị ứng) nằm trong Backend, có unit test. Frontend và AI không phải nơi quyết định.
- Mọi thay đổi schema đi qua migration. Không sửa DB bằng tay.
- Không commit `.env`, khóa API, dữ liệu cá nhân thật.
- Test dùng AI giả lập. Không gọi OpenAI thật trong test thường.
- Dùng mã FR/NFR/US/TC của tài liệu trong tên test, commit và mô tả PR để giữ truy vết.
- Phân biệt bốn trạng thái khi báo cáo: **yêu cầu**, **thiết kế**, **đã cài đặt**, **đã kiểm thử đạt**. Chưa có bằng chứng thì ghi "chưa đánh giá".
- Chỉ đưa số liệu hiệu năng, chất lượng RAG, tỷ lệ thành công từ lần chạy thật. Không dựng số liệu.
- Không trình bày tham số tự chọn (mục 6) như chuẩn dinh dưỡng hoặc kết luận y khoa.
- Khi cài đặt khác thiết kế, cập nhật tài liệu và sơ đồ cho khớp mã nguồn.
- Bàn giao cuối: mã chạy được, migration, dữ liệu mẫu có nguồn, `.env.example`, hướng dẫn cài đặt/khôi phục, OpenAPI, UML/ERD, bảng truy vết, kết quả kiểm thử, ảnh giao diện thật, báo cáo khớp trạng thái hệ thống.

## 18. Vai trò nhóm

- TV1: thiết kế kỹ thuật, code, tích hợp, quyết định nghiệm thu (~20 giờ/tuần).
- TV2: chuẩn hóa dữ liệu món, đối chiếu kết quả, cài thử theo README.
- TV3: chuẩn bị PDF, câu hỏi kiểm thử, bằng chứng demo.
