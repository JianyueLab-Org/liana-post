-- Reuse the service schema mounted by docker-compose.demo.yml.
CREATE DATABASE IF NOT EXISTS `postage_db` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `postage_db`;
SOURCE /demo-postage-schema.sql;
SOURCE /demo-postage-retail-schema.sql;
SOURCE /demo-postage-acceptance-schema.sql;
