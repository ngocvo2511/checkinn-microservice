-- Refactor cities table into provinces and cities

-- 1. Create provinces table
CREATE TABLE provinces (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    code VARCHAR(10) UNIQUE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    hotel_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_provinces_name UNIQUE (name),
    CONSTRAINT uk_provinces_code UNIQUE (code)
);

-- 2. Insert all 63 provinces/special cities into provinces table
-- Miền Bắc
INSERT INTO provinces (id, name, code, latitude, longitude, created_at) VALUES
(gen_random_uuid(), 'Hà Nội', 'HN', 21.0285, 105.8542, NOW()),
(gen_random_uuid(), 'Hà Giang', 'HG', 22.8026, 104.9784, NOW()),
(gen_random_uuid(), 'Cao Bằng', 'CB', 22.6356, 106.2522, NOW()),
(gen_random_uuid(), 'Bắc Kạn', 'BK', 22.3032, 105.8768, NOW()),
(gen_random_uuid(), 'Tuyên Quang', 'TQ', 21.7767, 105.2280, NOW()),
(gen_random_uuid(), 'Lào Cai', 'LC', 22.4809, 103.9755, NOW()),
(gen_random_uuid(), 'Điện Biên', 'DB', 21.8042, 103.1076, NOW()),
(gen_random_uuid(), 'Lai Châu', 'LCH', 22.3864, 103.4702, NOW()),
(gen_random_uuid(), 'Sơn La', 'SL', 21.1022, 103.7289, NOW()),
(gen_random_uuid(), 'Yên Bái', 'YB', 21.7168, 104.8986, NOW()),
(gen_random_uuid(), 'Hòa Bình', 'HB', 20.8142, 105.3380, NOW()),
(gen_random_uuid(), 'Thái Nguyên', 'TN', 21.5671, 105.8252, NOW()),
(gen_random_uuid(), 'Lạng Sơn', 'LS', 21.8536, 106.7610, NOW()),
(gen_random_uuid(), 'Quảng Ninh', 'QN', 21.0064, 107.2925, NOW()),
(gen_random_uuid(), 'Bắc Giang', 'BG', 21.2819, 106.1979, NOW()),
(gen_random_uuid(), 'Phú Thọ', 'PT', 21.2685, 105.2045, NOW()),
(gen_random_uuid(), 'Vĩnh Phúc', 'VP', 21.3609, 105.5474, NOW()),
(gen_random_uuid(), 'Bắc Ninh', 'BN', 21.1214, 106.1110, NOW()),
(gen_random_uuid(), 'Hải Dương', 'HD', 20.9373, 106.3145, NOW()),
(gen_random_uuid(), 'Hải Phòng', 'HP', 20.8449, 106.6881, NOW()),
(gen_random_uuid(), 'Hưng Yên', 'HY', 20.6464, 106.0511, NOW()),
(gen_random_uuid(), 'Thái Bình', 'TB', 20.4463, 106.3365, NOW()),
(gen_random_uuid(), 'Hà Nam', 'HNA', 20.5835, 105.9230, NOW()),
(gen_random_uuid(), 'Nam Định', 'ND', 20.4388, 106.1621, NOW()),
(gen_random_uuid(), 'Ninh Bình', 'NB', 20.2506, 105.9745, NOW()),

-- Miền Trung
(gen_random_uuid(), 'Thanh Hóa', 'TH', 19.8067, 105.7851, NOW()),
(gen_random_uuid(), 'Nghệ An', 'NA', 18.6791, 105.6811, NOW()),
(gen_random_uuid(), 'Hà Tĩnh', 'HT', 18.3559, 105.9058, NOW()),
(gen_random_uuid(), 'Quảng Bình', 'QB', 17.4679, 106.6226, NOW()),
(gen_random_uuid(), 'Quảng Trị', 'QT', 16.7943, 107.1854, NOW()),
(gen_random_uuid(), 'Thừa Thiên Huế', 'HUE', 16.4637, 107.5909, NOW()),
(gen_random_uuid(), 'Đà Nẵng', 'DN', 16.0544, 108.2022, NOW()),
(gen_random_uuid(), 'Quảng Nam', 'QNM', 15.5394, 108.0191, NOW()),
(gen_random_uuid(), 'Quảng Ngãi', 'QNG', 15.1214, 108.8044, NOW()),
(gen_random_uuid(), 'Bình Định', 'BD', 13.7830, 109.2196, NOW()),
(gen_random_uuid(), 'Phú Yên', 'PY', 13.0882, 109.0929, NOW()),
(gen_random_uuid(), 'Khánh Hòa', 'KH', 12.2585, 109.0526, NOW()),
(gen_random_uuid(), 'Ninh Thuận', 'NTH', 11.6739, 108.8629, NOW()),
(gen_random_uuid(), 'Bình Thuận', 'BTH', 10.9273, 108.1017, NOW()),

