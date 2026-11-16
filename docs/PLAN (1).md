
# Đặc tả và kế hoạch xây dựng website tư vấn dinh dưỡng cá nhân hóa tích hợp Chatbot RAG

## 1. Mục tiêu, phạm vi và tác nhân

### 1.1. Cơ sở và mục tiêu

Kế hoạch dựa trên toàn bộ 7 trang của :codex-file-citation{path="C:/Users/toiye/Downloads/De_Cuong_TTCS_Nhom_9.docx.pdf" purpose="source"} và các lựa chọn đã thống nhất.

Xây dựng một **đồ án hoàn chỉnh từ đầu**, có thể chạy thử, trình diễn và đánh giá. Tài liệu này là căn cứ chung cho:

- **Agent triển khai:** xây dựng hệ thống, kiểm thử và bàn giao.
- **Agent viết báo cáo:** trình bày yêu cầu, phân tích, thiết kế, cài đặt và kết quả thực nghiệm.
- **Người nghiệm thu:** đối chiếu chức năng và bằng chứng kiểm thử theo mã yêu cầu.

Các quyết định đã chốt:

| Nội dung | Quyết định |
|---|---|
| Frontend | Next.js, TypeScript, Tailwind CSS |
| Backend | Java, Spring Boot, RESTful API |
| Dữ liệu | PostgreSQL và pgvector |
| AI | OpenAI; RAG sử dụng kho tài liệu được quản trị |
| Tạo thực đơn | AI đề xuất; Backend kiểm tra trước khi lưu |
| Thời lượng thực đơn | 1 ngày hoặc 7 ngày |
| Đối tượng tạo thực đơn | Người từ 18 tuổi, tự khai báo khỏe mạnh, không mang thai/cho con bú, không cần chế độ ăn điều trị |
| Quyền xem dữ liệu cá nhân của Admin | Giới hạn trong ngữ cảnh phản hồi người dùng cho phép chia sẻ; ghi nhật ký truy cập |
| Ngôn ngữ, đơn vị | Tiếng Việt; kg, cm, g, ml, kcal; ngân sách VND/ngày |

**Ngoài phạm vi:** chẩn đoán, kê đơn, điều trị, tư vấn trực tiếp với bác sĩ, thanh toán, giao đồ ăn, ứng dụng di động riêng, nhận dạng món ăn bằng ảnh và OCR tài liệu scan.

Người ngoài phạm vi cá nhân hóa vẫn được xem kiến thức chung; hệ thống không tạo thực đơn hoặc đưa ra mục tiêu dinh dưỡng cá nhân cho nhóm này.

### 1.2. Tác nhân

| Mã | Tác nhân | Quyền và trách nhiệm |
|---|---|---|
| ACT-01 | Khách chưa đăng nhập | Xem giới thiệu, phạm vi sử dụng, chính sách dữ liệu; đăng ký, đăng nhập. |
| ACT-02 | Người dùng | Quản lý dữ liệu của mình; tính chỉ số; tạo, đổi và xem lịch sử thực đơn; theo dõi tiến trình; dùng Chatbot và gửi phản hồi. |
| ACT-03 | Quản trị viên | Quản lý tài khoản, danh mục thực phẩm/món ăn, tài liệu RAG, phản hồi và trạng thái hệ thống. |
| ACT-04 | Dịch vụ OpenAI | Cung cấp embedding và sinh nội dung theo yêu cầu của Backend; không trực tiếp truy cập cơ sở dữ liệu hoặc thay đổi thực đơn. |

Khách là trạng thái chưa xác thực, không phải một vai trò lưu trong tài khoản. Hai vai trò tài khoản là `USER` và `ADMIN`.

Chatbot, bộ kiểm tra thực đơn và PostgreSQL là thành phần bên trong hệ thống, không mô hình hóa thành tác nhân bên ngoài. Không thêm vai trò chuyên gia dinh dưỡng khi chưa có nghiệp vụ tương ứng.

## 2. Yêu cầu chức năng và quy tắc nghiệp vụ

### 2.1. Danh mục yêu cầu chức năng

Toàn bộ yêu cầu dưới đây thuộc phạm vi bàn giao của đồ án.

