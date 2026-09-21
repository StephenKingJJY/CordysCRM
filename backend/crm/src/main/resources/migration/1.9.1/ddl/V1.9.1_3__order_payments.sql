CREATE TABLE sales_order_payment (
 id VARCHAR(32) NOT NULL,
 order_id VARCHAR(32) NOT NULL,
 organization_id VARCHAR(32) NOT NULL,
 amount DECIMAL(20, 2) NOT NULL,
 received_date DATE NOT NULL,
 remark VARCHAR(2000) NOT NULL DEFAULT '',
 create_user VARCHAR(32) NOT NULL,
 create_time BIGINT NOT NULL,
 voided BOOLEAN NOT NULL DEFAULT FALSE,
 void_user VARCHAR(32) NULL,
 void_time BIGINT NULL,
 void_reason VARCHAR(500) NULL,
 PRIMARY KEY (id),
 INDEX idx_order_payment (organization_id, order_id, create_time),
 CONSTRAINT fk_order_payment_order FOREIGN KEY (order_id) REFERENCES sales_order(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