-- Tây Nguyên
(gen_random_uuid(), 'Kon Tum', 'KT', 14.3497, 108.0005, NOW()),
(gen_random_uuid(), 'Gia Lai', 'GL', 13.9830, 108.1089, NOW()),
(gen_random_uuid(), 'Đắk Lắk', 'DL', 12.6667, 108.0500, NOW()),
(gen_random_uuid(), 'Đắk Nông', 'DNO', 12.2646, 107.6098, NOW()),
(gen_random_uuid(), 'Lâm Đồng', 'LD', 11.5753, 108.1429, NOW()),

-- Đông Nam Bộ
(gen_random_uuid(), 'Bình Phước', 'BP', 11.7511, 106.7234, NOW()),
(gen_random_uuid(), 'Tây Ninh', 'TNI', 11.3351, 106.1098, NOW()),
(gen_random_uuid(), 'Bình Dương', 'BDU', 11.1653, 106.6401, NOW()),
(gen_random_uuid(), 'Đồng Nai', 'DNI', 10.9468, 106.8369, NOW()),
(gen_random_uuid(), 'Bà Rịa - Vũng Tàu', 'VT', 10.5417, 107.2429, NOW()),
(gen_random_uuid(), 'Hồ Chí Minh', 'HCM', 10.7769, 106.7009, NOW()),

-- Đồng bằng sông Cửu Long
(gen_random_uuid(), 'Long An', 'LA', 10.6956, 106.2431, NOW()),
(gen_random_uuid(), 'Tiền Giang', 'TG', 10.4493, 106.3420, NOW()),
(gen_random_uuid(), 'Bến Tre', 'BT', 10.2433, 106.3757, NOW()),
(gen_random_uuid(), 'Trà Vinh', 'TV', 9.8127, 106.2992, NOW()),
(gen_random_uuid(), 'Vĩnh Long', 'VL', 10.2397, 105.9571, NOW()),
(gen_random_uuid(), 'Đồng Tháp', 'DT', 10.4938, 105.6881, NOW()),
(gen_random_uuid(), 'An Giang', 'AG', 10.5216, 105.1258, NOW()),
(gen_random_uuid(), 'Kiên Giang', 'KG', 10.0125, 105.0808, NOW()),
(gen_random_uuid(), 'Cần Thơ', 'CT', 10.0452, 105.7469, NOW()),
(gen_random_uuid(), 'Hậu Giang', 'HGI', 9.7840, 105.6412, NOW()),
(gen_random_uuid(), 'Sóc Trăng', 'ST', 9.6025, 105.9739, NOW()),
(gen_random_uuid(), 'Bạc Liêu', 'BL', 9.2515, 105.7244, NOW()),
(gen_random_uuid(), 'Cà Mau', 'CM', 9.1527, 105.1960, NOW());

-- 3. Refactor cities table: drop old columns, add FK to provinces
ALTER TABLE cities 
    DROP COLUMN IF EXISTS type,
    DROP COLUMN IF EXISTS hotel_count;

ALTER TABLE cities
    ADD COLUMN parent_id UUID NULL REFERENCES provinces(id);

CREATE INDEX IF NOT EXISTS idx_cities_parent_id ON cities(parent_id);

