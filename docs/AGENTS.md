# AGENTS.md — Website tư vấn dinh dưỡng cá nhân hóa tích hợp Chatbot RAG

Đồ án Thực tập cơ sở chuyên ngành, nhóm 9. Thời gian 16/11–25/12/2026. Giao diện tiếng Việt. Đơn vị: kg, cm, g, ml, kcal; ngân sách VND/ngày.

## 1. Cách dùng file này

- Đọc hết file trước khi sửa code.
- Khi các nguồn mâu thuẫn, ưu tiên: (1) yêu cầu của người dùng trong phiên làm việc, (2) file này, (3) `docs/PLAN.md` (đặc tả), (4) `docs/DB_TABLES.md` (thiết kế 30 bảng), (5) `docs/Ke_hoach_TTCS_6_phase_va_User_Story.md` (lịch và user story).
- Gặp mâu thuẫn hoặc thiếu thông tin: xem mục 15. Không tự quyết; hỏi và ghi lại.
- Không đánh dấu một chức năng là "đã làm" nếu chưa chạy và chưa có test.

## 2. Mục tiêu và phạm vi

Luồng demo gồm khai báo hồ sơ, tính chỉ số, xem gợi ý bữa ăn một ngày theo kcal mục tiêu và hỏi Chatbot RAG có dẫn nguồn.

Gợi ý dựa trên kcal mục tiêu ước tính và mục tiêu dinh dưỡng trong hồ sơ cơ bản. Gợi ý mang tính tham khảo. Luồng gợi ý không kiểm tra dị ứng, thực phẩm loại trừ hoặc ngân sách. Giao diện nêu rõ giới hạn khi hiển thị kết quả.

**Phạm vi chỉ số Phase 1:** hồ sơ cơ bản có thể lưu ở mọi độ tuổi hợp lệ. Các công thức ước tính dùng cho người từ 18 tuổi. Tuổi được tính từ ngày sinh.

**Ngoài phạm vi:** chẩn đoán, kê đơn, điều trị, tư vấn trực tiếp với bác sĩ, thanh toán, giao đồ ăn, app di động riêng, nhận dạng món bằng ảnh, OCR tài liệu scan, đăng nhập mạng xã hội, khôi phục mật khẩu qua email.

## 3. Tác nhân

| Mã     | Tác nhân                | Ghi chú                                                                                        |
| ------ | ----------------------- | ---------------------------------------------------------------------------------------------- |
| ACT-01 | Khách                   | Chưa xác thực, không phải role lưu trong DB                                                    |
| ACT-02 | Người dùng (`USER`)     | Quản lý dữ liệu của mình                                                                       |
| ACT-03 | Quản trị viên (`ADMIN`) | Tài khoản, danh mục, tài liệu RAG, phản hồi, vận hành                                          |
| ACT-04 | OpenAI                  | Chỉ embedding và sinh nội dung theo yêu cầu của Backend. Không truy cập DB, không đổi thực đơn |

Chatbot, bộ kiểm tra thực đơn và PostgreSQL là thành phần bên trong, không phải tác nhân. Không thêm vai trò chuyên gia dinh dưỡng.

## 4. Kiến trúc và công nghệ

Ba tầng, **một Backend chia module**. Không tách nhiều dịch vụ độc lập.

| Tầng           | Công nghệ                                                                       |
| -------------- | ------------------------------------------------------------------------------- |
| Presentation   | Next.js, TypeScript, Tailwind CSS. Giao diện User và Admin                      |
| Logic          | Java, Spring Boot, RESTful API, Spring Security (session phía server)           |
| Data           | PostgreSQL + pgvector. PDF lưu ở vùng không truy cập công khai                  |
| Tích hợp ngoài (Phase 2–3) | Spring AI 1.1.x trong adapter OpenAI của Spring Boot. Khóa API, model sinh và model embedding lấy từ biến môi trường |

