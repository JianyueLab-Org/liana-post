# 数据库结构来源与字段清单

盘点日期：2026-11-17。87 张业务表、752 个字段（另有九库各自的 Flyway 及 demo 台账，共 105 张表）。完整字段、类型、可空性、默认值和原来源见 [inventory.json](inventory.json)。sorting V9、dispatch V10、transport V4 增加明确结束勾核及可靠关闭。独立 MySQL 8 空库、重复迁移和种子保护回归已通过；752 个字段的类型、可空性、默认值及 extra 与 information_schema 逐项比对无差异。实际服务和浏览器验收单独记录，不以结构通过代替。

| 库/所有者 | 表 | 主要结构来源 | 合并与保留差异 |
|---|---|---|---|
| `auth_db` | `permission`, `role`, `role_permission`, `user`, `user_role` | `liana-auth-service/src/main/resources/schema.sql` | 必需角色/权限与 demo 用户分开；关系按业务编码解析 ID。 |
| `dispatch_db` | `dispatch_bag`, `dispatch_batch`, `dispatch_batch_bag`, `dispatch_transport_task_link`, `handoff_record`, `route_rule`, `dispatch_manifest_outbox`, `dispatch_bag_packing`, `dispatch_bag_item`, `dispatch_manifest_assignment`, `dispatch_assignment_inbox`, `dispatch_receipt_inbox`, `dispatch_forward_inbox`, `dispatch_repack_inbox`, `dispatch_repack_source` | `db/dispatch_db.sql` + P1 V2—V6 / P2 V9 | 独立路单号及本地预告/封单/作废 outbox，保存匹配回执；作废确认与批次状态同事务提交，待确认不允许交接/关联任务；结构化邮件明细及 OMS 装包重量不可变，重封新包保留原开拆事件、邮件和来源总包。 |
| `facility_db` | `demo_facility_profile`, `demo_postal_island`, `demo_postal_region`, `demo_route_profile`, `facility`, `facility_route`, `facility_type` | `db/facility_db.sql`<br>`db_revise/017_liana_pacific_demo_network.sql` | 保留 transport_type、地图四表及 map_x/map_y；设施类型不引入资源 data.sql 独有的 status。 |
| `oms_db` | `country`, `country_service_type`, `mail`, `mail_type`, `oms_postage_acceptance`, `recipient`, `sender`, `service_type`, `oms_packing_inbox`, `oms_unpacking_inbox` | `database/migrations/oms_db/V4__unpacking_confirmation_inbox.sql`<br>`liana-oms-service/src/main/resources/schema.sql` + P1 V3 / P2 V4 | 保留邮资核销契约；装包和开拆命令回执与邮件归属变更同事务提交，重复投递返回固定回执，不得解绑已被新包占用的邮件。 |
| `postage_db` | `postage_acceptance`, `postage_acceptance_lock`, `postage_acceptance_stamp`, `postage_digital_stamp`, `postage_quote`, `postage_receipt`, `postage_retail_audit`, `postage_sale`, `postage_stamp_policy`, `postage_tariff_lock`, `postage_tariff_version` | `liana-postage-service/src/main/resources/acceptance-schema.sql`<br>`liana-postage-service/src/test/resources/retail-schema.sql`<br>`liana-postage-service/src/main/resources/schema.sql` | 合并 schema/retail-schema/acceptance-schema；保留字符串主键、UTC 毫秒、JSON 文本、本库外键；锁行和发行规则进入 V2。 |
| `sorting_db` | `discrepancy_verification`, `package_item_line`, `sorting_manifest`, `sorting_manifest_item`, `sorting_manifest_package`, `sorting_total_package`, `sorting_manifest_version`, `sorting_manifest_seal`, `sorting_manifest_cancel`, `sorting_receive_request`, `sorting_bag_receipt`, `sorting_bag_position`, `sorting_receipt_outbox`, `sorting_forward_request`, `sorting_bag_handoff`, `sorting_unpack_request`, `sorting_unpack_item`, `sorting_repack_request`, `sorting_repack_item` | `db_revise/007_sorting_core_migration.sql`<br>`db_revise/015_manifest_package_relationship.sql` + P1 V2—V4 / P2 V7—V8 | 路单版本、封单、实际接收、开拆扫描及重封来源分层保存；完成开拆与原响应、OMS outbox、差异事实同事务；只有扫描且属于预期的邮件可解绑；盈余隔离、缺件不伪造实收；重封固定新包号并保留逐件历史。 |
| `syncer_db` | `outbox_message`, `retry_record`, `sync_task` | `db/syncer_db.sql` | 保留同步三表；业务本地 outbox 在 P1 随领域事务增加。 |
| `tracking_db` | `tracking_event` | `db/tracking_db.sql` | 保留邮件号 13 字符；总包/路单事件身份问题留 P1/P2 处理，不放宽邮件号绕过。 |
| `transport_db` | `transport_asset`, `transport_route`, `transport_schedule`, `transport_task`, `transport_task_bag`, `transport_manifest_assignment`, `transport_assignment_bag`, `transport_manifest_current`, `transport_bag_current`, `transport_assignment_outbox`, `transport_receipt_inbox` | 原 schema + transport V2 | 新任务按整单版本快照和结构化包明细分配，路单/包各有当前唯一占用，历史不删；取消收到 RELEASE 回执才释放。旧 task_bag 只保留历史，不新增写入；原 capacity kg 在校验边界乘 1000 转克。 |

