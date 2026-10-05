# 部署说明

知华科技 · 上海如静知华信息科技有限公司 · <https://www.zhuatech.cn/> · 商业咨询微信 zhuatech / zhuatech2。非商业源码学习版，未经书面授权不得商用。

需要 Docker Compose v2、Python 3.11+。运行 `python3 scripts/init-env.py` 创建三个独立随机密码，配置权限 0600；用 `docker compose -p deviationflow-local config --quiet` 检查配置，再 `docker compose -p deviationflow-local up --build -d --wait`。默认只有前端端口 8116 绑定本机，数据库和后端不对外映射。

健康顺序为 MySQL → 后端 `/actuator/health` → 前端。后端镜像执行 spotless:check 和 clean package（不跳过测试）；前端镜像执行 format:check、lint、test、build。两个应用容器为非 root，数据库账号为独立应用用户。

HTTPS 部署需要自行配置可信反向代理和证书，COOKIE_SECURE=true，限制监听范围与来源。当前单实例会话、单企业数据范围，不提供分布式会话、租户隔离或高可用保证。MySQL 不使用公开 root 连接。真实密码、客户记录和备份不上传仓库。

普通停止使用 `docker compose -p deviationflow-local down`，保留命名卷；不要用 down -v 删除业务库。验收使用独立 deviationflow-check 项目，结束后仅删除该项目容器、网络及卷。备份应保存 MySQL 逻辑快照、迁移版本和配置密文；恢复到隔离环境核对账号、授权、使用和审计后再切换。基础镜像补丁升级应重建并完整复验。