- Frontend và API cùng origin qua reverse proxy.
- Worker chạy trong ứng dụng Backend; trạng thái tác vụ lưu PostgreSQL.
- Chạy bằng Docker Compose.
- Module Backend: xác thực, hồ sơ, tính toán, thực phẩm/món ăn, thực đơn, tiến trình, Chatbot/RAG, quản trị.
- Phase 2 dùng Spring AI và OpenAI Structured Outputs (`JSON_SCHEMA`) để sinh gợi ý bữa ăn một ngày. Backend kiểm tra định dạng và tính kcal ước tính từ dữ liệu món. Worker giới hạn 3 lần gọi trong 180 giây, tính cả lượt thử lại.
- Phase 3 dùng adapter Spring AI với cấu hình OpenAI của backend để tạo embedding và sinh câu trả lời. Module RAG đọc PDF theo trang bằng `PagePdfDocumentReader`, lưu và truy xuất `rag_chunks` qua JDBC/pgvector, rồi xác minh mã đoạn trích dẫn. Schema đi qua Flyway.
- Không hardcode khóa API hoặc tên model trong nghiệp vụ.
- Đổi model embedding hoặc số chiều vector: tạo lại chỉ mục. Không trộn vector không tương thích.
- Thời điểm lưu theo UTC. Ngày thực đơn và nhật ký theo `Asia/Ho_Chi_Minh`.

## 5. Lịch và mức ưu tiên

Mốc: demo toàn luồng **11/12**, chốt code **18/12**, nộp **25/12/2026**.

| Phase | Ngày        | Kết quả                                              |
| ----- | ----------- | ---------------------------------------------------- |
| 1     | 16–20/11    | Tài khoản, hồ sơ, chỉ số chạy từ trình duyệt         |
| 2     | 23–27/11    | AI đề xuất thực đơn 1 ngày, Backend kiểm tra và lưu  |
| 3     | 30/11–04/12 | Chatbot trả lời có nguồn và số trang từ PDF đã duyệt |
| 4     | 07–11/12    | Xem lại gợi ý đã lưu và hỏi Chatbot về gợi ý         |
| 5     | 14–18/12    | Chốt phạm vi, kiểm tra lỗi, cài thử trên máy thứ hai |
| 6     | 21–25/12    | Đo thực nghiệm, tổng duyệt, báo cáo, nộp             |

**P0:** tài khoản, hồ sơ, chỉ số, dữ liệu món cần thiết, thực đơn 1 ngày, nạp và duyệt PDF, hỏi đáp có trích dẫn.
**P1:** xem lại gợi ý đã lưu và hỏi Chatbot về nội dung gợi ý.
**P2:** nhật ký tiến trình, phản hồi, quản trị mở rộng. Thực đơn tuần, áp dụng hoặc chỉnh sửa gợi ý và lịch sử phiên bản để ngoài phạm vi bàn giao.

Chỉ làm P2 khi P0 và P1 chạy trọn vẹn. Luồng demo/bảo vệ đi từ đăng nhập, hồ sơ và gợi ý một ngày đến hỏi Chatbot, xem nguồn và mở lại gợi ý đã lưu.

User story gồm US01 đăng nhập, US02 hồ sơ, US03 danh mục (dữ liệu mẫu P0, CRUD đầy đủ P2), US04 chỉ số, US05 nạp và duyệt PDF, US06 gợi ý một ngày, US07 hỏi đáp có nguồn, US08 xem lại gợi ý đã lưu (P1), US09 giải thích gợi ý (P1), US10 nhật ký (P2), US11 phản hồi (P2), US12 quyền riêng tư và khóa tài khoản (P2), US13 theo dõi vận hành (P2).

## 6. Hồ sơ và công thức tính