## 结构对照与已发现的问题

- 独立 MySQL 8 容器回放旧脚本时，正常执行在 002 因重复 transport_task_code 列停止；继续诊断还发现 003 邮件号过长、004 重复列/索引、005 重复 mail_scope。诊断的继续执行仅用于读取结构，不是新的部署方式。
- 对诊断后的旧库与新基线逐列比较：无遗漏列，无除 DATETIME 毫秒精度以外的类型、可空性、默认值或 extra 差异；不比较旧 AUTO_INCREMENT 历史计数。
- 基线保留当前索引与本库外键，补齐 019 的网络边索引与任务重试字段。新路单的历史占用与关系外键留在后续模型迁移，不提前移除现有约束。
- `inventory.json` 记录 V1 及当前已实现的 P1/P2 业务列，新增及修改列的来源指向对应迁移；不把 Flyway 内部表纳入业务字段契约。后续领域迁移必须同步更新该清单。
- sorting V6：新增 `sorting_forward_request`、`sorting_bag_handoff` 及当前位置；dispatch V8：新增 `dispatch_forward_inbox` 和本段批次/当前占用。sorting V7：新增固定开拆请求、逐件 EXPECTED/SURPLUS 扫描记录及 OMS 投递状态；sorting V8 / dispatch V9 / OMS V4：新增结构化重封请求、逐件原总包/开拆事件追溯、dispatch 新总包装包来源及 OMS 开拆 inbox。完整字段类型已登记 inventory.json；新增事实不写入 demo 种子。

## 时间与部署过渡

本次关闭协议新增：sorting 所有的 `sorting_manifest_check`（冻结分区及运输快照）、`sorting_manifest_close`（固定管理命令）、`sorting_close_outbox`（双端独立投递）；dispatch 所有的 `dispatch_manifest_close_inbox`；transport 所有的 `transport_manifest_close_inbox`。缺袋扩展 sorting 的容器/交出状态 MISSING 和 transport 分配 SETTLED，不新增实收事实。对应权威迁移为 sorting V9、dispatch V10、transport V4。

- 新隔离基线的普通时间为 DATETIME(3)，工具连接强制 UTC；邮资及 OMS 核销台账仍为 UTC epoch milliseconds。
- compose.verify.yml 使用 MySQL UTC；compose.services.yml 使用 JVM UTC、JDBC UTC 及驱动强制连接会话 UTC，不额外绑定 Hikari 初始化 SQL。默认 docker-compose.demo.yml 仍使用原时区和初始化挂载，P4 前不替换正在运行的演示环境。当前验收限制见 [verification.md](verification.md)。
- 未改造的接口仍可能序列化不带偏移的 LocalDateTime；业务 +12 展示与带偏移接口契约在 P1/P2 随业务操作落实，不宣称仅换 JDBC 就完成时区整治。

## 必需数据与 demo

- V2：角色/权限、设施类型、产品/国家/运输服务目录、邮资锁行与发行规则。
- demo/v1：15 个 LN 设施、4 岛群、12 岛、22 个网络方向边、路由规则、12 条执行路线、独立运力和 12 个下一业务日代表班次；8 个虚构角色账号；初始资费快照。
- 初始资费快照冻结自现有 initial-tariff.json，保留 2026-10-14T16:00:00Z 的生效时间。运营后的资费发布及规则修订由业务接口维护。
- 不生成旧 A1/B1/C1 机构、测试邮件、总包、路单、轨迹或邮资交易。
