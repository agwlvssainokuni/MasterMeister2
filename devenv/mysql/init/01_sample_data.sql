-- 動作確認用サンプルテーブル・データ（MySQL）

CREATE TABLE departments (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    department_code VARCHAR(10)  NOT NULL UNIQUE,
    department_name VARCHAR(50)  NOT NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE employees (
    id              INT AUTO_INCREMENT PRIMARY KEY,
    employee_code   VARCHAR(20)    NOT NULL UNIQUE,
    employee_name   VARCHAR(50)    NOT NULL,
    department_id   INT,
    email           VARCHAR(100),
    hire_date       DATE,
    salary          DECIMAL(10,2),
    is_active       BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_employees_department
        FOREIGN KEY (department_id) REFERENCES departments (id)
);

INSERT INTO departments (department_code, department_name) VALUES
    ('DEV', '開発部'),
    ('SALES', '営業部'),
    ('HR', '人事部'),
    ('FIN', '経理部');

INSERT INTO employees (employee_code, employee_name, department_id, email, hire_date, salary, is_active) VALUES
    ('E0001', '山田太郎', 1, 'yamada@example.com',   '2020-04-01', 450000.00, TRUE),
    ('E0002', '佐藤花子', 1, 'sato@example.com',     '2021-04-01', 420000.00, TRUE),
    ('E0003', '鈴木一郎', 2, 'suzuki@example.com',   '2019-10-01', 480000.00, TRUE),
    ('E0004', '高橋美咲', 2, 'takahashi@example.com', '2022-04-01', 380000.00, TRUE),
    ('E0005', '田中健太', 3, 'tanaka@example.com',   '2018-04-01', 500000.00, TRUE),
    ('E0006', '伊藤由美', 3, 'ito@example.com',      '2023-04-01', 350000.00, FALSE),
    ('E0007', '渡辺誠',   4, 'watanabe@example.com', '2017-04-01', 520000.00, TRUE),
    ('E0008', '中村愛',   4, 'nakamura@example.com', '2020-10-01', 410000.00, TRUE),
    ('E0009', '小林大輔', NULL, 'kobayashi@example.com', '2024-04-01', 300000.00, TRUE),
    ('E0010', '加藤麻衣', 1, 'kato@example.com',     '2021-10-01', 430000.00, TRUE);