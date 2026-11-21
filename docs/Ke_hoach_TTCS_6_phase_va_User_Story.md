**THỰC TẬP CƠ SỞ / 16.11 – 25.12.2026**

Kế hoạch triển khai website tư vấn dinh dưỡng cá nhân hóa

Trong sáu tuần, nhóm xây dựng luồng từ nhập hồ sơ đến nhận gợi ý bữa ăn một ngày và hỏi Chatbot RAG có dẫn nguồn. Mốc demo toàn bộ luồng là 11/12. Nhóm chốt tính năng ngày 18/12 và nộp ngày 25/12/2026.

## Kiến trúc triển khai AI

Spring AI 1.1.x chạy trong adapter OpenAI của backend Spring Boot 3.5 và Java 21. Phase 2 sinh gợi ý một ngày theo kcal mục tiêu và mục tiêu dinh dưỡng trong hồ sơ cơ bản. Backend kiểm tra đầu ra, tính kcal ước tính và lưu gợi ý. Phase 3 dùng lại adapter để tạo embedding và sinh câu trả lời RAG, với JDBC/pgvector để truy xuất nguồn. Khóa API và model đọc từ biến môi trường. Kiểm thử tự động dùng adapter giả lập.

Xem [kiến trúc dự án](AGENTS.md#4-kiến-trúc-và-công-nghệ) và [trạng thái Phase 1](PHASE1.md).

## Lịch triển khai

| **Phase** | **Thời gian** | **Kết quả cần bàn giao**                                |
| --------- | ------------- | ------------------------------------------------------- |
| 1         | 16–20/11      | Tài khoản, hồ sơ và chỉ số chạy từ trình duyệt.         |
| 2 | 23–27/11 | Gợi ý bữa ăn một ngày và hiển thị kcal ước tính cạnh mục tiêu. |
| 3         | 30/11–04/12   | Chatbot trả lời có nguồn và số trang từ PDF đã duyệt.   |
| 4 | 07–11/12 | Xem lại gợi ý và hỏi Chatbot về nội dung gợi ý. |
| 5         | 14–18/12      | Chốt phạm vi, kiểm tra lỗi và cài thử trên máy thứ hai. |
| 6         | 21–25/12      | Đo thực nghiệm, tổng duyệt, hoàn chỉnh báo cáo và nộp.  |

## Phân công theo năng lực thực hiện

TV1 phụ trách thiết kế kỹ thuật, code, tích hợp và quyết định nghiệm thu. Kế hoạch dự trù khoảng 20 giờ/tuần của TV1. TV2 chuẩn hóa dữ liệu món và đối chiếu kết quả. TV3 chuẩn bị PDF, câu hỏi kiểm thử và bằng chứng demo.

## Phạm vi ưu tiên

P0 gồm tài khoản, hồ sơ, chỉ số, món mẫu, gợi ý một ngày, nạp và duyệt PDF, hỏi đáp có trích dẫn. P1 gồm xem lại gợi ý và hỏi Chatbot về gợi ý. P2 gồm nhật ký, phản hồi và quản trị mở rộng. Thực đơn tuần, áp dụng hoặc chỉnh sửa gợi ý và lịch sử phiên bản để ngoài phạm vi bàn giao.

Các mã US01–US13 giữ theo backlog của nhóm. Một số story có phần tối thiểu và phần mở rộng. Trang cuối liệt kê phần có thể dời sau hạn nộp nếu giảng viên không yêu cầu.

**PHASE 1 / 16/11 – 20/11 • 2026**

# Tài khoản hồ sơ và chỉ số

Hoàn thành một lượt sử dụng có dữ liệu được lưu trong PostgreSQL. Chuẩn bị dữ liệu nhỏ để tuần sau có thể viết bộ kiểm tra thực đơn ngay.

## User Story và tiêu chí nghiệm thu

**US01 · P0** Là người dùng, tôi muốn đăng nhập để quản lý dữ liệu riêng.

**Nghiệm thu.** Đăng ký, đăng nhập và đăng xuất được. Tài khoản A bị từ chối khi đọc hồ sơ của B. Dùng hai tài khoản mẫu để demo sớm.

**US02 · P0** Là người dùng, tôi muốn tạo, xem, sửa và xóa hồ sơ cơ bản của mình. Hồ sơ gồm ngày sinh, nhóm hệ số công thức, chiều cao, cân nặng, mức vận động và mục tiêu.

**Nghiệm thu.** Tải lại trang vẫn xem được hồ sơ đã lưu. Kiểm tra dữ liệu nhập, quyền sở hữu và phiên bản khi sửa hoặc xóa hồ sơ hiện tại.

**US04 · P0** Là người dùng, tôi muốn xem các chỉ số tính từ hồ sơ của mình.

**Nghiệm thu.** Với người từ 18 tuổi, BMI, năng lượng nghỉ (REE/RMR), TDEE, kcal mục tiêu, nước và P/C/F khớp ví dụ tính tay. Lưu đầu vào cùng phiên bản công thức.

## Công việc theo ngày

| **Ngày** | **Việc của người triển khai chính**                                                                                               |
| -------- | --------------------------------------------------------------------------------------------------------------------------------- |
| 16/11    | Khởi tạo Next.js, Spring Boot và PostgreSQL/pgvector. Thử một request từ giao diện tới database. Lần gọi OpenAI thật chuyển sang Phase 2. |
| 17/11 | Tạo migration tài khoản, hồ sơ và kết quả tính. Làm đăng ký, đăng nhập, đăng xuất và chuẩn bị hai tài khoản demo. |
| 18/11 | Làm form và API CRUD hồ sơ cơ bản. Kiểm tra dữ liệu nhập và quyền sở hữu tại Backend. |
| 19/11 | Viết service tính chỉ số. Đối chiếu một hồ sơ người lớn bằng tay và thử lưu hồ sơ dưới 18 tuổi. |
| 20/11    | Nối luồng trên UI. Giữ một bộ món mẫu và một PDF đã đọc được để tiếp tục độc lập.                                                 |

## Phần bàn giao của hai thành viên

Trước 18/11, TV2 giao 3 món có nguyên liệu, đơn vị, trạng thái sống/chín, số khẩu phần, giá, dị nguyên và nguồn. TV3 giao 1 PDF kèm 3 câu hỏi, đáp án và số trang. TV1 kiểm tra một khẩu phần và mở đúng trang trước khi duyệt làm tiếp.

## Mốc cuối phase

Đăng nhập, lưu hồ sơ, xem chỉ số và tải lại trang. Dữ liệu còn nguyên và người dùng khác không đọc được.

**PHASE 2 / 23/11 – 27/11 • 2026**

# Gợi ý bữa ăn một ngày

Gợi ý mang tính tham khảo. Kcal ước tính được hiển thị cạnh kcal mục tiêu. Giao diện ghi rõ gợi ý không kiểm tra dị ứng, thực phẩm loại trừ hoặc ngân sách.

## User Story và tiêu chí nghiệm thu

**US03 · P0 cho dữ liệu mẫu** Là quản trị viên, tôi muốn quản lý danh mục có nguồn để hệ thống chọn được món phù hợp.

**Nghiệm thu.** Có món mẫu đang hoạt động, kèm nguồn và kcal mỗi khẩu phần để tính tổng. Giao diện CRUD đầy đủ thuộc P2.

**US06 · P0** Là người dùng, tôi muốn nhận gợi ý một ngày dựa trên kcal mục tiêu và đặc điểm hồ sơ.

**Nghiệm thu.** Gợi ý có một ngày với các bữa có món và khẩu phần hợp lệ. Tổng kcal ước tính khớp dữ liệu món và hiển thị cạnh mục tiêu. Tác vụ lỗi không lưu gợi ý dở dang.

## Công việc theo ngày

| **Ngày** | **Việc của người triển khai chính**                                                                                                                             |
| -------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 23/11 | Chuẩn bị danh mục món mẫu có nguồn, khẩu phần và kcal mỗi khẩu phần. Kiểm tra dữ liệu dùng để demo. |
| 24/11 | Kiểm tra mã món, khẩu phần và cấu trúc. Đối chiếu kcal ước tính với phép tính tay. |
| 25/11    | Làm API tác vụ và giao diện theo dõi trạng thái. Dùng adapter AI giả lập để kiểm tra từ nút bấm đến database. |
| 26/11 | Tích hợp Spring AI và Structured Outputs. Gửi kcal mục tiêu, mục tiêu dinh dưỡng và món mẫu. Worker giới hạn 3 lần gọi trong 180 giây, tính cả lượt thử lại. |
| 27/11 | Thử gọi OpenAI thật qua Spring AI với hai hồ sơ mẫu có kcal mục tiêu hoặc mục tiêu dinh dưỡng khác nhau. Kiểm tra gợi ý và kcal hiển thị. |

## Phần bàn giao của hai thành viên

TV2 đối chiếu khẩu phần, kcal và đơn vị của các món demo. TV3 ghi đầu vào, kết quả mong đợi và kết quả thực tế của các ca thử.

## Mốc cuối phase

Tạo và xem lại gợi ý một ngày. Kết quả hiển thị kcal mục tiêu, tổng kcal ước tính và giới hạn của gợi ý.

**PHASE 3 / 30/11 – 04/12 • 2026**

# Chatbot RAG trả lời có nguồn

Kiểm chứng khả năng tìm đúng đoạn và đúng trang trước khi đánh giá câu trả lời do mô hình sinh ra. Người dùng có thể nói về dị ứng, thói quen ăn uống hoặc tình trạng cá nhân trong câu hỏi. Các thông tin này đi theo hội thoại.

## User Story và tiêu chí nghiệm thu

**US05 · P0** Là quản trị viên, tôi muốn nạp, duyệt và kích hoạt PDF để kiểm soát nguồn Chatbot sử dụng.

**Nghiệm thu.** Mỗi đoạn giữ tên tài liệu, phiên bản và số trang. Truy xuất chỉ dùng tài liệu đang hoạt động. Thu hồi tài liệu có hiệu lực từ lượt hỏi mới.

**US07 · P0** Là người dùng, tôi muốn hỏi bằng tiếng Việt và mở đúng trang chứa căn cứ cho câu trả lời.

**Nghiệm thu.** Nguồn hiển thị thuộc tập đoạn đã truy xuất. Thiếu căn cứ được thông báo rõ. Hội thoại thuộc đúng tài khoản và UI báo lỗi khi dịch vụ không phản hồi.

## Công việc theo ngày

| **Ngày** | **Việc của người triển khai chính**                                                                                     |
| -------- | ----------------------------------------------------------------------------------------------------------------------- |
| 30/11    | Nạp PDF có lớp văn bản bằng API quản trị. Đọc theo trang bằng `PagePdfDocumentReader`, chia đoạn với mục tiêu 500 token và chồng lấn 100 token, rồi đối chiếu đoạn và số trang với bản gốc. |
| 01/12    | Tạo embedding qua Spring AI. Lưu vào `rag_chunks` và truy xuất tối đa 5 đoạn từ tài liệu đang hoạt động qua JDBC/pgvector. Thử với câu hỏi đã biết đáp án và nguồn. |
| 02/12    | Dùng adapter Spring AI để gửi câu hỏi và các đoạn truy xuất cho OpenAI. Nhận câu trả lời kèm mã đoạn nguồn theo cấu trúc cố định. Backend xác minh mã thuộc tập đã truy xuất và báo thiếu căn cứ khi cần. |
| 03/12    | Làm màn hình chat, lưu hội thoại và hiển thị tên tài liệu cùng số trang. Hoàn thiện thao tác kích hoạt và thu hồi PDF.  |
| 04/12    | Thử câu có đáp án, câu thiếu căn cứ, câu dựa trên tài liệu vừa thu hồi và trường hợp dịch vụ hết thời gian chờ 30 giây. |

## Phần bàn giao của hai thành viên

TV3 lập bảng câu hỏi, đáp án, tài liệu và trang. TV2 mở các trang được trích, đánh dấu trang tìm đúng nhưng nội dung chưa hỗ trợ câu trả lời nếu gặp.

## Mốc cuối phase

Demo gợi ý một ngày và Chatbot trên cùng website. Sửa lỗi luồng chính trước khi mở rộng P2.

**PHASE 4 / 07/12 – 11/12 • 2026**

# Xem lại và giải thích gợi ý

Cho người dùng xem lại gợi ý đã lưu và hỏi Chatbot về món, khẩu phần hoặc kcal ước tính.

## User Story và tiêu chí nghiệm thu

**US08 · P1** Là người dùng, tôi muốn mở lại gợi ý đã lưu.

**Nghiệm thu.** Tải lại trang và mở được gợi ý của mình. Kiểm tra quyền sở hữu ở Backend.

**US09 · P1** Là người dùng, tôi muốn Chatbot giải thích gợi ý đang xem.

**Nghiệm thu.** Chatbot dùng món, khẩu phần và kcal đã lưu. Kiến thức dinh dưỡng có nguồn RAG khi đủ căn cứ.

## Công việc theo ngày

| **Ngày** | **Việc của người triển khai chính**                                                                                                       |
| -------- | ----------------------------------------------------------------------------------------------------------------------------------------- |
| 07/12    | Làm màn hình xem thực đơn đã lưu. Khi mở chat từ thực đơn, Backend kiểm tra quyền sở hữu và lấy đúng món, khẩu phần, kcal.                |
| 08/12    | Nối chức năng giải thích. Dùng dữ liệu thực đơn cho số liệu và truy xuất nguồn khi cần giải thích kiến thức dinh dưỡng.                   |
| 09/12 | Hiển thị kcal mục tiêu, kcal ước tính và giới hạn của gợi ý trên màn hình xem lại. |
| 10/12 | Kiểm tra quyền sở hữu khi mở gợi ý và khi hỏi Chatbot về gợi ý. |
| 11/12 | Demo từ hồ sơ đến gợi ý một ngày, hỏi Chatbot, mở nguồn và xem lại gợi ý đã lưu. |

## Phần bàn giao của hai thành viên

TV2 kiểm tra số liệu Chatbot dùng khi giải thích. TV3 kiểm tra xem lại, quyền sở hữu và mở đúng trang nguồn.

## Mốc cuối phase

Từ một hồ sơ, tạo gợi ý, hỏi Chatbot, xem nguồn và mở lại gợi ý đã lưu. Dùng luồng này làm kịch bản bảo vệ.

**PHASE 5 / 14/12 – 18/12 • 2026**

# Chốt phạm vi và phiên bản demo

Dành tuần này cho độ ổn định. Chỉ mở rộng khi luồng P0 và P1 đã chạy trọn vẹn.

## User Story và tiêu chí nghiệm thu

**US10 · P2** Là người dùng, tôi muốn ghi cân nặng, nước uống và bữa đã dùng để theo dõi theo ngày.

**Nghiệm thu.** Nếu chọn, ghi được dữ liệu và khẩu phần thực tế. Sau khi tải lại, lịch sử hiển thị đúng ngày và đúng tài khoản.

## Công việc theo ngày

| **Ngày** | **Việc của người triển khai chính**                                                                                                                                          |
| -------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 14/12    | Đối chiếu FR01–FR19 với bản chạy được. Ghi rõ mục đã làm, làm một phần và còn thiếu. Kiểm tra yêu cầu bắt buộc của giảng viên.                                               |
| 15–16/12 | Sửa lỗi luồng chính. Xét nhật ký nếu P0/P1 đã ổn định. |
| 17/12 | Kiểm tra truy cập chéo tài khoản, thu hồi tài liệu, mã món sai, timeout và kết quả AI đến muộn. |
| 18/12    | Chốt code, migration, dữ liệu mẫu, .env.example, README và danh sách giới hạn. Cài thử trên máy thứ hai.                                                                     |

## Phần bàn giao của hai thành viên

TV2 cài theo README trên máy khác và ghi bước bị kẹt. TV3 chạy kịch bản demo bằng tài khoản mẫu, chụp ảnh và ghi mã lỗi để TV1 tái hiện.

## Mốc cuối phase

Có phiên bản cài được trên máy thứ hai. Ghi tên phiên bản demo và đánh dấu đúng mức hoàn thành của từng story.

**PHASE 6 / 21/12 – 25/12 • 2026**

# Đánh giá thực nghiệm và nộp bài

Nghiệm thu lại các story đã chọn và điền kết quả đo thực tế vào báo cáo. Dành 25/12 cho kiểm tra gói nộp và sự cố phát sinh.

## User Story và tiêu chí nghiệm thu

**Nghiệm thu US01–US10 theo phạm vi đã chốt** Là người dùng, tôi muốn thực hiện trọn luồng demo trên bản bàn giao và nhận thông báo rõ khi tác vụ thất bại.

**Nghiệm thu.** Chạy được luồng hồ sơ, gợi ý một ngày, xem lại và Chatbot có nguồn trên máy demo. Lưu kết quả ca lỗi và trạng thái thực tế của từng story.

## Công việc theo ngày

| **Ngày** | **Việc của người triển khai chính**                                                                                                                                |
| -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| 21/12 | Đánh giá trên 30 hồ sơ. Ghi tỷ lệ tạo gợi ý thành công và phân bố chênh lệch giữa kcal ước tính với kcal mục tiêu. |
| 22/12    | Đánh giá 50 câu hỏi RAG bằng đáp án và trang tham chiếu. Đo P95 của Chatbot với 100 yêu cầu, 5 yêu cầu đồng thời. Đo riêng API thường với 20 người dùng đồng thời. |
| 23/12    | Sửa lỗi ảnh hưởng kết quả và chạy lại các ca liên quan. Ghi model, môi trường, dữ liệu, số đo và giới hạn vào báo cáo.                                             |
| 24/12    | Tổng duyệt trên máy bảo vệ. Chuẩn bị dữ liệu demo, bản sao database và video ngắn của lần chạy thành công.                                                         |
| 25/12    | Kiểm tra gói nộp, đường dẫn repo, README, báo cáo và bản trình diễn. Nộp phiên bản đã tổng duyệt.                                                                  |

## Phần bàn giao của hai thành viên

TV2 chuẩn hóa bảng số liệu và kiểm tra file nộp. TV3 đối chiếu nguồn PDF, lưu bằng chứng cho các câu sai và bấm giờ buổi tổng duyệt. TV1 xác minh kết quả trước khi đưa vào báo cáo.

## Mốc cuối phase

Bàn giao code, hướng dẫn chạy, dữ liệu mẫu, báo cáo, bảng thực nghiệm và bản demo. Ghi kết quả đo cạnh mục tiêu thiết kế: P95 Chatbot dưới 5 giây, API thường dưới 500 ms.

**BACKLOG / PHẠM VI MỞ RỘNG**

# User Story có thể dời sau mốc nộp

TV1 rà soát các mục sau ngày 14/12. Nếu một mục là yêu cầu bắt buộc của giảng viên, nhóm điều chỉnh thứ tự ưu tiên và ghi rõ phần bị dời để giữ mốc chốt code 18/12.

**US11 · P2 · FR13, FR17** Là người dùng, tôi muốn gửi phản hồi, chọn nội dung hội thoại chia sẻ với quản trị viên và theo dõi việc xử lý.

**Nghiệm thu.** Admin chỉ xem được bản chụp đã chia sẻ. Người dùng thấy trạng thái và có thể thu hồi quyền xem. Hệ thống ghi nhật ký truy cập.

**US12 · P2 · FR14, FR19** Là người dùng, tôi muốn biết dữ liệu nào được gửi tới AI và yêu cầu xóa tài khoản. Là quản trị viên, tôi muốn khóa hoặc mở khóa tài khoản.

**Nghiệm thu.** Màn hình nêu phạm vi dữ liệu gửi đi và tiếp nhận yêu cầu xóa. Khi tài khoản bị khóa, phiên đang mở bị chặn ở lần gọi API tiếp theo.

**US13 · P2 · FR18** Là quản trị viên, tôi muốn xem trạng thái tác vụ tạo thực đơn và xử lý PDF để biết lỗi xảy ra ở bước nào.

**Nghiệm thu.** Thấy mã tác vụ, bước lỗi, thời gian và trạng thái. Thử một PDF lỗi và một tác vụ hết hạn. Thống kê không lộ hồ sơ hoặc hội thoại riêng tư.

## Phần mở rộng của các story đã triển khai

| **Story**   | **Phần cần ghi riêng khi báo cáo tiến độ**                                                                       |
| ----------- | ---------------------------------------------------------------------------------------------------------------- |
| US01        | Đăng ký và đổi mật khẩu nếu bản demo ban đầu mới có đăng nhập, đăng xuất và tài khoản mẫu.                       |
| US03        | Giao diện quản trị đầy đủ để thêm, sửa và ngừng sử dụng thực phẩm, công thức.                                    |
| US05 / US07 | Quản lý thay phiên bản tài liệu và thao tác xóa hội thoại nếu mới hoàn thành luồng nạp, duyệt, hỏi và xem nguồn. |
| US08 / US10 | Ghi phần xem lại gợi ý đã làm và trạng thái nhật ký. Thực đơn tuần và lịch sử phiên bản nằm ngoài phạm vi bàn giao. |
