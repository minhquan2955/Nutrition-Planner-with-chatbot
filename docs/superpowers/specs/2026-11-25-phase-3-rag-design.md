# Thiết kế Phase 3 · Chatbot RAG có nguồn

**Trạng thái** · Đã duyệt. Các quyết định về corpus, cấu hình embedding, bản chép đối chiếu và quyền mở PDF sau thu hồi đã được xác nhận.

## Phạm vi bàn giao

| Yêu cầu | Hành vi cần bàn giao |
| --- | --- |
| US05 · FR-16 | Admin nạp PDF, theo dõi xử lý, đối chiếu đoạn trích và trang với bản gốc, kích hoạt hoặc thu hồi. |
| US07 · FR-10, FR-11 | Người dùng hỏi nhiều lượt bằng tiếng Việt, mở lại hội thoại của mình và xem trích dẫn dẫn đến đúng trang PDF. |
| FR-02 · NFR-04, NFR-05 | Tái sử dụng phiên, CSRF và kiểm tra chủ sở hữu phía backend. Giới hạn dữ liệu đưa vào OpenAI và log kỹ thuật. |

Giải thích gợi ý đã lưu thuộc Phase 4. Thay món, chỉnh sửa thực đơn, quản lý thay phiên bản tài liệu, xóa hội thoại và các luồng P2 để sau phạm vi Phase 3. Chẩn đoán, kê đơn, chế độ ăn điều trị và OCR nằm ngoài phạm vi sản phẩm.

## Lựa chọn cách xử lý corpus

| Cách làm | Hệ quả |
| --- | --- |
| Đọc và lập chỉ mục toàn bộ sách Việt Nam ngay khi nạp | Phủ nhiều chủ đề sớm. Bản trích thử đã cho thấy lỗi ký tự tiếng Việt và thứ tự cột cần đối chiếu trước khi dùng số liệu. |
| Chọn các trang đối chiếu được, lưu đúng số trang vật lý và mở rộng dần | Phạm vi kiến thức ban đầu nhỏ hơn. Mỗi câu thử về số liệu có thể đối chiếu tên thực phẩm, mã, giá trị, đơn vị và trang. |

Phương án thứ hai đã được chọn. Nguồn chính là [Bảng thành phần thực phẩm Việt Nam 2007](../../corpus/bangthanhphanthucphamVN.pdf). Thử trước bằng chính `PagePdfDocumentReader` vì bản trích bằng `pypdf` mới giúp nhận diện rủi ro. Kiểm tra khả năng mở PDF gốc với mật khẩu rỗng và khả năng hiển thị trong trình xem PDF của trình duyệt. Bắt đầu bằng các trang đã có mã thực phẩm được đối chiếu ở Phase 2, ví dụ gạo nếp cái mã 1001 ở trang PDF 14 và bánh phở tươi mã 1013 ở trang PDF 26. Bỏ khỏi tập trang đầu các trang đọc sai số liệu, sai đơn vị hoặc ghép sai cột. Dùng trang văn bản rõ và trang bảng đạt đối chiếu để lập bộ ca demo. [Dữ liệu đã đối chiếu của Phase 2](../../../backend/src/main/resources/db/migration/V6__phase2_sample_recipes.sql)

[PDF FDA](../../fixtures/fda-food-allergies-2024.pdf) bổ sung ca tìm kiếm và trích dẫn từ nguồn tiếng Anh, với câu hỏi bằng tiếng Việt. Workbook trong corpus tiếp tục dùng để tham khảo công thức Phase 2. Mục tiêu 10 tài liệu có nguồn xác minh được theo dõi trong đánh giá chất lượng. Bộ demo Phase 3 có tối thiểu một trang từ sách Việt Nam và các trang FDA đã đối chiếu.

Nếu bản trích tự động giữ được trang nhưng đọc sai ký tự hoặc cột, đề xuất cho phép một bản chép ngắn từ các trang được đối chiếu trực tiếp với PDF gốc. Mỗi đoạn ghi cách trích, tên thực phẩm, mã, giá trị và đơn vị có trong bảng. Giữ liên kết đến trang vật lý của bản gốc và áp dụng cùng bước duyệt của Admin. Ưu tiên bản trích tự động khi đã đạt đối chiếu. Ghi nhận trạng thái sử dụng và chia sẻ của từng nguồn trước khi kích hoạt trên môi trường triển khai.

## Nạp, duyệt và mở PDF

