# Liana Post 演示部署与维护

2026-11-18 起，正式演示使用九库 Flyway 初始化和独立的 `mysql_v2` / `redis_v2` 持久卷。旧 demo 数据已按授权舍弃。唯一结构来源是 `database/migrations/`，初始海岛主数据在 `database/seeds/demo/`；业务服务启动不建表、不生成样例邮件。

## 首次部署

需要 Docker Desktop（Linux 容器）、Java 17+、Maven、PowerShell，以及可用的 `D:/sentinel/sentinel-dashboard-1.8.9.jar`。其他位置通过 `SENTINEL_HOME` 设置；该目录只读挂载。

```powershell
Copy-Item .env.demo.example .env.demo
# 编辑 .env.demo，填写数据库密码、演示账号密码和两个独立随机服务凭据。
./script/demo.ps1 -Action Up
./script/demo.ps1 -Action Check
python script/demo-smoke.py
```

`.env.demo`、日志和 `.dist/` 均不提交。`Up` 先打包全部服务，再构建镜像，等待 MySQL 健康，执行 `initialize-demo`，最后启动服务。全套服务冷启动可能需要数分钟，`up -d` 返回并不代表所有 HTTP 接口已就绪。

访问 `http://localhost/`；默认 web 80、网关 8080、Nacos 8848、Sentinel 8858，MySQL 仅绑定本机 3307。演示账号包括 `admin001`、`oro-clerk`、`oro-sorter`，密码来自本机 `LIANA_DEMO_PASSWORD`。种子不创建业务邮件。

冒烟脚本通过 web 网关创建两封虚构邮件，验证收寄、装包、封单、运输、接收、模拟开拆、勾核、关闭和任务完成；状态保存在 `.dist/demo-smoke.json`。同一状态重复运行仅核对完成结果。更换空数据卷时须同时移走这个本地状态文件。

## 日常开发与演示

```powershell
./script/demo.ps1 -Action Stop
./script/demo.ps1 -Action Start
./script/demo.ps1 -Action Build
./script/demo.ps1 -Action Up
./script/demo.ps1 -Action Check
```

不要直接执行旧的无 env 文件启动命令。数据库连接、服务间地址、UTC 时区和内部凭据统一由 Compose 注入。dispatch 内部端口 8084、facility 8082；Nginx 动态解析网关地址，容器更换 IP 后可恢复代理。

## 备份与回退

更新前停止业务写入，保存九个业务库的一致性备份、当前镜像版本及对应代码提交。备份不入 Git，不作为初始化脚本。不要用旧 jar 启动已升级到新结构的数据卷；Flyway 没有自动降级操作。

回退时停止应用，使用单独的新卷恢复对应备份，运行匹配版本的 validate/check，再启动服务。保留原卷直到确认恢复成功。验证结果见 [数据库验证记录](database/verification.md)。

## 清理边界

本次已清理旧验收容器、可确认归属的匿名卷、测试网络，以及旧 `liana-post_mysql_data` / `liana-post_redis_data`。保留正在使用的 v2 卷、现行镜像、Nacos、Sentinel、Redis 和 MySQL，以及其他项目资源。不要运行全局 `docker system prune --volumes`。

旧 `db/`、`db_revise/`、服务主资源目录的初始化 SQL 和 `script/demo-mysql-init.sh` 已退出工作树；可在 Git 提交 `9052993` 中查阅历史版本。邮资 H2 测试 SQL 仅位于 `src/test/resources/`。以后所有数据库变更遵循 [数据库规范](docs/database-design-standard.md)，通过追加迁移完成。