| Mã | Chức năng | Hành vi và điều kiện nghiệm thu |
|---|---|---|
| FR-01 | Đăng ký, xác thực | Đăng ký bằng email và mật khẩu; email không trùng; đăng nhập, đăng xuất, đổi mật khẩu. Tài khoản khóa không thể tiếp tục sử dụng phiên đăng nhập. |
| FR-02 | Phân quyền | Kiểm tra vai trò và chủ sở hữu dữ liệu ở Backend. Người dùng không đọc hoặc sửa dữ liệu người khác bằng cách đổi ID trên URL/API. |
| FR-03 | Hồ sơ dinh dưỡng | Lưu ngày sinh, giới tính dùng cho công thức, chiều cao, cân nặng, mức vận động, mục tiêu, dị ứng, thực phẩm không thích, chế độ ăn, ngân sách và số bữa. Kiểm tra điều kiện cá nhân hóa. |
| FR-04 | Chỉ số cá nhân | Backend tính BMI, BMR ước tính, TDEE, kcal mục tiêu, macro và nước tham khảo. Hiển thị đơn vị, thời điểm tính, công thức và phiên bản quy tắc. |
| FR-05 | Tra cứu thực phẩm, món ăn | Tìm kiếm, lọc theo nhóm và chế độ ăn; xem thành phần, khẩu phần, dinh dưỡng, chất gây dị ứng, chi phí ước tính và nguồn dữ liệu. |
| FR-06 | Tạo thực đơn bằng AI | Chọn ngày bắt đầu, thời lượng 1/7 ngày và 3–5 bữa/ngày. AI chọn món trong danh mục hợp lệ; Backend kiểm tra toàn bộ trước khi công bố kết quả. |
| FR-07 | Điều chỉnh thực đơn | Đổi món, đổi bữa hoặc điều chỉnh khẩu phần. Backend tính lại và kiểm tra toàn bộ ngày bị ảnh hưởng; thất bại thì giữ nguyên bản trước. |
| FR-08 | Lịch sử thực đơn | Lưu, xem lại, đặt thực đơn làm kế hoạch sử dụng cho các ngày tương ứng và lưu các phiên bản chỉnh sửa. Không ghi đè lịch sử khi hồ sơ thay đổi. |
| FR-09 | Theo dõi tiến trình | Ghi cân nặng, lượng nước và bữa đã dùng theo ngày; sửa khẩu phần thực tế đã ghi; hiển thị biểu đồ và so sánh với mục tiêu. |
| FR-10 | Hội thoại Chatbot | Tạo hội thoại, hỏi đáp nhiều lượt, xem và xóa lịch sử của mình. Hỗ trợ kiến thức dinh dưỡng, giải thích thực đơn và đề xuất thay thế. |
| FR-11 | Truy xuất và dẫn nguồn | Câu trả lời kiến thức sử dụng RAG hiển thị tài liệu, phiên bản, trang và đoạn liên quan. Khi thiếu bằng chứng, thông báo chưa đủ căn cứ. |
| FR-12 | Đề xuất thay thế qua Chatbot | Gợi ý món thay thế theo hồ sơ hiện tại. Chỉ thay đổi thực đơn khi người dùng chọn áp dụng và Backend kiểm tra đạt. |
| FR-13 | Phản hồi | Đánh giá câu trả lời, gửi nội dung phản ánh; xem trước và đồng ý chia sẻ các tin nhắn/hồ sơ cần thiết; theo dõi trạng thái xử lý. |
| FR-14 | Quản trị tài khoản | Admin tìm tài khoản, xem trạng thái, khóa/mở khóa. Không được xem mật khẩu hoặc tự do duyệt hồ sơ sức khỏe/hội thoại. |
| FR-15 | Quản trị thực phẩm, món ăn | Tạo, sửa, ngừng sử dụng thực phẩm, nguyên liệu, món ăn, khẩu phần, giá và nguồn. Dữ liệu đang được lịch sử tham chiếu không bị xóa mất dấu vết. |
| FR-16 | Quản trị tài liệu RAG | Tải PDF, khai báo nguồn, xử lý, xem lỗi, duyệt kích hoạt, thay phiên bản và thu hồi. Chỉ tài liệu đã kích hoạt được truy xuất. |
| FR-17 | Xử lý phản hồi | Admin xem dữ liệu đã được chia sẻ cho từng phản hồi, ghi kết quả xử lý và đổi trạng thái: mới, đang xử lý, đã giải quyết. |
| FR-18 | Theo dõi vận hành | Hiển thị trạng thái xử lý tài liệu, tác vụ tạo thực đơn, lỗi AI, thời gian phản hồi và lượng sử dụng AI; không đưa dữ liệu cá nhân vào bảng thống kê chung. |
| FR-19 | Quyền riêng tư | Người dùng xem nội dung được gửi cho AI, xóa hội thoại, thu hồi quyền chia sẻ phản hồi và yêu cầu xóa tài khoản. Backend thực thi trên dữ liệu của ứng dụng. |