-- 4. Insert city-level records
INSERT INTO cities (id, name, code, latitude, longitude, parent_id, created_at)
VALUES
    -- Miền Bắc
    (gen_random_uuid(), 'Hạ Long', 'HL', 20.9544, 107.0342, (SELECT id FROM provinces WHERE code = 'QN'), NOW()),
    (gen_random_uuid(), 'Bắc Ninh', 'BN_TP', 21.1825, 106.0779, (SELECT id FROM provinces WHERE code = 'BN'), NOW()),
    (gen_random_uuid(), 'Thái Nguyên', 'TN_TP', 21.5918, 105.8447, (SELECT id FROM provinces WHERE code = 'TN'), NOW()),
    (gen_random_uuid(), 'Lạng Sơn', 'LS_TP', 21.8530, 106.7589, (SELECT id FROM provinces WHERE code = 'LS'), NOW()),
    (gen_random_uuid(), 'Hải Dương', 'HD_TP', 20.9303, 106.3212, (SELECT id FROM provinces WHERE code = 'HD'), NOW()),
    (gen_random_uuid(), 'Ninh Bình', 'NB_TP', 20.2718, 105.9744, (SELECT id FROM provinces WHERE code = 'NB'), NOW()),
    (gen_random_uuid(), 'Nam Định', 'ND_TP', 20.4250, 106.1661, (SELECT id FROM provinces WHERE code = 'ND'), NOW()),
    
    -- Miền Trung
    (gen_random_uuid(), 'Đà Lạt', 'DAL', 11.9404, 108.4583, (SELECT id FROM provinces WHERE code = 'LD'), NOW()),
    (gen_random_uuid(), 'Huế', 'HUE_TP', 16.4637, 107.5909, (SELECT id FROM provinces WHERE code = 'HUE'), NOW()),
    (gen_random_uuid(), 'Nha Trang', 'NT', 12.2388, 109.1967, (SELECT id FROM provinces WHERE code = 'KH'), NOW()),
    (gen_random_uuid(), 'Quy Nhơn', 'QN_BD', 13.7667, 109.2289, (SELECT id FROM provinces WHERE code = 'BD'), NOW()),
    (gen_random_uuid(), 'Thanh Hóa', 'TH_TP', 19.8036, 105.7854, (SELECT id FROM provinces WHERE code = 'TH'), NOW()),
    (gen_random_uuid(), 'Vinh', 'VH', 18.6784, 105.6850, (SELECT id FROM provinces WHERE code = 'NA'), NOW()),
    (gen_random_uuid(), 'Hà Tĩnh', 'HT_TP', 18.3568, 105.9060, (SELECT id FROM provinces WHERE code = 'HT'), NOW()),
    
    -- Tây Nguyên
    (gen_random_uuid(), 'Pleiku', 'PLK', 13.9843, 108.0055, (SELECT id FROM provinces WHERE code = 'GL'), NOW()),
    (gen_random_uuid(), 'Buôn Mê Thuột', 'BMT', 12.6667, 108.0500, (SELECT id FROM provinces WHERE code = 'DL'), NOW()),
    (gen_random_uuid(), 'Kon Tum', 'KT_TP', 14.3639, 107.9821, (SELECT id FROM provinces WHERE code = 'KT'), NOW()),
    
    -- Đông Nam Bộ
    (gen_random_uuid(), 'Vũng Tàu', 'VTA', 10.3460, 107.0843, (SELECT id FROM provinces WHERE code = 'VT'), NOW()),
    (gen_random_uuid(), 'Biên Hòa', 'BH', 10.9468, 106.8369, (SELECT id FROM provinces WHERE code = 'DNI'), NOW()),
    (gen_random_uuid(), 'Thủ Dầu Một', 'TDM', 11.1653, 106.6401, (SELECT id FROM provinces WHERE code = 'BDU'), NOW()),
    (gen_random_uuid(), 'Tây Ninh', 'TNI_TP', 11.3167, 106.1118, (SELECT id FROM provinces WHERE code = 'TNI'), NOW()),
    
    -- Đồng bằng sông Cửu Long
    (gen_random_uuid(), 'Hà Tiên', 'HT_KG', 10.3721, 104.4887, (SELECT id FROM provinces WHERE code = 'KG'), NOW()),
    (gen_random_uuid(), 'Rạch Giá', 'RG', 10.0125, 105.0808, (SELECT id FROM provinces WHERE code = 'KG'), NOW()),
    (gen_random_uuid(), 'Long Xuyên', 'LX', 10.3889, 105.4194, (SELECT id FROM provinces WHERE code = 'AG'), NOW()),
    (gen_random_uuid(), 'Châu Đốc', 'CD', 10.6967, 105.1247, (SELECT id FROM provinces WHERE code = 'AG'), NOW()),
    (gen_random_uuid(), 'Mỹ Tho', 'MT', 10.3623, 106.3789, (SELECT id FROM provinces WHERE code = 'TG'), NOW()),
    (gen_random_uuid(), 'Vinh Long', 'VL_TP', 10.2539, 105.9697, (SELECT id FROM provinces WHERE code = 'VL'), NOW()),
    (gen_random_uuid(), 'Cao Lãnh', 'CL', 10.3122, 105.6389, (SELECT id FROM provinces WHERE code = 'DT'), NOW()),
    (gen_random_uuid(), 'Sóc Trăng', 'ST_TP', 9.6023, 105.9758, (SELECT id FROM provinces WHERE code = 'ST'), NOW()),
    (gen_random_uuid(), 'Bạc Liêu', 'BL_TP', 9.2768, 105.7212, (SELECT id FROM provinces WHERE code = 'BL'), NOW());

