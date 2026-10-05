-- Copyright 2026 上海如静知华信息科技有限公司 · https://www.zhuatech.cn/ · 微信 zhuatech / zhuatech2
CREATE TABLE deviation_permit (
id bigint AUTO_INCREMENT PRIMARY KEY,
version bigint NOT NULL DEFAULT 0,
code varchar(60) NOT NULL UNIQUE,
title varchar(160) NOT NULL,
category varchar(60) NOT NULL,
department_id bigint NOT NULL,
author_id bigint NOT NULL,
reviewer_id bigint NOT NULL,
approver_id bigint NOT NULL,
executor_id bigint NOT NULL,
item_code varchar(100) NOT NULL,
item_revision varchar(60) NOT NULL,
scope_tag varchar(120) NOT NULL,
baseline varchar(2000) NOT NULL,
deviation varchar(2000) NOT NULL,
controls varchar(2000) NOT NULL,
external_authorization varchar(1000) NOT NULL,
quantity_limit bigint NOT NULL,
used_quantity bigint NOT NULL,
unit varchar(20) NOT NULL,
valid_from date NOT NULL,
valid_until date NOT NULL,
status varchar(20) NOT NULL,
review_note varchar(2000) NOT NULL,
approval_note varchar(2000) NOT NULL,
created_at timestamp(6) NOT NULL,
approved_at timestamp(6),
edit_revision bigint NOT NULL,
FOREIGN KEY(department_id) REFERENCES department(id),
FOREIGN KEY(author_id) REFERENCES account(id),
FOREIGN KEY(reviewer_id) REFERENCES account(id),
FOREIGN KEY(approver_id) REFERENCES account(id),
FOREIGN KEY(executor_id) REFERENCES account(id)
);
CREATE TABLE permit_usage (
id bigint AUTO_INCREMENT PRIMARY KEY,
permit_id bigint NOT NULL,
actor_id bigint NOT NULL,
reference varchar(100) NOT NULL,
quantity bigint NOT NULL,
evidence varchar(2000) NOT NULL,
created_at timestamp(6) NOT NULL,
voided boolean NOT NULL,
void_actor_id bigint,
void_reason varchar(2000) NOT NULL,
voided_at timestamp(6),
UNIQUE(permit_id,reference),
FOREIGN KEY(permit_id) REFERENCES deviation_permit(id),
FOREIGN KEY(actor_id) REFERENCES account(id),
FOREIGN KEY(void_actor_id) REFERENCES account(id)
);
CREATE TABLE command_record (id bigint AUTO_INCREMENT PRIMARY KEY,request_key varchar(36) NOT NULL UNIQUE,fingerprint varchar(64) NOT NULL,result_id bigint NOT NULL);
CREATE TABLE flow_event (id bigint AUTO_INCREMENT PRIMARY KEY,kind varchar(20) NOT NULL,object_id bigint NOT NULL,department_id bigint NOT NULL,actor_id bigint NOT NULL,action varchar(40) NOT NULL,note varchar(2000) NOT NULL,created_at timestamp(6) NOT NULL,FOREIGN KEY(department_id) REFERENCES department(id),FOREIGN KEY(actor_id) REFERENCES account(id));
CREATE INDEX ix_permit_scope ON deviation_permit(department_id,status,valid_until);
CREATE INDEX ix_permit_executor ON deviation_permit(executor_id,status);
CREATE INDEX ix_usage_permit ON permit_usage(permit_id,created_at);
CREATE INDEX ix_event_object ON flow_event(kind,object_id,id);
