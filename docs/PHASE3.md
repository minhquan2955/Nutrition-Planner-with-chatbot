# Phase 3 · Chatbot RAG có nguồn

US05 cung cấp màn hình quản trị nạp, duyệt, kích hoạt và thu hồi PDF. US07 cung cấp hỏi đáp tiếng Việt, hội thoại riêng theo tài khoản và trích dẫn mở đúng trang PDF. Xem [thiết kế đã duyệt](superpowers/specs/2026-11-25-phase-3-rag-design.md).

## Nạp kho demo

1. Cấu hình `OPENAI_API_KEY`, `OPENAI_MODEL` hỗ trợ JSON Schema và `OPENAI_EMBEDDING_MODEL=text-embedding-3-small` ở backend. Vector dùng 1.536 chiều. Khởi động ứng dụng rồi đăng nhập Admin và mở **Tài liệu RAG**.
2. Nạp [Bảng thành phần thực phẩm Việt Nam (2007)](corpus/bangthanhphanthucphamVN.pdf). Nhập đơn vị **Bộ Y tế · Viện Dinh dưỡng**, năm và phiên bản **2007**, trang **14,26**. Mở mục **Bản chép đối chiếu** và dán [JSON chép lại các hàng đã đối chiếu](corpus/phase3-transcriptions.json) vào ô **Bản chép theo trang**. Giữ nguyên tệp PDF gốc.
3. Nạp [FDA Food Allergies: What You Need to Know](fixtures/fda-food-allergies-2024.pdf). Nhập tổ chức **FDA**, năm và phiên bản **2024**, [URL nguồn](https://www.fda.gov/media/180903/download) và trang **1-2**.
4. Khi tài liệu chuyển sang **Chờ duyệt**, mở từng trang đích từ đoạn trích. Đối chiếu câu chữ hoặc giá trị và đơn vị rồi chọn **Kích hoạt**. Hỏi các câu bên dưới từ một tài khoản người dùng. Thử thu hồi một nguồn, hỏi lại và kiểm tra lịch sử.

Sách Việt Nam có 567 trang vật lý. Trình đọc tự động mở được tệp và xác định đúng trang nhưng một số nhãn tiếng Việt lỗi mã chữ và văn bản của hai cột dính vào nhau. Hai bản chép ghi lại những giá trị đã kiểm tra trên trang PDF 14 và 26, tương ứng trang in 1 và 13. Excel trong corpus là nguồn tham khảo công thức Phase 2. Bộ demo trên gồm hai tài liệu. Mục tiêu 10 nguồn xác minh được theo dõi trong đánh giá corpus.

| Câu hỏi đối chiếu | Căn cứ dự kiến |
| --- | --- |
| 100 g phần ăn được của gạo nếp cái có bao nhiêu năng lượng và protein? | 344 kcal, 8,6 g protein, mã 1001, trang PDF 14 của sách Việt Nam. |
| Bánh phở mã 1013 có bao nhiêu năng lượng và glucid trên 100 g phần ăn được? | 143 kcal, 31,7 g glucid, trang PDF 26 của sách Việt Nam. |
| Theo FDA, mè/vừng có thuộc nhóm dị nguyên thực phẩm chính trong tài liệu không? | Có, trang PDF 1 của FDA. |
| Theo FDA, nguồn dị nguyên được nêu ở đâu trên nhãn? | Trong ngoặc sau tên thành phần, hoặc trong thông báo “Contains” cạnh/sau danh sách thành phần, trang PDF 2 của FDA. |
| Kho này có đủ căn cứ xác định lượng iốt của món canh do mình nấu không? | Báo thiếu căn cứ. |

## API và vận hành

| Endpoint | Hành vi |
| --- | --- |
| `GET /api/v1/admin/rag-documents`, `POST /api/v1/admin/rag-documents` | Liệt kê hoặc nhận PDF multipart tối đa 20 MB, khai báo metadata, phạm vi trang và bản chép đã đối chiếu nếu có. |
| `GET /api/v1/admin/rag-documents/{id}`, `GET /api/v1/admin/rag-documents/{id}/file` | Xem trạng thái, tối đa 60 đoạn mẫu và PDF gốc. |
| `POST /api/v1/admin/rag-documents/{id}/activate`, `POST /api/v1/admin/rag-documents/{id}/revoke` | Kích hoạt sau xử lý và duyệt; thu hồi nguồn đang hoạt động. |
| `GET /api/v1/conversations`, `POST /api/v1/conversations`, `GET /api/v1/conversations/{id}` | Liệt kê, tạo và mở hội thoại của chủ tài khoản. |
| `POST /api/v1/conversations/{id}/messages` | Gửi câu hỏi, xử lý tối đa 30 giây và lưu trọn lượt hỏi nếu hoàn tất. Nếu gửi lại cùng `requestId` và nội dung đã lưu, trả hội thoại hiện tại. |
| `GET /api/v1/conversations/{id}/sources/{sourceId}/file` | Kiểm tra chủ hội thoại và hiệu lực nguồn trước khi trả PDF; giao diện thêm `#page=N`. |

Worker ghi toàn bộ đoạn và trạng thái chờ duyệt trong một giao dịch. Tác vụ dở được nhận lại sau khi hết hạn giữ chỗ 5 phút. Với tệp lỗi hoặc lỗi embedding, Admin có thể nạp lại cùng thông tin ấn bản. Trình đọc trích xuất toàn tệp để xác định trang; chỉ các trang được chọn đi vào kho tìm kiếm. Khi nguồn bị thu hồi, các lượt hỏi mới bỏ qua nguồn đó và liên kết PDF ngừng phục vụ. Hội thoại trước đó vẫn giữ tên, phiên bản, trang và đoạn trích.

Truy xuất cosine chính xác tối đa năm đoạn. Ngưỡng mặc định `RAG_MINIMUM_SIMILARITY=0.30` là tham số khởi điểm cần đo trên bộ câu hỏi thật. Mã nguồn trả về phải khớp tập đoạn đã truy xuất. Một mã sai khiến toàn bộ câu trả lời do model sinh được thay bằng thông báo thiếu căn cứ. Các lượt hỏi gần nhất giúp hiểu câu nối tiếp, trong giới hạn 8.000 token cho truy vấn embedding. Câu trả lời cũ chỉ hiện trong lịch sử trên giao diện. Log kỹ thuật dùng trace ID, thời gian và mã lỗi. Bảng tin nhắn lưu model, phiên bản prompt và số token khi nhà cung cấp có trả về.

Đổi model embedding cần nạp và duyệt lại các bản ghi nguồn với bộ vector tương thích. Khi xuất bản lại để phục vụ model mới, dùng mã phiên bản bản ghi mới có ghi rõ ấn bản gốc (ví dụ `2007-index-v2`), xác minh rồi thu hồi bản ghi cũ. Giữ nguyên các bản ghi đã được trích dẫn. Thay số chiều cần migration và lập lại chỉ mục. Tập hợp PDF gốc lưu trong volume riêng, xem [sao lưu và khôi phục](../README.md#sao-lưu-và-khôi-phục-thử).

## Kiểm thử

Ngày 25/11/2026, `mvn verify` đạt **51 ca** trên PostgreSQL 17/pgvector. Typecheck, lint, production build và **8 ca Playwright** đều đạt. Các ca Phase 3 có AI giả lập và bao phủ truy cập chéo tài khoản, PDF hỏng hoặc thiếu lớp văn bản, mở nguồn, thu hồi, mã trích dẫn giả, câu hỏi ngoài kho, nạp lại sau lỗi, timeout và gửi lại cùng mã yêu cầu.

Luồng tích hợp bổ sung chạy trên Next.js, Spring Boot và PostgreSQL với AI giả lập. Admin nạp PDF Việt Nam 4,7 MB, kiểm hai đoạn trích và kích hoạt. User hỏi về gạo nếp cái, nhận nguồn trang PDF 14 và mở PDF qua API hỗ trợ HTTP Range. Sau thu hồi, URL cũ trả 404 và lịch sử vẫn giữ đoạn trích. Chromium cũng xác nhận tệp gốc mở được tại trang 14 của 567 trang. Lần khôi phục vào database riêng giữ lịch sử trích dẫn và đối chiếu đúng checksum PDF với metadata.

Chất lượng câu trả lời của OpenAI thật, độ bao phủ corpus và mục tiêu p95 dưới 5 giây **chưa đánh giá**. Các phép đo dùng bộ câu hỏi và cấu hình tải trong [kế hoạch 6 phase](Ke_hoach_TTCS_6_phase_va_User_Story.md).

## Cải thiện các plan ban đầu

| Tài liệu | Cải thiện cần đưa vào lần cập nhật kế hoạch |
| --- | --- |
| [Đặc tả gốc](PLAN%20%281%29.md) | Gắn FR-10/11/16 với US05/07 và giới hạn Phase 3. Tách ba phép kiểm mã nguồn, đúng trang, và căn cứ cho từng phát biểu trong NFR-07. |
| [Lịch triển khai](Ke_hoach_TTCS_6_phase_va_User_Story.md) | Đối chiếu trình đọc PDF và đánh dấu trang bảng có thể dùng trước khi tạo embedding. Công bố số nguồn demo, số trang đã duyệt và cách đánh giá nguồn không liên quan. |
| [Thiết kế CSDL](Danh_sach_30_bang_CSDL.md) | Ghi rõ vòng đời nguồn, bản chụp đoạn trích, định danh model, quy tắc thu hồi và sao lưu chung cả metadata lẫn tệp. |