- API Admin nhận PDF tối đa 20 MB và metadata gồm tiêu đề, đơn vị phát hành, năm nếu biết, URL nếu có, phiên bản và tập trang tùy chọn. Với corpus Việt Nam, chọn rõ tập trang đã đối chiếu. Số trang bắt đầu từ 1 và tính từ đầu tệp PDF. Có thể hiện thêm số trang in khi xác định được.
- Lưu bản gốc dưới tên nội bộ trong kho riêng gắn Docker volume và tính SHA-256. Đồng bộ giới hạn upload ở Nginx và Spring, đủ khoảng trống cho multipart. Kiểm tra định dạng bằng trình đọc PDF. Trả lỗi rõ cho PDF hỏng, không mở được hoặc không có văn bản sử dụng được.
- Lưu trạng thái `QUEUED`, `PROCESSING`, `READY`, `ACTIVE`, `FAILED` hoặc `REVOKED` trong PostgreSQL. Worker trong backend lấy tác vụ đã lưu và giới hạn số tài liệu xử lý đồng thời. Khi khởi động lại, tài liệu đang xử lý có thể được đọc lại từ bản gốc. Lưu toàn bộ đoạn và chuyển sang `READY` trong cùng giao dịch. Admin xem trang, đoạn mẫu và thông tin xử lý trước khi kích hoạt. Tài liệu đã thu hồi kết thúc vòng đời kích hoạt trong Phase 3.
- Tách đoạn bên trong từng trang, khởi điểm 500 token và chồng lấn 100. Giữ ranh giới hàng bảng nếu trích xuất cho phép kiểm chứng. Mỗi đoạn mang mã tài liệu, thứ tự đoạn và trang vật lý.
- Mở PDF từ API có xác thực qua liên kết kèm `#page=N`. Admin xem bản gốc để duyệt. Người dùng mở tài liệu đang hoạt động qua nguồn thuộc hội thoại của mình. Sau thu hồi, yêu cầu mở tệp mới bị chặn và giao diện giữ tên, phiên bản, trang và đoạn trích trong lịch sử. Phản hồi tệp dùng chính sách cache phù hợp với thu hồi.
- Sao lưu và khôi phục cần cả PostgreSQL lẫn kho PDF, với kiểm tra đọc được PDF ứng với metadata sau khôi phục.

## Truy xuất và trả lời