- Một tài khoản có tối đa một hồ sơ hiện tại. Phase 1 hỗ trợ tạo, đọc, sửa và xóa hồ sơ hiện tại. Sửa tạo phiên bản mới. Xóa bỏ đánh dấu hiện tại và giữ lịch sử trong DB.
- Hồ sơ cơ bản gồm ngày sinh, nhóm hệ số công thức, chiều cao, cân nặng, mức vận động và mục tiêu.
- Người dùng có thể trao đổi về dị ứng, thói quen ăn uống và tình trạng cá nhân khi hỏi Chatbot ở Phase 3. Đây là ngữ cảnh hội thoại. Hồ sơ mới không thu thập các trường này.
- Thiếu hoặc sai miền giá trị: báo lỗi tại đúng trường.

**Công thức (có nguồn nghiên cứu):**

- BMI = kg / m².
- Năng lượng nghỉ (Mifflin–St Jeor) = `10×kg + 6,25×cm − 5×tuổi + hệ số`; hệ số `+5` hoặc `−161` theo `sex_for_formula`. Giao diện ghi rõ là **ước tính**. Báo cáo phân biệt "BMR" trong đề cương với REE/RMR của công thức gốc.

**Tham số mặc định do nhóm chọn (không phải khuyến nghị y khoa), lưu trong `nutrition_rule_versions`:**

| Tham số        | Giá trị                                                           |
| -------------- | ----------------------------------------------------------------- |
| Hệ số vận động | 1,2 / 1,375 / 1,55 / 1,725 / 1,9 (5 mức)                          |
| TDEE           | năng lượng nghỉ × hệ số vận động                                  |
| Kcal mục tiêu  | duy trì = TDEE; giảm cân = 90% TDEE; tăng cân/tăng cơ = 110% TDEE |
| P/C/F          | 20/50/30; tăng cơ 25/45/30                                        |
| Quy đổi        | protein, carb 4 kcal/g; fat 9 kcal/g                              |
| Nước           | 30 ml/kg/ngày                                                     |

Cân nặng mới trong nhật ký cập nhật hồ sơ và tạo phiên bản chỉ số mới. Gợi ý đã lưu giữ kcal mục tiêu tại thời điểm tạo. Mục tiêu mới áp dụng cho yêu cầu gợi ý tiếp theo.

## 7. Dữ liệu món và kcal ước tính

- Số liệu dinh dưỡng trên **100 g phần ăn được**, kèm trạng thái sống/chín/chế biến, nguồn, ngày cập nhật.
- Không trộn số liệu sống/chín. Không tự đổi ml sang g khi thiếu hệ số.
- Không bịa thành phần dinh dưỡng hoặc nguồn để đủ số lượng. Thiếu dữ liệu thì ghi nhận phần thiếu.
- Dùng danh mục món mẫu có nguồn và dữ liệu khẩu phần đủ để demo các hồ sơ mẫu. RAG giữ mục tiêu 10 tài liệu có nguồn xác minh.

Backend kiểm tra cấu trúc một ngày, mã món đang dùng và khẩu phần hợp lệ. Backend tính kcal ước tính từ kcal mỗi khẩu phần của món mẫu. Giao diện hiển thị kcal mục tiêu và tổng kcal ước tính cạnh nhau. Mức gần mục tiêu là tiêu chí tham khảo khi sinh và đánh giá.

## 8. Luồng AI tạo gợi ý một ngày

1. Kiểm tra quyền và đọc hồ sơ có chỉ số ước tính hiện tại.
2. Gửi kcal mục tiêu, mục tiêu dinh dưỡng và danh mục món ứng viên kèm kcal mỗi khẩu phần.
3. Nhận bữa, `recipeId`, số khẩu phần và giải thích ngắn qua OpenAI Structured Outputs.
4. Kiểm tra dữ liệu trả về, tính kcal ước tính và lưu gợi ý hoàn chỉnh để người dùng xem lại.
5. Nếu cần thử lại do lỗi đầu ra, worker giới hạn tổng số lần gọi ở **3 lượt/tác vụ** trong **180 giây**. Khi hết lượt hoặc hết thời gian, báo lỗi và bỏ kết quả đến muộn.