Không triển khai đăng nhập mạng xã hội và khôi phục mật khẩu qua email trong phiên bản đồ án; không mô tả chúng là chức năng đã có.

### 2.2. Hồ sơ và tính toán

**Quy tắc dữ liệu:**

- Một tài khoản có một hồ sơ hiện tại và nhiều phiên bản lịch sử.
- Chế độ ăn ban đầu: ăn thông thường, chay có trứng/sữa, thuần chay.
- Dị ứng và thực phẩm loại trừ phải ánh xạ đến nguyên liệu hoặc nhóm nguyên liệu chuẩn hóa.
- Nếu người dùng nhập một dị ứng chưa ánh xạ được, yêu cầu làm rõ trước khi tạo thực đơn; không bỏ qua.
- Thông tin thiếu hoặc ngoài miền tính toán hợp lệ phải báo tại trường dữ liệu tương ứng.
- Thời điểm lưu theo UTC; ngày thực đơn và nhật ký sử dụng múi giờ `Asia/Ho_Chi_Minh`.

**Công thức cốt lõi:**

- BMI = cân nặng kg / chiều cao m².
- Dùng Mifflin–St Jeor để ước tính năng lượng nghỉ: `10 × kg + 6,25 × cm − 5 × tuổi + hệ số`, với hệ số `+5` hoặc `−161` theo nhóm giới tính của công thức.
- Giao diện ghi rõ đây là **ước tính**, không phải phép đo chuyển hóa. Báo cáo phân biệt thuật ngữ BMR trong đề cương với REE/RMR của công thức gốc. [Nguồn nghiên cứu Mifflin–St Jeor](https://pubmed.ncbi.nlm.nih.gov/2305711/).

Các tham số dưới đây là **mặc định bổ sung để triển khai đồ án**, không được viết thành khuyến nghị điều trị:

| Tham số | Mặc định |
|---|---|
| Hệ số vận động | 1,2; 1,375; 1,55; 1,725; 1,9, tương ứng 5 mức có mô tả trong giao diện |
| TDEE | Năng lượng nghỉ ước tính × hệ số vận động |
| Kcal mục tiêu | Duy trì: TDEE; giảm cân: 90% TDEE; tăng cân/tăng cơ: 110% TDEE |
| Tỷ lệ P/C/F tham khảo | 20/50/30; mục tiêu tăng cơ: 25/45/30 |
| Quy đổi macro | Protein và carbohydrate: 4 kcal/g; fat: 9 kcal/g |
| Nước tham khảo | 30 ml/kg/ngày, gắn nhãn ước tính đơn giản của bản demo |

Lưu các tham số trong bộ quy tắc có phiên bản. Agent viết báo cáo phải phân biệt công thức có nguồn nghiên cứu với tham số do thiết kế lựa chọn; không suy diễn rằng mọi giá trị trên đều đã được xác nhận chuyên môn.

Cân nặng mới trong nhật ký cập nhật hồ sơ và tạo phiên bản chỉ số mới. Thực đơn đã lưu giữ mục tiêu cũ, hiển thị rõ thời điểm tính; chỉ áp dụng mục tiêu mới khi người dùng tạo hoặc điều chỉnh kế hoạch.

### 2.3. Dữ liệu thực phẩm và ràng buộc thực đơn

Mỗi thực phẩm có số liệu trên **100 g phần ăn được**, trạng thái sống/chín hoặc chế biến, nguồn và ngày cập nhật. Công thức món ăn gồm nguyên liệu, khối lượng, số khẩu phần và thông tin thành phẩm. Không trộn số liệu sống/chín hoặc tự chuyển ml thành g khi thiếu hệ số.

Backend là nguồn quyết định đối với tổng kcal, macro, dị ứng và chi phí; không sử dụng tổng số do AI tự khai báo.

**Ràng buộc bắt buộc cho từng ngày:**

1. Món và nguyên liệu tồn tại, đang được phép sử dụng.
2. Không chứa dị nguyên hoặc thực phẩm người dùng đã loại trừ.
3. Phù hợp chế độ ăn.
4. Đúng số bữa.
5. Chi phí tính toán không vượt ngân sách ngày.
6. Tổng kcal lệch không quá ±5% so với kcal mục tiêu của ngày.
7. Khẩu phần hợp lệ; mặc định 0,5–3 khẩu phần chuẩn, bước 0,25, trừ khi món có giới hạn riêng.

**Ưu tiên mềm:** gần tỷ lệ macro mục tiêu, đa dạng món và giảm lặp món trong tuần. Không biến các ưu tiên này thành lý do từ chối một thực đơn đáp ứng toàn bộ ràng buộc bắt buộc.

Sai số ±5% là sai lệch giữa **kcal tính từ danh mục** và **mục tiêu ước tính**, không phải chứng minh độ chính xác sinh lý hoặc độ chính xác của món ăn ngoài thực tế.

### 2.4. Luồng AI tạo và điều chỉnh thực đơn

1. Kiểm tra quyền, điều kiện sử dụng và phiên bản hồ sơ.
2. Backend lọc danh mục món phù hợp, lấy dữ liệu dinh dưỡng và chi phí.
3. Gửi danh mục ứng viên cùng ràng buộc cho AI.
4. AI trả về ngày, bữa, `recipeId`, số khẩu phần và giải thích ngắn theo cấu trúc đã định nghĩa.
5. Backend kiểm tra định dạng, ID và tính lại toàn bộ ràng buộc.
6. Nếu chưa đạt, gửi các lỗi kiểm tra cho AI sửa; tối đa **3 lượt gọi cho một tác vụ**, gồm lượt đầu.
7. Chỉ lưu thực đơn thành công khi **tất cả các ngày** đều đạt.
8. Khi hết lượt hoặc quá 180 giây, kết thúc với thông báo “chưa tìm được thực đơn đáp ứng yêu cầu”; không tự nới ngân sách, bỏ dị ứng hoặc lưu kết quả chưa đạt.

Sử dụng Structured Outputs để ràng buộc cấu trúc đầu ra; vẫn phải kiểm tra nghiệp vụ độc lập. [Tài liệu OpenAI về Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs).

Tác vụ chạy nền, giao diện hiển thị trạng thái. Tác vụ đã hết hạn không được ghi kết quả đến muộn. Thực đơn tuần không được báo thành công nếu chỉ tạo được một phần.

Khi đổi món hoặc khẩu phần:

- Kiểm tra lại cả ngày, không chỉ món thay thế.
- Dùng phiên bản để phát hiện chỉnh sửa đồng thời.
- Nếu dữ liệu hồ sơ/danh mục thay đổi trong lúc xử lý, kiểm tra lại trước khi lưu.
- Lịch sử giữ nguyên bản chụp dữ liệu cũ; thực đơn xung đột với dị ứng hiện tại được đánh dấu không còn phù hợp.
- Việc áp dụng lại hoặc sao chép thực đơn cũ luôn kiểm tra theo hồ sơ hiện tại.

### 2.5. Luồng RAG và giới hạn Chatbot

**Nạp tài liệu:**

- Nhận PDF có lớp văn bản, tối đa 20 MB/tệp; báo rõ nếu tài liệu scan không đọc được.
- Lưu tên tài liệu, tổ chức/tác giả, năm, URL nguồn nếu có, phiên bản và dấu kiểm tra tệp.
- Tách nội dung theo trang và đoạn; cấu hình khởi đầu 500 token/đoạn, chồng lấn 100 token.
- Tạo embedding, lưu pgvector; Admin xem kết quả rồi kích hoạt.
- Tài liệu bị thu hồi không được dùng cho câu trả lời mới; lịch sử giữ dấu vết nguồn đã sử dụng.

**Trả lời:**

- Truy xuất tối đa 5 đoạn phù hợp từ tài liệu đang hoạt động.
- Kết hợp câu hỏi, ngữ cảnh hội thoại giới hạn và phần hồ sơ cần thiết.
- Mỗi trích dẫn phải được Backend đối chiếu với đoạn thực sự được truy xuất.
- Nếu không có bằng chứng phù hợp, thông báo giới hạn; không tự tạo tài liệu tham khảo.
- Giải thích số liệu thực đơn dựa trên dữ liệu Backend; kiến thức dinh dưỡng dựa trên tài liệu RAG.
- Không chẩn đoán, kê đơn hoặc đưa chế độ ăn điều trị.
- Nội dung tài liệu và tin nhắn không được phép thay đổi quyền truy cập hay ra lệnh sửa dữ liệu.
- Không gửi tên, email, mật khẩu hoặc toàn bộ lịch sử người dùng cho AI.

“Có trích dẫn” và “trích dẫn thực sự hỗ trợ nội dung” phải được đánh giá riêng.

### 2.6. Theo dõi tiến trình và quyền Admin

Nhật ký gồm cân nặng, lượng nước và các bữa đã dùng. Tổng kcal đã ghi nhận được tính từ món và khẩu phần người dùng xác nhận; giao diện không gọi đây là phép đo chính xác lượng ăn thực tế.

Khi gửi phản hồi, người dùng xem trước và chọn chia sẻ bản chụp hồ sơ cùng các tin nhắn liên quan. Admin chỉ xem bản chụp đó trong phiếu phản hồi; mọi lần đọc được ghi nhật ký gồm người truy cập, thời gian, mục đích và đối tượng.

Thu hồi chia sẻ phải chặn các lần đọc tiếp theo. Khi xóa tài khoản, xóa hoặc ẩn danh hóa dữ liệu cá nhân liên quan trong ứng dụng; nhật ký kỹ thuật còn lại không chứa hồ sơ hay nội dung hội thoại.

## 3. Yêu cầu phi chức năng và tiêu chí nghiệm thu

Các ngưỡng dưới đây là **mục tiêu kiểm thử**, không phải kết quả đã đạt.

| Mã | Yêu cầu | Tiêu chí kiểm chứng |
|---|---|---|
| NFR-01 | Độ đúng thực đơn | 100% thực đơn được lưu thành công vượt qua kiểm tra ràng buộc; kcal từng ngày trong ±5%. |
| NFR-02 | Hiệu năng Chatbot | Mục tiêu p95 thời gian hoàn tất câu trả lời dưới 5 giây trên 100 yêu cầu, 5 yêu cầu đồng thời; đo riêng thời gian xuất hiện nội dung đầu tiên. |
| NFR-03 | Hiệu năng thông thường | p95 API đọc/ghi thông thường dưới 500 ms với 20 người dùng đồng thời, không gồm AI và xử lý tài liệu; ghi cấu hình máy và dữ liệu đo. |
| NFR-04 | Phân quyền, xác thực | Mật khẩu băm bằng thuật toán thích hợp; cookie phiên HttpOnly, Secure khi chạy HTTPS; chống CSRF; kiểm tra quyền ở mọi API dữ liệu riêng. |
| NFR-05 | Bảo vệ dữ liệu | Khóa API chỉ ở Backend; không ghi mật khẩu, token, hồ sơ hoặc hội thoại nguyên văn vào log chung; truy cập dữ liệu phản hồi có nhật ký. |
| NFR-06 | Tin cậy khi AI lỗi | Chatbot dừng chờ sau 30 giây và cho phép thử lại; tác vụ thực đơn tối đa 180 giây. Không trả thành công giả, không ghi dữ liệu một phần. |
| NFR-07 | Chất lượng nguồn | 100% trích dẫn hiển thị ánh xạ đến nguồn đã truy xuất. Đánh giá riêng tỷ lệ phát biểu được nguồn hỗ trợ và khả năng từ chối khi thiếu nguồn. |
| NFR-08 | Giao diện | Hoạt động tại chiều rộng 360, 768 và 1440 px; dùng được bằng bàn phím; biểu mẫu có nhãn và lỗi rõ ràng; có trạng thái tải/rỗng/lỗi. |
| NFR-09 | Nhất quán dữ liệu | Giao dịch cho thao tác tạo/đổi thực đơn; khóa ngoại; quản lý phiên bản; không ghi đè khi hai yêu cầu sửa đồng thời. |
| NFR-10 | Khả năng bảo trì | Tách module nghiệp vụ; có migration, tài liệu API, cấu hình môi trường mẫu và kiểm thử tự động; không hardcode khóa hoặc model trong nghiệp vụ. |
| NFR-11 | Tái lập và quan sát | Ghi mã yêu cầu, phiên bản công thức, prompt, model và danh mục dùng trong lần sinh; có mã truy vết lỗi và số liệu thời gian/token. |
| NFR-12 | Sao lưu và triển khai | Có hướng dẫn sao lưu, khôi phục PostgreSQL và tài liệu; thực hiện ít nhất một lần khôi phục trên môi trường thử nghiệm. |

Tiêu chí dưới 5 giây áp dụng cho **Chatbot hỏi đáp**, không áp dụng cho tác vụ tạo thực đơn tuần. Nếu chưa đạt, báo cáo phải ghi kết quả thực đo và hạn chế; không thay thời gian hoàn tất bằng thời gian nhận token đầu tiên để tuyên bố đạt.

## 4. Thiết kế và trình tự triển khai cho agent

### 4.1. Kiến trúc

Dùng kiến trúc ba tầng với **một Backend chia module**, tránh phát sinh nhiều dịch vụ độc lập cho đồ án:

- **Presentation:** Next.js, giao diện Người dùng và Admin.
- **Logic:** Spring Boot với các module xác thực, hồ sơ, tính toán, thực phẩm, thực đơn, tiến trình, Chatbot/RAG và quản trị.
- **Data:** PostgreSQL/pgvector; vùng lưu tệp PDF không truy cập công khai.
- **Tích hợp ngoài:** OpenAI qua adapter riêng, cấu hình model sinh và embedding bằng biến môi trường.

Frontend và API được phục vụ cùng origin qua reverse proxy. Dùng Spring Security và phiên đăng nhập phía server. Worker xử lý tác vụ trong ứng dụng Backend, trạng thái tác vụ lưu PostgreSQL.

Model dùng thực tế phải được ghi trong cấu hình và bằng chứng thực nghiệm. Khi đổi model embedding hoặc số chiều vector, tạo lại chỉ mục tương ứng, không trộn vector không tương thích.

### 4.2. Nhóm dữ liệu

| Nhóm | Thực thể chính |
|---|---|
| Tài khoản | Account, Session |
| Hồ sơ | NutritionProfileVersion, CalculationSnapshot, NutritionRuleVersion |
| Danh mục | Food, Allergen, Recipe, RecipeIngredient, phiên bản dữ liệu và giá |
| Thực đơn | GenerationJob, MealPlanVersion, PlanDay, Meal, MealItem |
| Tiến trình | WeightEntry, WaterEntry, MealConsumption |
| Hội thoại | Conversation, Message, Citation |
| RAG | DocumentVersion, DocumentChunk, Embedding |
| Quản trị | Feedback, SharedContextSnapshot, AuditLog |

Mọi dữ liệu riêng có chủ sở hữu. Thực đơn lưu bản chụp mục tiêu, dinh dưỡng và giá tại thời điểm tạo để có thể tái lập kết quả.

### 4.3. Giao diện API và giao diện người dùng

API dùng tiền tố `/api/v1`, tài liệu hóa bằng OpenAPI:

| Nhóm API | Trách nhiệm |
|---|---|
| `/auth/*` | Đăng ký, đăng nhập, đăng xuất, đổi mật khẩu |
| `/me/profile`, `/me/metrics` | Hồ sơ và chỉ số của người đang đăng nhập |
| `/foods`, `/recipes` | Tra cứu danh mục |
| `/meal-plan-jobs` | Tạo tác vụ và đọc trạng thái |
| `/meal-plans/*` | Xem, áp dụng, đổi món, điều chỉnh và lịch sử |
| `/progress/*` | Nhật ký, tổng hợp tiến trình |
| `/conversations/*` | Hội thoại, tin nhắn và nguồn |
| `/feedback/*` | Phản hồi, chia sẻ và thu hồi chia sẻ |
| `/admin/*` | Quản trị danh mục, tài liệu, tài khoản và phản hồi |

Tạo thực đơn trả `202` cùng `jobId`. Tác vụ có các trạng thái `QUEUED`, `RUNNING`, `SUCCEEDED`, `FAILED`, `TIMED_OUT`; chỉ `SUCCEEDED` trả `mealPlanId`.

Lỗi dùng cấu trúc chung gồm `code`, `message`, `fieldErrors`, `traceId`. Người dùng không được tự gửi `userId` để quyết định chủ sở hữu.

Các màn hình bắt buộc: giới thiệu/xác thực; khai báo hồ sơ; tổng quan chỉ số; danh mục món; thực đơn ngày/tuần và lịch sử; tiến trình; Chatbot và nguồn; phản hồi; khu quản trị.

### 4.4. Thứ tự thực hiện

| Giai đoạn | Công việc | Điều kiện hoàn thành |
|---|---|---|
| 1. Nền tảng đặc tả | Tạo danh mục yêu cầu, mô hình dữ liệu, OpenAPI, sơ đồ kiến trúc và bộ quy tắc có phiên bản | Mọi FR/NFR có tiêu chí và ca kiểm thử tương ứng |
| 2. Nền tảng ứng dụng | Khởi tạo Frontend/Backend, migration, xác thực, phân quyền, cấu hình chạy bằng Docker Compose | Đăng nhập và kiểm thử cách ly dữ liệu đạt |
| 3. Nghiệp vụ nền | Hồ sơ, tính toán, thực phẩm/món ăn, dữ liệu mẫu và quản trị danh mục | Tính toán tái lập được; truy xuất rõ nguồn dữ liệu |
| 4. Thực đơn AI | Adapter AI, đầu ra có cấu trúc, worker, bộ kiểm tra, đổi món và lịch sử | Không lưu thực đơn sai; xử lý được lỗi/timeout |
| 5. Chatbot RAG | Nạp PDF, embedding, truy xuất, dẫn nguồn, hội thoại và phản hồi có giới hạn quyền | Nguồn hợp lệ; không rò rỉ dữ liệu qua hội thoại/phản hồi |
| 6. Hoàn thiện | Tiến trình, responsive, thống kê vận hành và quyền xóa dữ liệu | Các luồng người dùng và Admin hoạt động xuyên suốt |
| 7. Đánh giá, bàn giao | Kiểm thử, đo AI thực, hoàn thiện UML, hướng dẫn cài đặt và bằng chứng báo cáo | Có kết quả thực đo và danh sách tiêu chí đạt/chưa đạt |

Bộ dữ liệu trình diễn đặt mục tiêu tối thiểu 100 thực phẩm, 50 món và 10 tài liệu dinh dưỡng có nguồn xác minh. Giá minh họa phải ghi là giá giả lập; không bịa thành phần dinh dưỡng để đủ số lượng. Nếu thiếu dữ liệu đáng tin cậy, ghi nhận phần thiếu thay vì tạo nguồn giả.

## 5. Kiểm thử, tài liệu báo cáo và quy tắc bàn giao

### 5.1. Các nhóm kiểm thử bắt buộc

| Mã | Kịch bản |
|---|---|
| TC-01 | Đăng ký trùng email, sai mật khẩu, đăng xuất, tài khoản khóa và truy cập sai vai trò |
| TC-02 | Người A thử đọc/sửa hồ sơ, thực đơn, hội thoại, phản hồi của người B |
| TC-03 | Hồ sơ thiếu dữ liệu, chưa đủ tuổi, thuộc nhóm ngoài phạm vi hoặc dị ứng chưa ánh xạ |
| TC-04 | Đối chiếu BMI/năng lượng nghỉ/TDEE bằng bộ dữ liệu tính tay; kiểm tra đơn vị và làm tròn |
| TC-05 | Thực đơn 1/7 ngày: kiểm tra kcal từng ngày, dị ứng, chế độ ăn, ngân sách và số bữa |
| TC-06 | AI trả ID không tồn tại, khẩu phần sai, dữ liệu thiếu, từ chối sinh hoặc phản hồi lỗi |
| TC-07 | Ngân sách quá thấp/danh mục quá hẹp: kết thúc có giải thích, không lưu kết quả sai |
| TC-08 | Đổi món làm vượt kcal/ngân sách; hai lần sửa đồng thời; thay đổi hồ sơ khi AI đang chạy |
| TC-09 | Thêm dị ứng sau khi tạo thực đơn: lịch sử còn nguyên nhưng chặn áp dụng lại nếu không phù hợp |
| TC-10 | Tài liệu scan, tệp lỗi, nguồn bị thu hồi, kho rỗng và câu hỏi ngoài kho tri thức |
| TC-11 | Trích dẫn giả, nguồn không hỗ trợ câu trả lời và nội dung cố ép Chatbot bỏ qua quy tắc |
| TC-12 | Admin chỉ đọc ngữ cảnh đã chia sẻ; thu hồi chia sẻ chặn truy cập tiếp theo; có audit log |
| TC-13 | Ghi/sửa cân nặng, nước, bữa đã dùng; tính tổng theo ngày và cập nhật phiên bản chỉ số |
| TC-14 | Timeout, mất kết nối, khởi động lại Backend, thao tác gửi trùng và kết quả AI đến muộn |
| TC-15 | Responsive, bàn phím, đo hiệu năng, xóa dữ liệu và khôi phục bản sao lưu |

Kiểm thử tự động dùng AI giả lập để tái lập lỗi. Đánh giá thực nghiệm chạy riêng với OpenAI thật:

- **30 hồ sơ mẫu:** bao gồm các mục tiêu, chế độ ăn, dị ứng và ngân sách khác nhau.
- **50 câu hỏi RAG:** câu có đáp án, thiếu nguồn, ngoài phạm vi và câu cố tác động quy tắc.
- **100 yêu cầu đo thời gian:** công bố model, cấu hình, số đồng thời và phân bố độ trễ.

Đo cả **tỷ lệ sinh thành công** và **tỷ lệ thực đơn đã lưu đáp ứng ràng buộc**. Không đạt tiêu chí bằng cách từ chối mọi yêu cầu khó.

### 5.2. Hồ sơ UML và liên kết yêu cầu

Agent triển khai phải tạo:

- Use Case tổng quan và đặc tả các use case cốt lõi.
- Activity Diagram: cập nhật hồ sơ, tạo/đổi thực đơn, hỏi đáp RAG, nạp tài liệu, xử lý phản hồi.
- Sequence Diagram cho các luồng trên, có nhánh lỗi và kiểm tra quyền.
- Class Diagram mô tả mô hình miền.
- ERD với khóa chính, khóa ngoại và quan hệ phiên bản.
- Sơ đồ kiến trúc và triển khai.

Mỗi use case cần có tác nhân, tiền điều kiện, kích hoạt, luồng chính, ngoại lệ, hậu điều kiện và mã FR/NFR liên quan.

Duy trì bảng truy vết:

**Yêu cầu → Use case → Module/API → Ca kiểm thử → Bằng chứng → Mục báo cáo.**

Ví dụ: `FR-06 → UC-Tạo thực đơn → Meal Planning → TC-05/06/07 → kết quả chạy thực nghiệm → mục 4.3 và 4.6`.

### 5.3. Cấu trúc báo cáo

Giữ cấu trúc 5 chương trong đề cương:

| Chương | Nội dung và đầu vào |
|---|---|
| 1. Giới thiệu đề tài | Bối cảnh, mục tiêu, đối tượng, phạm vi, ý nghĩa và phương pháp |
| 2. Khảo sát nghiệp vụ và cơ sở công nghệ | Tác nhân, FR/NFR, giao diện, công nghệ và giới hạn hệ thống |
| 3. Phân tích và thiết kế | Use Case, đặc tả, Activity, Sequence và Class Diagram |
| 4. Kiến trúc và cài đặt | Kiến trúc ba tầng, ERD, luồng thực đơn/RAG, giao diện, cài đặt; bổ sung **4.6. Kiểm thử và đánh giá thực nghiệm** |
| 5. Kết luận và hướng phát triển | Kết quả thực đạt, hạn chế, công việc chưa đạt và hướng mở rộng |

### 5.4. Chỉ dẫn cho agent viết báo cáo

- Dùng mã FR/NFR và thuật ngữ trong đặc tả; không tự thêm chức năng đã triển khai.
- Phân biệt rõ **yêu cầu**, **thiết kế**, **đã cài đặt** và **đã kiểm thử đạt**.
- Mô tả cài đặt dựa trên mã nguồn và API thực tế; cập nhật sơ đồ khi triển khai khác thiết kế.
- Chỉ đưa số liệu hiệu năng, chất lượng RAG và tỷ lệ thành công từ bằng chứng chạy thật.
- Chỉ sử dụng ảnh chụp hệ thống thật cho phần kết quả.
- Kiểm chứng thông tin xuất bản của tài liệu tham khảo trong đề cương trước khi trích dẫn.
- Không trình bày cấu hình thử nghiệm như một chuẩn dinh dưỡng hoặc kết quả nghiên cứu y khoa.
- Khi chưa có bằng chứng, ghi “chưa đánh giá”; không tự tạo kết quả để hoàn thiện chương kết luận.

**Bộ bàn giao cuối cùng:** mã nguồn chạy được, migration và dữ liệu mẫu có nguồn, cấu hình mẫu, hướng dẫn cài đặt/khôi phục, OpenAPI, bộ sơ đồ UML/ERD, bảng truy vết yêu cầu, kết quả kiểm thử, ảnh giao diện thực tế và tài liệu báo cáo bám đúng trạng thái hệ thống.