- Dùng Spring AI 1.1.x trong backend và dùng lại cấu hình OpenAI của ứng dụng. Đọc tên model embedding từ môi trường, khởi điểm `text-embedding-3-small` với 1.536 chiều. Cột pgvector dùng `VECTOR(1536)`. So khớp model và số chiều khi truy xuất. Khi đổi cấu hình, lập lại chỉ mục trước khi sử dụng tài liệu với model mới. Giữ định danh, văn bản và trang của đoạn đã được trích dẫn. [Tài liệu OpenAI về embedding](https://developers.openai.com/api/docs/guides/embeddings)
- Khởi điểm với cosine search chính xác qua JDBC/pgvector. Lấy tối đa năm đoạn của tài liệu đang hoạt động tại thời điểm truy xuất. Dùng câu hỏi hiện tại và ngữ cảnh tối thiểu cần thiết để hiểu câu nối tiếp. Chọn ngưỡng liên quan từ các câu đối chiếu và câu ngoài kho, rồi ghi cấu hình vào tài liệu triển khai.
- Dùng câu hỏi hiện tại và tối đa ba lượt hỏi gần nhất do người dùng gửi để hiểu câu nối tiếp. Câu trả lời trước đây vẫn xuất hiện trong lịch sử trên giao diện. Căn cứ cho lượt trả lời mới đến từ tập đoạn truy xuất của chính lượt đó. Thêm ca hỏi nối tiếp sau thu hồi để kiểm tra điều này.
- Phase 3 trả lời kiến thức dựa trên câu hỏi, ngữ cảnh giới hạn và các đoạn truy xuất. Hồ sơ tài khoản không tự động đưa vào hai lời gọi AI vì luồng giải thích gợi ý cá nhân thuộc Phase 4. Các điều kiện cá nhân do người dùng tự nêu đi theo nội dung hỏi đáp. Áp dụng cùng quy tắc lọc email, tên và thông tin tài khoản đã biết, cũng như chuỗi được đánh dấu là mật khẩu hoặc khóa, trước cả bước embedding lẫn bước sinh câu trả lời. Dữ liệu đầu vào chỉ phục vụ truy xuất và sinh câu trả lời.
- Phản hồi theo JSON Schema chứa kết quả trả lời hoặc thiếu căn cứ, nội dung và danh sách mã đoạn. Backend xác nhận từng mã nằm trong tập đã truy xuất và tạo thông tin trích dẫn từ dữ liệu đã lưu. Khi kho rỗng, thiếu đoạn phù hợp, thiếu nguồn hợp lệ hoặc có mã giả, trả câu báo thiếu căn cứ. Hỏi ngoài phạm vi có thể trả câu hướng dẫn phù hợp với giới hạn của chatbot.
- Hạn 30 giây bao trùm embedding truy vấn, truy xuất, sinh và xác minh đầu ra. Timeout hoặc lỗi upstream hiển thị trạng thái có thể thử lại. Kết quả đến muộn không tạo tin nhắn hay nguồn. Chỉ lưu lượt trả lời hoàn chỉnh và các đoạn trích trong cùng giao dịch. Mỗi hội thoại xử lý tối đa một lượt hỏi tại một thời điểm.

## Tích hợp ứng dụng

- Flyway tạo `rag_documents`, `rag_chunks`, `chat_conversations`, `chat_messages`, `chat_sources`. Khóa ngoại và bản chụp đoạn trích giúp giữ lịch sử khi thu hồi.
- `/api/v1/admin/rag-documents` hỗ trợ nạp, xem trạng thái, mẫu đoạn và bản gốc, kích hoạt và thu hồi. `/api/v1/conversations` hỗ trợ tạo, liệt kê, xem, gửi lượt hỏi và mở PDF qua nguồn của hội thoại. Kiểm tra người sở hữu từ phiên, áp dụng quy ước lỗi và tài liệu OpenAPI hiện có.
- Tích hợp Chatbot và Quản trị tài liệu vào giao diện tiếng Việt hiện tại. Chat có trạng thái tải, thiếu nguồn và lỗi có thể thử lại. Trích dẫn hiện tài liệu, phiên bản, trang, đoạn trích và liên kết nếu còn quyền mở. Admin thấy trạng thái xử lý, lỗi và kết quả mẫu.
- Giữ mã truy vết, thời gian, model, phiên bản prompt và token khi có. Log vận hành tránh lưu nguyên văn câu hỏi, câu trả lời, hồ sơ và khóa API.

## Nghiệm thu

| Ca | Kết quả cần kiểm chứng |
| --- | --- |
| TC-01, TC-02 | User không quản trị PDF, hai tài khoản không truy cập chéo hội thoại hoặc PDF qua nguồn của nhau. |
| TC-10 | PDF scan và tệp lỗi trả lỗi rõ. Trang trong corpus Việt Nam đối chiếu đúng thực phẩm và đơn vị. FDA có ca kiểm tra trang 1 và 2. Kho rỗng, câu thiếu căn cứ và lượt hỏi sau thu hồi có phản hồi phù hợp. |
| TC-11 | Mã nguồn giả hoặc đoạn không thuộc tập truy xuất bị chặn. Câu trả lời và tên tài liệu hiển thị dưới dạng dữ liệu. Đo riêng tỷ lệ nguồn hợp lệ, tỷ lệ nội dung được nguồn hỗ trợ và tỷ lệ báo thiếu căn cứ đúng. |
| TC-14 | Timeout trong giới hạn, lỗi upstream có thể thử lại và kết quả muộn không lưu. Tài liệu xử lý dở có thể xử lý lại khi worker khởi động. |
| TC-15 | Thử bàn phím và 360/768/1440 px. Kiểm tra mở đúng trang trong trình duyệt và khôi phục bản gốc từ bản sao lưu. |

Kiểm thử tự động dùng adapter AI giả lập. Đối chiếu corpus thật và chạy kiểm thử tự động trước khi báo hoàn tất. Đánh giá OpenAI thật được báo riêng, với bộ 50 câu RAG và phép đo 100 yêu cầu, 5 đồng thời theo hướng dẫn dự án. Mục tiêu p95 hoàn tất dưới 5 giây cần bằng chứng từ lần chạy thực tế.

## Cải thiện tài liệu kế hoạch ban đầu

| Tài liệu | Nội dung cần làm rõ |
| --- | --- |
| [Đặc tả gốc](../../PLAN%20%281%29.md) | Ánh xạ FR-10/11/16 vào phạm vi Phase 3. Tách kiểm tra mã đoạn, kiểm tra trang PDF và chấm nội dung có căn cứ trong NFR-07. Ghi rõ việc thu hồi ảnh hưởng thế nào đến liên kết mở PDF trong lịch sử. |
| [Lịch và user story](../../Ke_hoach_TTCS_6_phase_va_User_Story.md#chatbot-rag-trả-lời-có-nguồn) | Đưa đối chiếu trình đọc với corpus lên trước embedding. Ghi bộ nguồn demo, quy ước số trang vật lý, câu hỏi đối chiếu số liệu và tiêu chí thiếu căn cứ. |
| [Thiết kế 30 bảng](../../Danh_sach_30_bang_CSDL.md#22-rag_documents--phiên-bản-tài-liệu-rag) | Mô tả chuyển trạng thái tài liệu, model và số chiều, truy vết đoạn trích, quy tắc thu hồi và bản sao lưu kho PDF. |

Ghi các quyết định và bằng chứng chạy trong tài liệu Phase 3, README và hướng dẫn repo khi triển khai. Dùng các tài liệu gốc làm mốc đối chiếu.
