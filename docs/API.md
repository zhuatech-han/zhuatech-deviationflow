# DeviationFlow 接口

知华科技 · 上海如静知华信息科技有限公司 · <https://www.zhuatech.cn/> · 商业咨询微信 zhuatech / zhuatech2。非商业源码学习版，许可见 LICENSE。

所有业务路径前缀 `/api`。GET `/auth/csrf` 获取 header／token；POST `/auth/login` 使用 username／password 及 CSRF 头，保留会话 Cookie；随后重新获取 CSRF。写操作需此头。GET `/auth/me` 当前用户及权限；POST `/auth/logout`；POST `/auth/password` 包含 oldPassword／newPassword。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | /options | 当前范围账号、部门、字典及参数 |
| GET | /workbench | 本人相关待办 |
| GET | /dashboard | 范围内统计，按单位汇总 |
| GET | /permits | search、status、page、size、sort 分页搜索 |
| GET | /permits/{id} | record、usages、events |
| POST | /permits | 新建草稿 |
| PUT | /permits/{id} | 作者编辑草稿，version 必填 |
| DELETE | /permits/{id}?version=n | 作者删除从未提交的草稿 |
| POST | /permits/{id}/commands/{action} | submit、review、return-review、approve、return-approval、revoke、close |
| POST | /permits/{id}/usages | 指定执行人登记 |
| POST | /permits/{id}/usages/{usageId}/void | 指定批准人独立冲正 |
| GET | /permits/{id}/report.json | 同范围导出 JSON，no-store |
| GET | /audit | 最近一千条范围内审计 |
| GET/POST/PUT/DELETE | /admin/{type}[/{id}] | ALL 且 admin 权限目录管理 |

管理类型 users、roles、departments、permissions、menus、dictionaries、settings。权限／菜单仅修改注册条目；字典被业务引用后禁止删除；最后一位有效 ALL 管理员受保护。

草稿输入：code、title、category、departmentId、reviewerId、approverId、executorId、itemCode、itemRevision、scopeTag、baseline、deviation、controls、externalAuthorization、quantityLimit、unit、validFrom、validUntil，编辑另传 version。数量上限 1–1,000,000,000，单位 件／批／套；日期为 ISO yyyy-MM-dd，含首尾最多 365 日。

状态命令输入 version、note、requestKey。使用输入 version、requestKey、reference、quantity、evidence、itemCode、itemRevision、scopeTag；冲正使用状态命令结构。requestKey 为小写 UUID，每次新意图新键，网络原请求重试使用原键原内容。键与操作者和完整输入绑定，不能换人或改内容重放。成功使用重试返回原使用行；状态命令重试返回当前授权视图，不重复事件。

服务端生成作者、使用人、累计量及所有日期时间。版本来自详情，不允许跳过。分页 page 从 0 起，size 1–100，sort newest／oldest，search 最多 120 字符。状态筛选 DRAFT／REVIEW／APPROVAL／SCHEDULED／ACTIVE／EXHAUSTED／EXPIRED／REVOKED／CLOSED。

错误返回 JSON code。400 输入无效；401 登录失效；403 权限、数据范围、指定身份或独立性不符；404 不存在；409 版本、重复、状态或额度冲突；429 登录失败限流。内部异常不返回堆栈。账户散列不出现在接口中，导出不放品牌广告。
