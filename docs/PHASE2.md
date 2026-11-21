# Phase 2 · Gợi ý thực đơn một ngày

US03 cung cấp danh mục món mẫu có định lượng và nguồn. US06 tạo gợi ý gồm bữa sáng, trưa và tối từ kcal mục tiêu và mục tiêu dinh dưỡng đã lưu. Giao diện dùng mục **Thực đơn** trong bố cục Phase 1 và cho mở lại gợi ý gần nhất. Danh sách lịch sử thuộc Phase 4.

## Nguồn danh mục

- [Bảng thành phần thực phẩm Việt Nam 2007](corpus/bangthanhphanthucphamVN.pdf) của Viện Dinh dưỡng cung cấp giá trị trên 100 g phần ăn được. Mỗi nguyên liệu nêu mã thực phẩm và số trang tính từ đầu tệp PDF.
- Bảng 100 công thức trong `docs/corpus` cung cấp tên, thành phần và định lượng của các công thức tham khảo. Migration ghi số dòng Excel của công thức được chọn và các điều chỉnh của phiên bản mẫu.
- Ba món đơn giản dùng [dữ liệu USDA SR28 đã đối chiếu ở Phase 1](fixtures/phase1-foods.json). Tổng cộng danh mục có 13 món và 21 nguyên liệu có số liệu.

Khẩu phần là quy ước của danh mục mẫu. Một số món dùng phiên bản rút gọn bỏ gia vị chưa định lượng hoặc xác định rõ loại nguyên liệu thay thế. Các món dùng số liệu sống được tính bằng lượng ăn được cân trước khi nấu. Cơm và trứng luộc từ USDA dùng lượng đã chín. Món rau xào dùng lượng dầu cân riêng bằng gam. Kcal của món mẫu là ước tính theo nguyên liệu đã ghi, với giả định phần đã cân đi vào món. Chưa có số đo khối lượng thành phẩm hay giá cho các món mới.

Ví dụ, một phần **Phở bò đơn giản** là một phần tư công thức mẫu. Bánh phở tươi 800 g × 143 kcal/100 g, thăn bò nạc sống 300 g × 127 kcal/100 g và hành tây sống 100 g × 41 kcal/100 g cho **391,5 kcal/khẩu phần**.

## Luồng API và kiểm thử

| Endpoint | Hành vi |
|---|---|
| `GET /api/v1/foods`, `GET /api/v1/recipes` | Liệt kê nguyên liệu hoặc món đang hoạt động, trạng thái, nguồn và kcal |
| `POST /api/v1/meal-plan-jobs` | Trả `202` cùng mã tác vụ. Chỉ một tác vụ đang chờ hoặc đang chạy cho mỗi người dùng |
| `GET /api/v1/meal-plan-jobs/{id}`, `GET /api/v1/meal-plan-jobs/latest` | Đọc trạng thái của chủ tài khoản |
| `GET /api/v1/meal-plans/{id}`, `GET /api/v1/meal-plans/latest` | Đọc gợi ý đã lưu của chủ tài khoản |

Các endpoint `latest` trả `204` khi chưa có dữ liệu. Tác vụ hết hạn sau 180 giây kể từ lúc gửi và dùng tối đa ba lượt gọi. Backend dùng Spring AI với JSON Schema, kiểm tra mã món và khẩu phần, tính kcal rồi lưu đầy đủ trong một giao dịch. Gợi ý tham khảo và không kiểm tra dị ứng, thực phẩm loại trừ hoặc ngân sách.

Các ca `MealSuggestionTest`, `OpenAiMealPlannerTest`, `MealPlanApiTest` và `phase2.spec.ts` bao phủ tính kcal, định dạng request, trường hợp từ chối hoặc sai đầu ra, quyền sở hữu, giới hạn thử lại, bỏ kết quả đến muộn và mở lại gợi ý trên giao diện.

Ngày 21/11/2026, bộ kiểm thử backend có 27 ca đạt với PostgreSQL 17/pgvector và Playwright có 6 ca đạt. Hai hồ sơ thử nghiệm đi từ nút **Tạo gợi ý** qua Next.js, Spring AI dùng phản hồi HTTP giả lập và PostgreSQL, sau đó mở lại gợi ý đã lưu. Giao diện được kiểm tra ở 360, 768 và 1440 px. Gọi OpenAI thật với hai hồ sơ mẫu, đánh giá chất lượng và đo độ lệch kcal thực nghiệm chưa thực hiện vì môi trường chưa cấu hình khóa và model.
