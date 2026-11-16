-- Three verified identities for Phase 1 exclusions. Nutrition/recipe fixtures live in docs/fixtures.
-- Prices below are explicitly simulated demo prices (VND / 100 g cooked edible portion).
INSERT INTO data_sources VALUES
('20000000-0000-0000-0000-000000000001','USDA SR28 (2015), Dairy and Egg Products, NDB 01129, PDF page 384','USDA ARS','https://www.ars.usda.gov/ARSUserFiles/80400525/Data/SR/SR28/reports/sr28fg01.pdf#page=384','2026-11-16T08:00:00Z'),
('20000000-0000-0000-0000-000000000002','USDA SR28 (2015), Cereal Grains and Pasta, NDB 20045/20037, PDF pages 325/277','USDA ARS','https://www.ars.usda.gov/ARSUserFiles/80400535/Data/SR/SR28/reports/sr28fg20.pdf','2026-11-16T08:00:00Z');
INSERT INTO ingredients VALUES
('30000000-0000-0000-0000-000000000001','20000000-0000-0000-0000-000000000001','30000000-0000-0000-0000-000000000001',1,'Trứng luộc chín','Trứng','COOKED_HARD_BOILED','["EGG"]',6000,'2026-11-16T08:00:00Z','ACTIVE'),
('30000000-0000-0000-0000-000000000002','20000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000002',1,'Cơm gạo trắng hạt dài (enriched)','Ngũ cốc','COOKED','[]',2000,'2026-11-16T08:00:00Z','ACTIVE'),
('30000000-0000-0000-0000-000000000003','20000000-0000-0000-0000-000000000002','30000000-0000-0000-0000-000000000003',1,'Cơm gạo lứt hạt dài','Ngũ cốc','COOKED','[]',3000,'2026-11-16T08:00:00Z','ACTIVE');
