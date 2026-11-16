# Dữ liệu khởi đầu Phase 1

`phase1-foods.json` chứa 3 món tối giản, định lượng **sau chế biến**, cùng nguyên liệu, nguồn, macro, dị nguyên và giá giả lập. Khẩu phần do nhóm chọn. Số liệu dinh dưỡng đã đối chiếu bảng USDA SR28 (2015), không coi là số đo của món Việt Nam hoặc dữ liệu mới nhất. Chưa nhập recipes/nutrient_values: các bảng đó thuộc Phase 2. Migration V3 chỉ nạp 3 định danh để chọn dị ứng/loại trừ trong hồ sơ.

Ví dụ đối chiếu tay: 50 g trứng luộc = 155 × 0,5 = **77,5 kcal**; protein 6,29 g; carb 0,56 g; fat 5,305 g. Giá **giả lập** 6.000 VND/100 g → 3.000 VND/khẩu phần. Không dùng những giá này như giá thị trường. Bộ 3 món chưa đủ để khẳng định mọi hồ sơ đều có thực đơn khả thi.

Nguồn nhóm dị nguyên: [FDA, Food Allergies](https://www.fda.gov/food/buy-store-serve-safe-food/food-allergies-what-you-need-know). EGG chỉ nhận diện thành phần trứng; mảng rỗng của cơm không bảo đảm không nhiễm chéo hoặc không có dị ứng khác. Dị ứng không ánh xạ được phải ghi ở trường chưa rõ.

## PDF chuẩn bị cho RAG

- File: `fda-food-allergies-2024.pdf`, 4 trang có lớp văn bản.
- Tiêu đề: **Food Allergies: What You Need to Know**; cơ quan: FDA; mã CFS26, năm 2024.
- URL: https://www.fda.gov/media/180903/download
- SHA-256: `6b73da78a2397b31683e7bad11fa8fea057a8973695d639237de2c428d14409a`.
- Đây là tài liệu chuẩn bị và kiểm tra đọc PDF; **chưa nạp embedding, chưa được kích hoạt trong RAG**. Các đoạn về điều trị nằm ngoài phạm vi chatbot của đồ án.

Ba câu hỏi kiểm tra (số trang bắt đầu từ 1):

| Câu hỏi | Đáp án đối chiếu | Trang |
|---|---|---|
| Theo tài liệu, mè/vừng thuộc danh sách dị nguyên chính không? | Có, là mục thứ chín. | 1 |
| Đậu phộng và đậu nành có trong danh sách không? | Cả hai có tên trong danh sách. | 1 |
| Có thể nêu nguồn dị nguyên ở đâu trên nhãn? | Trong ngoặc sau tên thành phần, hoặc thông báo “Contains” cạnh/sau danh sách thành phần. | 2 |

Các đáp án kiểm tra khả năng tìm đúng đoạn/trang; không phải kết luận y khoa riêng cho người dùng.
