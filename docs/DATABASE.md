# 数据库与迁移

知华科技 · 上海如静知华信息科技有限公司 · <https://www.zhuatech.cn/> · 商业咨询微信 zhuatech / zhuatech2。非商业源码学习版，许可见 LICENSE。

MySQL 8.4，版本化 Flyway 迁移，Hibernate ddl-auto=validate。V1__identity.sql：account、access_role、role_permissions、department、permission、nav_menu、dictionary_entry、system_setting、audit_event。V2__deviations.sql：deviation_permit、permit_usage、flow_event、command_record。

授权关联部门、作者、复核、批准、执行账号；使用关联授权与原操作者及可空冲正者。授权代码唯一；使用凭据 (permit_id, reference) 唯一；命令 UUID 唯一。外键禁止删除已引用目录。版本字段保护旧页面，数量采用 BIGINT 整数；批准与使用时间服务器生成，使用量扣减与事件同一事务。

授权状态持久保存 DRAFT、REVIEW、APPROVAL、ACTIVE、REVOKED、CLOSED。SCHEDULED／EXHAUSTED／EXPIRED 根据 ACTIVE 的日期和净用量派生，页面、查询和统计使用相同规则。冲正不删除原使用行，只增加冲正元数据并减少净使用量。终结状态优先于派生日期和额度。

写事务先锁固定总部 Department 1，再刷新账号／角色，统一 READ_COMMITTED，防止并发额度超用与权限变更竞争。此实现用于学习与小规模协作，高并发系统需评估更细粒度锁及迁移兼容性。固定总部不能删除。

部署升级新增迁移，不重写已生效文件。MySQL 数据卷必须备份，升级前恢复到独立环境演练。禁止凭空改历史批准人、直接增大额度或删除事件。测试使用 H2 MySQL 模式仅辅助自动化；实际迁移和持久化另在全新 MySQL 卷验收。
