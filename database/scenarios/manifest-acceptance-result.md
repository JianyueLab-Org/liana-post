# 路单固定场景实际验收记录

记录日期：2026-11-18。以下为实际 HTTP、浏览器和 MySQL 8 结果；不表示正式 demo 已切换。

## 环境

- 专用项目 `liana-manifest-accept-r2`，MySQL 容器 `liana-manifest-accept-r2-mysql-1`，127.0.0.1:13328，tmpfs；Redis 127.0.0.1:13379。
- 八个服务在 Windows 主机运行，端口 18081—18087、18090，JVM/JDBC UTC，SQL 初始化和远程配置关闭。Docker 可用内存不足以并行运行两套完整业务容器，因此使用 `start-local-services.ps1`。
- 浏览器通过独立 Vite 15173 访问专用服务，不经过旧 demo 网关。旧 MySQL 3307、旧卷和旧业务数据未迁移、重置或切换。
- 九库最新迁移验证成功：auth/facility/postage V2、OMS V4、dispatch V10、sorting V9、transport V4、tracking/syncer V1。未改写已执行迁移。
- 操作使用 demo 登录账号和虚构邮件。固定请求、回执及场景状态保存在本机临时目录 `liana-manifest-accept-r2/scenario.json`，不含密码或令牌；MySQL tmpfs 不作为发布数据源。

## 已完成的固定场景

| 场景 | 实际结果与证据 |
|---|---|
| 邮资和收寄 | 创建 9 件虚构邮件，每次原收寄请求重试返回同一邮件号；报价、传统邮资确认和登录正常 |
| 一任务多路单 | 首段两张路单、三个总包、120g，同班次聚合；确认后交接、出发、到达 |
| 取消改派 | 原任务 `TT-F266DB22-5862-41CC-BA23-E7C3D0784C1C` 为 CANCELLED，两项历史 RELEASED；新任务独立分配 |
| 同键并发接收 | 四个相同接收请求返回同一固定回执/事件，不重复实收 |
| 部分实收及少包 | 首段第一张路单仅收一包，明确勾核后另一包 MISSING；新接收 409，原成功请求仍可重放 |
| 缺件/盈余 | 第一包仅扫描一件预期邮件和一件真实存在的虚构盈余；缺件保留原包归属、盈余隔离，管理员分别确认未收到/隔离 |
| 开拆重封 | OMS 确认后才重封；三次并发同键重封仅生成新包 `B202611171617008020232`，20g，来源关系保留，dispatch ACK 与 OMS CONFIRMED 分开 |
| 三段直封 | 总包 `B202611171603426713022` 沿 LN-OR-01 → LN-OR-RC → LN-NAM-AIR → LN-NAM-HQ，LAND/AIR/LAND，条码和原封装清单不变 |
| 总部页面逐件开拆 | 页面先收总包，再逐件扫描 `UU300447281LN`、`UU447729711LN`；提交完成后 OMS 实际解绑并更新到 LN-NAM-HQ |
| 页面刷新与服务恢复 | 暂停专用 sorting 后提交开拆失败；刷新仍保留两件和原请求。恢复服务后重试 `UNPACK-BCAFDECC-751A-4FD9-B4FA-52D1BEC93C78` 成功，确认状态为可继续路由 |
| 下一段零包实收 | 总部对新包路单零实收结束勾核；位置保留 LN-NAM-AIR、状态 MISSING、保留交出引用；无总部包级收据。源站再转运/开拆均 409，迟到接收 409 |
| 管理员关闭与占用释放 | 主链路六张路单全部 CLOSED；三个任务由正式状态接口 COMPLETED。含缺袋的分配 SETTLED，receivedAt 保持空，不伪造实收 |
| 双端独立故障恢复 | 暂停专用 dispatch 后关闭五张单；TRANSPORT 一次 SENT，DISPATCH RETRY，路单 CLOSING。恢复后 DISPATCH 第六次成功，同一固定命令完成 CLOSED |
| 原消息迟到重放 | 从数据库读取原始 outbox，不构造替代事实；共重放 20 次 ASSIGN/RELEASE/两端包级收据。sorting 当前位置、dispatch 包记录及 transport 当前占用逐项未变化 |
| 接收与结束勾核并发 | 新建独立单 `M33B83A8D88DB416ABE86B487C3D3519A`，两个线程同屏障并发 HTTP；勾核先提交、接收 409，零实收/一缺袋，无部分接收；审核后 CLOSED，任务 COMPLETED |
| 海运停航/网络/权限 | LN-OR-RC → LN-OR-02 的 SEA 单 `M53E0B982457E4D18B3494ADEB6CAAB6C`：虚构 LAND 替代路线 400、错误班次 409、异机构分配 403、SUSPENDED 班次不能分配；已交接后停航仍禁止 DEPARTED，任务保留 ASSIGNED，无到达/实收 |
| UTC+12 跨日 | 修正 UTC 日期与海岛星期不一致；UTC 11:59/12:01 两侧正反例四项通过。班次输入/显示明确 +12，旧无偏移路单时间按 UTC 解释 |
| 数据核查 | 业务场景后再次执行九库 `check`，全部 invariant 为零。七张 CLOSED、一张停航 SEALED；唯一剩余路单/总包当前占用属于停航船班，保留占用符合预期 |