## 9. RAG và Chatbot

**Nạp tài liệu:**

- PDF có lớp văn bản, tối đa 20 MB. PDF scan: báo rõ là không đọc được.
- Lưu tên, tổ chức/tác giả, năm, URL (nếu có), phiên bản, checksum.
- Tách theo trang và đoạn: khởi điểm 500 token/đoạn, chồng lấn 100 token.
- Tạo embedding, lưu pgvector. Admin xem kết quả rồi mới kích hoạt.
- Tài liệu bị thu hồi không dùng cho câu trả lời mới (hiệu lực từ lượt hỏi mới). Lịch sử giữ dấu vết nguồn đã dùng.

**Trả lời:**

- Truy xuất tối đa 5 đoạn từ tài liệu đang hoạt động.
- Ngữ cảnh gửi AI gồm câu hỏi, lịch sử hội thoại giới hạn và phần hồ sơ cơ bản cần thiết. Dị ứng, thói quen ăn uống hay tình trạng cá nhân chỉ đi theo nội dung người dùng chủ động nêu trong hội thoại.
- Backend đối chiếu mỗi trích dẫn với đoạn thực sự được truy xuất. Không tự tạo tài liệu tham khảo.
- Không có bằng chứng phù hợp: nói rõ chưa đủ căn cứ.
- Số liệu thực đơn lấy từ Backend. Kiến thức dinh dưỡng lấy từ tài liệu RAG.
- Không chẩn đoán, kê đơn, đưa chế độ ăn điều trị.
- Nội dung tài liệu và tin nhắn không được đổi quyền truy cập hay ra lệnh sửa dữ liệu (chống prompt injection).
- Không gửi AI: tên, email, mật khẩu, toàn bộ lịch sử người dùng.
- Chatbot có thể giải thích gợi ý đã lưu bằng dữ liệu do Backend cung cấp. Luồng này chỉ đọc.
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

| Nhóm                         | Trách nhiệm                                 |
| ---------------------------- | ------------------------------------------- |
| `/auth/*`                    | Đăng ký, đăng nhập, đăng xuất, đổi mật khẩu |
| `/me/profile`, `/me/metrics` | Hồ sơ và chỉ số của người đang đăng nhập    |
| `/foods`, `/recipes`         | Tra cứu danh mục                            |
| `/meal-plan-jobs`            | Tạo tác vụ, đọc trạng thái                  |
| `/meal-plans/*`              | Xem lại gợi ý một ngày đã lưu               |
| `/progress/*`                | Nhật ký và tổng hợp                         |
| `/conversations/*`           | Hội thoại, tin nhắn, nguồn                  |
| `/feedback/*`                | Phản hồi, chia sẻ, thu hồi                  |
| `/admin/*`                   | Danh mục, tài liệu, tài khoản, phản hồi     |

- Tạo thực đơn trả `202` kèm `jobId`.
- Trạng thái tác vụ: `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`. Chỉ `SUCCEEDED` trả `mealPlanId`.
- Lỗi dùng cấu trúc chung: `code`, `message`, `fieldErrors`, `traceId`.
- Màn hình bàn giao gồm giới thiệu/xác thực, hồ sơ, tổng quan chỉ số, danh mục món mẫu, tạo và xem lại gợi ý một ngày, Chatbot có nguồn và quản trị tài liệu RAG. Các màn hình P2 theo phạm vi tại mục 5.
- Giao diện hoạt động ở 360, 768, 1440 px; dùng được bằng bàn phím; có trạng thái tải/rỗng/lỗi.

## 12. Cơ sở dữ liệu (30 bảng, PostgreSQL)

