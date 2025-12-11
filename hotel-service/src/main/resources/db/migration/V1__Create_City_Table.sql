-- Migration: Create cities table and add city_id to hotels table

-- 1. Create cities table
CREATE TABLE cities (
    id UUID PRIMARY KEY,
    name VARCHAR(255) NOT NULL UNIQUE,
    code VARCHAR(10) UNIQUE,
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT uk_cities_name UNIQUE (name),
    CONSTRAINT uk_cities_code UNIQUE (code)
);

-- 2. Add city_id column to hotels table
ALTER TABLE hotels ADD COLUMN city_id UUID NOT NULL DEFAULT '00000000-0000-0000-0000-000000000000';

-- 3. Add foreign key constraint
ALTER TABLE hotels 
ADD CONSTRAINT fk_hotels_city_id 
FOREIGN KEY (city_id) REFERENCES cities(id);

-- 4. Create index on city_id for better query performance
CREATE INDEX idx_hotels_city_id ON hotels(city_id);

-- 5. Create index on city name for search
CREATE INDEX idx_cities_name ON cities(name);

-- 6. Insert all 63 Vietnamese provinces and cities
INSERT INTO cities (id, name, code, latitude, longitude, created_at) VALUES
-- Miền Bắc
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