主链路任务：

- LAND：`TT-DBACD2C6-D4CD-4367-A5F1-F96E1FDEE106`。
- AIR：`TT-6DB3B61C-9C89-48FF-945F-60BD414BAC88`。
- LAND：`TT-2CA78AAF-9B22-4BC2-8F6E-66C6D5E4FE1C`。

主链路路单：`MBA187CA82CB24A8BB37ED4FE58FBE3AE`、`M8A061BF1F19747D2B9303717AAD6AFB7`、`ME5760DD47D084258A75E2F08D0B4675A`、`MD00C3E618DD242898DD7D3956EED6F06`、`MFB8558AE36EE4CBAAAFA1743B81EE1D1`、`M6527F8EBE5A54D0093B65C058DA2FE81`。

## 验收发现并修复

1. dispatch 的 OMS Feign 客户端未接受显式 URL，关闭发现后无法查询候选；增加与其他客户端一致的 URL 配置。
2. sorting 缺少业务异常 HTTP 映射，409/403 被包装为 500；增加明确错误码处理和两项 HTTP 测试。
3. MySQL 返回 LocalDateTime，H2 返回 Timestamp，开拆查询/重复审核原强制类型转换失败；兼容两种 JDBC 返回，保持 UTC。
4. 核查 SQL 注释中的分号被工具拆成语句；去除注释分号。缺袋保留交出引用符合业务，核查允许 MISSING/MISSING 组合。
5. 班次星期按 UTC 日期校验，与 +12 种子契约不一致；改按海岛日校验、当前时刻显式 UTC，补四个边界用例。班次页完成 +12 输入/显示适配；路单页旧 DATETIME 字符串按 UTC 解释。

新增修复后相关测试累计 165 项：common 13、OMS 27、dispatch 42、sorting 60、transport 23，零失败/错误。dispatch、sorting 与 transport 分别在修改后重跑测试并打包；最终前端构建 109 模块通过。此前独立空库 MySQL 初始化专项 3 项结果仍保留，没有在非空场景库重跑空库测试。

## 发布边界和剩余交付

固定业务场景完成，接下来只做 P4 发布交付：统一初始化任务接入正式 Compose 依赖链、旧 SQL 退出运行入口，以及新卷部署/烟测/回退核对。不能把当前主机运行方式或 tmpfs 验收库当成已发布的 demo。

路单业务号目前仍为独立 UUID。早期计划中的“交出机构 +12 业务日流水号”尚未实现；本次跨日测试证明班次日期语义，不证明该流水号规则完成。此差异保持公开，不以格式增强扩大当前功能收尾。旧接口仍存在无偏移 LocalDateTime，当前受控 UTC 部署和页面适配不能等同于所有服务接口已统一 Instant。tracking 仍是尽力投影。上述既有差异应随发布说明交付。

本记录的并发结果只证明已执行交错及数据库约束，并非穷尽所有网络/并发排列。现有测试覆盖另一顺序；不反复添加相似场景作为无限延长验收的条件。