Xem [thiết kế CSDL gốc](Danh_sach_30_bang_CSDL.md). Danh sách 30 bảng mô tả thiết kế đầy đủ. Migration Phase 2–3 chỉ triển khai phần phục vụ phạm vi đã chốt.

**Quy ước:** khóa chính UUID. Thời điểm dùng `TIMESTAMPTZ`; ngày sinh và ngày thực đơn dùng `DATE`. Mặc định NOT NULL. Số thực dùng `NUMERIC(12,3)`. Mỗi hàng hồ sơ, nguyên liệu, công thức, thực đơn, tài liệu RAG là **một phiên bản**. User và Admin dùng chung `users`, phân biệt bằng `role`.

| Nhóm               | Bảng                                                                                  |
| ------------------ | ------------------------------------------------------------------------------------- |
| Tài khoản          | `users`, `sessions`                                                                   |
| Hồ sơ              | `nutrition_profiles`, `dietary_preferences`, `allergies`, `profile_disliked_foods`    |
| Tính toán          | `nutrition_rule_versions`, `calculation_snapshots`                                    |
| Danh mục           | `data_sources`, `ingredients`, `nutrient_values`, `recipes`, `recipe_ingredients`     |
| Thực đơn           | `meal_plan_jobs`, `meal_plans`, `meal_plan_days`, `meals`, `meal_plan_items`          |
| Tiến trình         | `progress_records`, `water_entries`, `meal_consumptions`                              |
| RAG                | `rag_documents`, `rag_chunks`                                                         |
| Hội thoại          | `chat_conversations`, `chat_messages`, `chat_sources`                                 |
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
- Gợi ý hoàn chỉnh trong phạm vi bàn giao gồm một ngày với các bữa có ít nhất một món. Kiểm tra trước khi commit giao dịch.
- Không sửa hồi tố bản ghi đã dùng làm căn cứ lịch sử. Ngừng sử dụng/thu hồi thay vì xóa bản ghi được tham chiếu.
- Chính sách xóa phải xác định cho từng FK. Tham chiếu tùy chọn tới tin nhắn gốc hoặc tài khoản có thể đặt NULL.

## 13. Yêu cầu phi chức năng (mục tiêu kiểm thử, chưa phải kết quả đạt)

| Mã     | Mục tiêu                                                                                                                                                                        |
| ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| NFR-01 | Gợi ý đã lưu có cấu trúc, mã món và khẩu phần hợp lệ. Backend tính kcal ước tính từ danh mục. Ghi nhận chênh lệch với mục tiêu khi đánh giá. |
| NFR-02 | Chatbot p95 hoàn tất < 5 giây (100 yêu cầu, 5 đồng thời). Đo riêng thời gian nhận nội dung đầu tiên; không dùng nó thay cho thời gian hoàn tất. Không áp dụng cho thực đơn tuần |
| NFR-03 | API thường p95 < 500 ms với 20 người dùng đồng thời, không gồm AI và xử lý tài liệu                                                                                             |
| NFR-04 | Băm mật khẩu bằng thuật toán phù hợp; cookie HttpOnly, Secure khi HTTPS; chống CSRF; kiểm tra quyền ở mọi API dữ liệu riêng                                                     |
| NFR-05 | Khóa API chỉ ở Backend; không ghi dữ liệu nhạy cảm vào log chung                                                                                                                |
| NFR-06 | Chatbot timeout 30 giây, cho thử lại; tác vụ thực đơn tối đa 180 giây; không trả thành công giả, không ghi dữ liệu một phần                                                     |
| NFR-07 | 100% trích dẫn hiển thị ánh xạ tới nguồn đã truy xuất                                                                                                                           |
| NFR-08 | Responsive, bàn phím, nhãn form, trạng thái tải/rỗng/lỗi                                                                                                                        |
| NFR-09 | Lưu gợi ý hoàn chỉnh trong một giao dịch và giữ toàn vẹn tham chiếu. |
| NFR-10 | Tách module; migration; OpenAPI; `.env.example`; test tự động                                                                                                                   |
| NFR-11 | Ghi mã yêu cầu, phiên bản công thức, prompt, model, danh mục, trace ID, thời gian, token                                                                                        |
| NFR-12 | Hướng dẫn sao lưu/khôi phục PostgreSQL; khôi phục thử ít nhất một lần                                                                                                           |