-- 5. Add hotel_count to cities (after cleanup from old schema)
ALTER TABLE cities
    ADD COLUMN hotel_count INT NOT NULL DEFAULT 0;

-- Update hotel_count for seeded cities (popular destinations)
UPDATE cities SET hotel_count = 1762 WHERE code = 'DAL';
UPDATE cities SET hotel_count = 1325 WHERE code = 'NT';
UPDATE cities SET hotel_count = 1450 WHERE code = 'HUE_TP';
UPDATE cities SET hotel_count = 980 WHERE code = 'VTA';
UPDATE cities SET hotel_count = 850 WHERE code = 'HL';
UPDATE cities SET hotel_count = 650 WHERE code = 'VH';
UPDATE cities SET hotel_count = 600 WHERE code = 'PLK';
UPDATE cities SET hotel_count = 500 WHERE code = 'BMT';
UPDATE cities SET hotel_count = 720 WHERE code = 'QN_BD';
UPDATE cities SET hotel_count = 550 WHERE code = 'TH_TP';
UPDATE cities SET hotel_count = 480 WHERE code = 'HT_TP';
UPDATE cities SET hotel_count = 450 WHERE code = 'BH';
UPDATE cities SET hotel_count = 520 WHERE code = 'TDM';
UPDATE cities SET hotel_count = 420 WHERE code = 'KT_TP';
UPDATE cities SET hotel_count = 380 WHERE code = 'TNI_TP';
UPDATE cities SET hotel_count = 350 WHERE code = 'HT_KG';
UPDATE cities SET hotel_count = 320 WHERE code = 'RG';
UPDATE cities SET hotel_count = 480 WHERE code = 'LX';
UPDATE cities SET hotel_count = 440 WHERE code = 'CD';
UPDATE cities SET hotel_count = 420 WHERE code = 'MT';
UPDATE cities SET hotel_count = 340 WHERE code = 'VL_TP';
UPDATE cities SET hotel_count = 380 WHERE code = 'CL';
UPDATE cities SET hotel_count = 360 WHERE code = 'ST_TP';
UPDATE cities SET hotel_count = 290 WHERE code = 'BL_TP';
UPDATE cities SET hotel_count = 680 WHERE code = 'BN_TP';
UPDATE cities SET hotel_count = 620 WHERE code = 'TN_TP';
UPDATE cities SET hotel_count = 540 WHERE code = 'LS_TP';
UPDATE cities SET hotel_count = 560 WHERE code = 'HD_TP';
UPDATE cities SET hotel_count = 480 WHERE code = 'NB_TP';
UPDATE cities SET hotel_count = 410 WHERE code = 'ND_TP';

-- 6. Update province hotel_count as sum of cities under each province
UPDATE provinces p
SET hotel_count = COALESCE((
    SELECT SUM(c.hotel_count)
    FROM cities c
    WHERE c.parent_id = p.id
), 0);

-- 7. Create indexes for performance
CREATE INDEX IF NOT EXISTS idx_provinces_name ON provinces(name);
CREATE INDEX IF NOT EXISTS idx_provinces_code ON provinces(code);
CREATE INDEX IF NOT EXISTS idx_cities_name ON cities(name);
CREATE INDEX IF NOT EXISTS idx_cities_code ON cities(code);
