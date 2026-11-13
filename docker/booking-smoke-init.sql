-- Executed only by the fresh `checkinn-booking-smoke` PostgreSQL volume.
-- No existing database is read, copied, modified, or referenced.
CREATE DATABASE checkinn_smoke_user;
CREATE DATABASE checkinn_smoke_booking;
CREATE DATABASE checkinn_smoke_hotel;

\connect checkinn_smoke_user
-- User-service has additive Flyway migrations V2+ but no V1 baseline. These
-- two minimal legacy baseline relations exist only in this empty smoke DB;
-- Flyway then runs and JPA creates the remaining test-only schema.
CREATE TABLE users (
  id uuid PRIMARY KEY, username varchar(50) NOT NULL UNIQUE,
  email varchar(254) NOT NULL UNIQUE, password varchar(100) NOT NULL,
  role varchar(20) NOT NULL, is_email_verified boolean NOT NULL
);
CREATE TABLE points_transactions (
  id uuid PRIMARY KEY, user_id uuid NOT NULL, booking_id varchar(128),
  transaction_type varchar(16) NOT NULL
);