## 14. Kiểm thử

- Test tự động dùng **AI giả lập** để tái lập lỗi. Đánh giá thực nghiệm chạy riêng với OpenAI thật.
- Ca bắt buộc gồm xác thực và phân quyền, truy cập chéo tài khoản, hồ sơ thiếu hoặc ngoài phạm vi, đối chiếu chỉ số tính tay, gợi ý một ngày, kiểm tra đầu ra AI, tài liệu scan hoặc thu hồi, kho RAG rỗng, trích dẫn giả, prompt injection, timeout, kết quả đến muộn, responsive, hiệu năng và khôi phục sao lưu. Đối chiếu mã TC gốc theo phần chức năng được chọn.
- Với gợi ý, thử đầu ra hợp lệ, mã món sai, khẩu phần sai, AI từ chối và đối chiếu tổng kcal ước tính với phép tính tay. Kiểm tra dữ liệu gửi AI chỉ chứa các trường cần cho gợi ý.
- Đánh giá thực: 30 hồ sơ mẫu; 50 câu hỏi RAG (có đáp án, thiếu nguồn, ngoài phạm vi, cố tác động quy tắc); 100 yêu cầu đo thời gian. Công bố model, cấu hình, số đồng thời, phân bố độ trễ.
- Đo tỷ lệ tạo gợi ý thành công trên tổng tác vụ và phân bố chênh lệch giữa kcal ước tính của gợi ý với kcal mục tiêu.
- Bảng truy vết: Yêu cầu → Use case → Module/API → Ca kiểm thử → Bằng chứng → Mục báo cáo. Ví dụ: `FR-06 → UC-Tạo thực đơn → Meal Planning → TC-05/06/07 → kết quả chạy → mục 4.3, 4.6`.

## 15. Chưa chốt (hỏi trước khi làm)

1. **Lớp AI — đã chốt:** dùng Spring AI trong adapter OpenAI của backend Spring Boot theo kiến trúc ở mục 4. Các mốc tích hợp nằm trong [kế hoạch 6 phase](Ke_hoach_TTCS_6_phase_va_User_Story.md).
2. **Phạm vi bàn giao — đã chốt:** gợi ý một ngày theo kcal và đặc điểm hồ sơ, cùng Chatbot RAG có trích dẫn. Dùng mục 5 để xác định phần thực hiện của các FR trong đặc tả gốc.
3. **FR-01 — đã chốt ngày 16/11/2026:** người dùng yêu cầu đăng ký và đổi mật khẩu ngay Phase 1, ngoài đăng nhập/đăng xuất và 2 tài khoản mẫu.
4. **Model embedding Phase 3 — đã chốt:** `text-embedding-3-small`, 1.536 chiều. Xem [cách nạp nguồn và lập lại chỉ mục](PHASE3.md).
5. **Công cụ migration — đã chốt:** Flyway SQL, build bằng Maven Wrapper, Java 21.
6. **Hồ sơ Phase 1 — đã chốt:** CRUD hồ sơ cơ bản và chỉ số ước tính theo quy tắc có phiên bản. V4 giữ dữ liệu cũ và cho phép tạo hồ sơ mà không nhập điều kiện cá nhân hóa. Xem `docs/PHASE1.md`.
7. **Áp dụng thực đơn:** thiết kế `meal_plan_days.is_applied` thuộc phạm vi mở rộng sau bàn giao.
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
- Backend kiểm tra đầu ra AI và tính kcal ước tính từ dữ liệu món. Các phép tính và kiểm tra dữ liệu có unit test.
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
