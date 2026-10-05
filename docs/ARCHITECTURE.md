# DeviationFlow 架构

知华科技 · 上海如静知华信息科技有限公司 · <https://www.zhuatech.cn/> · 商业咨询微信 zhuatech / zhuatech2。非商业源码学习版，许可见 LICENSE。

Vue 页面以当前账号菜单与权限呈现入口，Nginx 同源代理 API。Spring Security 会话与 CSRF 处理认证，AccessService 在请求及写入锁之后校验当前身份；DeviationService 管理业务状态与事务，AdminService 管理目录并保护最后管理员。Store 提供有界查询、持久化与数据库锁，MySQL 保存事实，Flyway 管理结构。

DRAFT → REVIEW → APPROVAL → ACTIVE。技术或批准退回 → DRAFT，保留历史事件。ACTIVE 按日期／净用量显示尚未生效／有效／额度用尽／到期，并可被指定批准人撤销或关闭；终结无回开入口。所有写操作带实体 version，状态和使用命令另带 UUID 幂等键，重复同意图不会增加事件或扣量。

使用记录保存原事实，批准人独立冲正保留原行。日期判定来自服务器上海自然日，不接受浏览器覆盖。客户端不能写累计用量或业务操作者。多实例需要另行实现共享会话、统一限流和数据库容量规划；当前写入使用粗粒度锁，适合学习与小规模内部协作。
