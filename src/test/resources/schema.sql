-- =============================================
-- 集成测试用 H2 表结构
-- 与 sql/init.sql 保持同步，以 init.sql 为准；改动生产 DDL 时须同步此文件
-- 差异说明：省略 MySQL 的 ENGINE/COMMENT/ON UPDATE 子句（H2 不支持），
--           update_time 不再自动刷新，测试不依赖该行为
-- =============================================

DROP TABLE IF EXISTS evaluation;
DROP TABLE IF EXISTS order_log;
DROP TABLE IF EXISTS repair_order;
DROP TABLE IF EXISTS device_category;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS sys_role;

CREATE TABLE sys_role (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    role_name   VARCHAR(50)  NOT NULL,
    role_code   VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(200)          DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE sys_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username    VARCHAR(50)  NOT NULL UNIQUE,
    password    VARCHAR(100) NOT NULL,
    real_name   VARCHAR(50)           DEFAULT NULL,
    phone       VARCHAR(20)           DEFAULT NULL,
    role_id     BIGINT       NOT NULL,
    status      TINYINT      NOT NULL DEFAULT 1,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_user_role_id ON sys_user (role_id);

CREATE TABLE device_category (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(50) NOT NULL,
    sort        INT         NOT NULL DEFAULT 0,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE repair_order (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_no    VARCHAR(32)  NOT NULL UNIQUE,
    student_id  BIGINT       NOT NULL,
    category_id BIGINT                DEFAULT NULL,
    device_name VARCHAR(100) NOT NULL,
    location    VARCHAR(100) NOT NULL,
    description VARCHAR(500)          DEFAULT NULL,
    image_url   VARCHAR(500)          DEFAULT NULL,
    status      TINYINT      NOT NULL DEFAULT 0,
    repairer_id BIGINT                DEFAULT NULL,
    finish_time DATETIME              DEFAULT NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_order_student_id ON repair_order (student_id);
CREATE INDEX idx_order_repairer_id ON repair_order (repairer_id);
CREATE INDEX idx_order_status ON repair_order (status);

CREATE TABLE order_log (
    id          BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT      NOT NULL,
    operator_id BIGINT      NOT NULL,
    action      VARCHAR(50) NOT NULL,
    remark      VARCHAR(200)         DEFAULT NULL,
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_log_order_id ON order_log (order_id);

CREATE TABLE evaluation (
    id          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    order_id    BIGINT       NOT NULL UNIQUE,
    score       TINYINT      NOT NULL,
    content     VARCHAR(500)          DEFAULT NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);
