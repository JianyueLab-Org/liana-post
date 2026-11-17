# 数据库与路单改造验证记录

更新：2026-11-18。固定业务验收已在专用 MySQL 8、八个实际服务与浏览器完成，详见 [实际验收记录](scenarios/manifest-acceptance-result.md)。主链路六单 CLOSED/三任务 COMPLETED，另一个并发场景 CLOSED，停航船班保留未出发占用；原消息重放、零/部分实收、开拆重封、双端中断恢复通过，最终九库 check 通过。验收修复后相关回归累计 165 项，前端 109 模块构建通过。87 表 752 列结构未改，旧 demo 未切换。剩余为 P4 部署入口与归档/切换交付；UUID 编号等既有差异详见实际记录，不宣称全部早期设想均已实现。以下历次结果保留为历史。

## 最新收尾与隔离验收结果

本轮实现 sorting V9 / dispatch V10 / transport V4：明确结束勾核、冻结实收/缺袋分区、管理员差异审核、双端 outbox/inbox 关闭及缺袋占用释放。缺袋保持 MISSING/SETTLED，不生成物理实收或 OMS 邮件解绑；已收到且进入后续任务的包不被旧路单结算改写。收到的包完成开拆确认或下一段勾核后才能关闭，避免未出发预约后来取消留下已关闭但未处理的总包。

实际执行：

- `mvn -q -pl liana-dispatch-service,liana-sorting-service,liana-oms-service,liana-transport-service -am test`：159 项，零失败/错误；common 13、OMS 27、dispatch 42、sorting 58、transport 19。
- 专项覆盖重封消费和固定回执、来源/网络门禁、事务失败整体回滚、同扫描并发唯一新包；零包及部分实收结束勾核、接收并发、正常开拆后关闭、未完成工作/同步门禁、管理员审核、双端独立重试、中断恢复、原事实重放和迟到新实收拒绝；结算不修改后续任务，SETTLED 后正常完成运输任务。安全差异不能经 MANIFEST 处置入口审核。
- `npm.cmd run build`：109 模块通过，包含勾核完成、缺袋/缺件区分、关闭确认、运输 SETTLED 展示及固定请求恢复。
- 独立项目 `liana-manifest-accept-20261117`，MySQL 8.0，localhost:13327，tmpfs。`LIANA_DB_TEST=true`、`mvn -q -Pdatabase -pl database test`：3 项通过（2 项配置单测、1 项实际 MySQL 集成），包括非版本非空库拒绝、九库空库首次/重复迁移、初始数据、demo 重复导入保护管理员修改、种子校验和冲突/事务回滚。
- MySQL 实际得到 105 张表（87 业务表 + 九 demo 台账 + 九 Flyway 台账）。752 列与 inventory.json 逐项比较类型、可空性、默认值和 extra，无差异、无重复字段键。清单同时修正新增条目的 SQL 引号/NULL 默认值表示；没有原地修改已发布迁移。
- UTF-8 无 BOM / LF 审计及 `git -c core.safecrlf=false diff --check` 通过。

实际服务验收尝试：服务已打包，独立 Compose 创建/启动命令成功返回（八服务与独立 Redis），随后 Docker Desktop Linux Engine v1.48 的容器列表及 sorting 日志查询返回 HTTP 500；localhost:18081 五秒请求无响应。故未确认应用就绪，未将其记为启动成功、HTTP 场景成功或浏览器验收成功。未重启 Docker Desktop，未清理、迁移或切换原 demo 数据库/容器。

下一步只运行 [固定验收清单](scenarios/manifest-acceptance.md)，验证真实 InnoDB 并发、HTTP 和浏览器场景，并在通过后完成正式部署入口切换。正式 demo 仍使用旧初始化路径；本轮新增迁移不代表已发布新版。若恢复独立容器继续联调，应重新打包最新源码；MySQL 集成测试为保护性测试，会留下模拟管理员修改的隔离数据，不能在该非空库直接重跑“空库测试”。不再新增功能，验收只修复既定改版问题。

### 验收续查：Docker 内部引擎不可达

2026-11-17 续查：`desktop-linux` 明确连接 `dockerDesktopLinuxEngine`，API v1.48 和 v1.41 均返回 HTTP 500，沙箱外复查结果相同。Docker Desktop 的 `monitor.log.0` 与后端日志记录 `connect tcp 192.168.65.7:2375: no route to host`，说明宿主代理无法连接内部引擎；降级客户端 API 未恢复连接。localhost:13327 虽处于监听状态，MySQL 握手读取仍超时；18081 的 HTTP 请求五秒超时，不能以端口监听判断服务已就绪。

本次八个服务已按最新源码重新打包成功（`mvn -q -pl liana-auth-service,liana-facility-service,liana-oms-service,liana-dispatch-service,liana-sorting-service,liana-transport-service,liana-tracking-service,liana-postage-service -am -DskipTests package`）；未重复执行此前通过的测试，也未将打包成功记为运行验收成功。

本次没有新增业务功能，也未重启 Docker、重建测试容器或操作旧演示库。下一步需要恢复 Docker Desktop；由于重启会中断同一引擎上的旧演示容器，应先取得用户对该环境操作的确认。隔离 MySQL 使用 tmpfs，恢复后如测试数据丢失，应新建专用验收项目并重新迁移/导入种子，不向旧演示库运行恢复脚本。

## 已有结果

- P0 交接记录中的后端及邮资测试共 73 项通过，前端 `npm.cmd run build` 通过；这些历史结果不替代本轮验证。
- 交接记录中的隔离 MySQL 验证覆盖空库初始化、重复迁移、demo 导入、管理员配置保留、校验和冲突与种子事务回滚；本次未完整复验成功。
- 本次重跑原隔离端口 13317 时，集成测试在初始空库检查发现 70 张表并停止。该失败仅说明环境非空，不是成功的初始化验证，也不是工具拒绝接管非版本库的保护测试。
- 最终 `mvn -q -Pdatabase -pl database -Dtest=DatabaseManagerTest test` 通过，2 项单元测试覆盖显式连接参数、UTC/超时参数及未知/破坏性命令拒绝。它不替代 MySQL 集成测试。

## 本次修正

实际服务容器日志显示八个服务启动失败，原因是额外设置 `spring.datasource.hikari.connection-init-sql` 时连接池已经启动并封闭。移除隔离 Compose 中这项重复属性，保留 JVM UTC 和 JDBC `serverTimezone=UTC&forceConnectionTimeZoneToSession=true`。修正后的服务启动尚待复验。

数据库工具增加 10 秒连接超时和 60 秒 socket 读取超时，避免网络故障无限等待。隔离 MySQL 增加 `--skip-name-resolve`，避免反向名称解析成为连接握手依赖。

## 环境阻碍与剩余检查

新建独立 `liana-database-p0-final` 项目、端口 13318，未挂载演示数据卷。MySQL 通过本地 socket 能读取系统库；Windows 到端口的 JDBC 握手停滞，Docker 网络内名称解析报 `UnknownHostException`，容器管理 API 间歇返回 HTTP 500。使用同一 MySQL 网络空间的 localhost 验证最终也报通信读取超时，未取得成功结果。未重启 Docker Desktop，以免中断现有演示服务。

待环境稳定后：

1. 在全新专用 MySQL 上重跑 `mvn -Pdatabase -pl database clean test`，确认最新迁移及种子完整通过。
2. 显式初始化及导入 demo 后，启动 `compose.services.yml`，确认八个服务可启动。
3. HTTP 验证登录、海岛网络、当前资费、初始邮件数为零，再完成报价和一次邮资收寄。
4. 重启服务，确认无额外业务行、账号及运营配置不被覆盖。
5. 完成本批 P1 的 MySQL 验证后，继续总包逐路单占用及运输分配历史，与服务读写同步交付；不能只放宽数据库唯一约束。

现有 `docker-compose.demo.yml` 和 `liana-demo-mysql` 未被切换、清库或迁移。

## P1 第一批验证

- `mvn -q -pl liana-sorting-service,liana-dispatch-service -am test` 成功退出，31 项通过、0 失败、0 错误：common 13，dispatch 7，sorting 11。
- 新增覆盖：完整预告替换及旧快照保留、同版重试不重置实收、同版内容冲突、跳版/处理后改版拒绝、失败事务回滚、并发首次投递；本地 outbox 与批次事务边界、固定 payload、租约恢复及过期发送者隔离、HTTP 与业务确认失败后重试；未确认禁止交接、未知重量保持空值、明确路单预览不跨单。
- `npm.cmd run build` 成功，108 个模块；本批界面展示路单号、预告状态及历史版本。
- 字段清单纳入两项 V2 迁移；业务表 54 张，含每库 Flyway 与种子台账时，空库集成测试预期 72 张表。核查增加当前版本摘要/预期数量与快照一致性。
- 数据库工具 `DatabaseManagerTest` 再次通过（2 项）；`inventory.json` 的 493 列无重复，新建 V2 表字段与清单对应；本批相关修改及新增文件 UTF-8 无 BOM、LF 检查通过，`git diff --check` 通过。

事务与版本测试使用 H2 的精简测试结构，HTTP 投递测试使用本地模拟接收端；这些测试不证明 MySQL DDL、InnoDB 锁行为或真实跨服务联调成功。新增临时 MySQL `liana-manifest-p1-mysql` 使用 tmpfs、不挂载演示卷且不暴露端口；容器创建成功，但查询时 Docker 管理 API 返回 HTTP 500，未能执行或验证本批迁移。没有重启 Docker Desktop。

下一次真实 MySQL 验证应在全新隔离库执行全部迁移、重复迁移、种子和核查，并验证并发同版投递、增版与接收竞争、重复 outbox 领取及崩溃恢复。当前不应将新 dispatch/sorting 包直接部署到只执行旧 schema 的演示库。

## P1 第二批验证：结构化装包与 OMS 确认

- `mvn -q -pl liana-dispatch-service,liana-oms-service,liana-sorting-service -am test` 成功退出，60 项通过、0 失败、0 错误、0 跳过：common 13，OMS 22，dispatch 14，sorting 11。本轮新增 15 项，原 OMS 14 项也包含在本次回归中。
- 新增测试覆盖：本地装包与投递记录整体回滚；固定清单及未知重量；租约恢复和过期结果隔离；HTTP 失败/假确认重试、真实确认后状态推进、明确冲突后停止重试；OMS 同命令并发幂等、不同总包争抢同一邮件只有一个成功、部分绑定失败整单回滚、机构/重量/请求身份冲突；开拆后的迟到重试不重新绑定；服务凭据缺失拒绝写入；旧入口不能跨未拆包转移或追加已确认包；未确认禁止编批、旧同步不改清单、按选中邮件真实重量准备命令。
- `mvn -q -Pdatabase -pl database -Dtest=DatabaseManagerTest test` 成功，2 项通过。集成测试的总表数断言已更新至 75，但本次没有执行 MySQL 集成测试。
- `npm.cmd run build` 成功，108 个模块；总包页可刷新确认/冲突状态，编批候选仅包含已确认总包，创建结果保留明确的待确认提示。
- V3 新增 `dispatch_bag_packing`、`dispatch_bag_item`、`oms_packing_inbox`；字段清单为 57 张业务表、514 列，已逐一核对 V3 新建表字段，未知总重改为可空。核查 SQL 增加装包明细、数量/重量投影、序号及快照数量检查。
- 79 个相关修改/新增文件的 UTF-8 无 BOM、LF 检查通过，`git diff --check` 通过；移除了两个已修改文件原有的 BOM。

本轮再次执行 `docker info`，Docker Desktop API 仍返回 HTTP 500，因此没有执行 V3 MySQL 迁移、真实 InnoDB 并发测试或实际服务联调。H2 及本地 HTTP 模拟测试的成功不能替代这些结果。未修改或重启现有演示 MySQL；新确认通道须先在隔离环境配置相同的 `PACKING_INTERNAL_TOKEN`，说明见 README。

当前剩余风险：并发建袋可保留多份待确认请求，只有一个能绑定邮件，失败总包会停在冲突状态；取消/重开处理入口尚未实现。sorting 的开拆与重封仍走既有流程，未纳入本批结构化封装和可靠事件闭环；总包当前位置、逐路单占用和运输改派历史仍待后续开发。

## P1 第三批验证：路单封单与交接门禁

- `mvn -q -pl liana-dispatch-service,liana-oms-service,liana-sorting-service -am test` 成功，73 项通过：common 13、OMS 22、dispatch 19、sorting 19，0 失败/错误/跳过。最后补充一项“未封路单接收前拒绝写事实”测试后，运行 `mvn -q -pl liana-sorting-service -am '-Dtest=ManifestPrealertServiceTest,ManifestSealControllerTest,SortingManifestPreviewTest' '-Dsurefire.failIfNoSpecifiedTests=false' test`，三个测试类的 17 项通过；合并现有报告共 74 项，0 失败/错误/跳过。
- 覆盖固定版本及不可变回执、并发相同命令、增版与封单竞争、机构和版本冲突、同请求异内容、封单状态写失败整单回滚、接收后的迟到重试、已存在处理事实拒绝封单、缺失凭据拒绝消费、本地重封自动生成系统封单，以及来源机构权限、登录操作人、未确认禁止交接/接收、固定命令重试、假回执与错版回执拒绝、明确业务冲突停止重试、过期发送者不能覆盖新回执。
- 首轮回归暴露原 JWT 测试固定替换尾字符的偶发失效，改为替换签名首字符的有效位，避免可能未改变字符或只改变 base64url 填充位；没有更改 JWT 生产逻辑。一次定向命令被 PowerShell 拆分含点的 `-D` 参数，补引号后成功执行。
- `mvn -q -Pdatabase -pl database -Dtest=DatabaseManagerTest test` 通过（2 项）；`npm.cmd run build` 通过，108 模块。字段清单为 58 张业务表、525 列，sorting V3 新表 9 列及 dispatch V4 新增 2 列均匹配，无重复字段键；MySQL 集成测试的含台账总表数断言更新至 76。
- 相关改动/新增文件 77 个的 UTF-8 无 BOM、LF 检查通过，`git diff --check` 通过。检查只覆盖改动范围，未批量修改无关文件已有编码或行尾。

`docker info` 曾返回服务器版本 28.0.1，但紧随其后的 `docker ps` 再次收到 Docker Desktop API HTTP 500，容器控制未恢复；没有运行本批 MySQL 迁移、InnoDB 并发验证或真实跨服务联调，没有重启 Docker Desktop或修改现有演示库。H2 和本地模拟 HTTP 的通过不能视为发布验收通过。

新隔离环境需先迁移 sorting V3 / dispatch V4，并给 dispatch 与 sorting 配置相同的 `MANIFEST_INTERNAL_TOKEN`（隔离 Compose 的 `LIANA_MANIFEST_TOKEN`）。缺失凭据会保持封单重试并禁止交接；详情见 README。旧已接收路单不会补造封单回执。sorting 本地重封的系统封单仅避免新门禁造成回归，原子开拆、结构化重封和可靠 OMS 更新仍属于 P2。下一批继续路单取消及运输有效分配/历史，不能删除旧总包唯一约束后直接宣称多段运输可用。

## P1 第四批验证：交接前作废及可靠确认

- `mvn -q -pl liana-dispatch-service,liana-oms-service,liana-sorting-service,liana-transport-service -am test` 成功，97 项通过、0 失败/错误/跳过：common 13、OMS 22、dispatch 30、sorting 30、transport 2。
- 接收端验证：未封/已封路单均可在处理前作废；固定版本、来源、操作人及原因；同命令幂等、同键异内容/换请求号拒绝；并发同命令一份回执；接收与作废同路单锁互斥；状态写失败回执整体回滚；已有接收、开拆、发运或差异事实拒绝；旧预告和封单迟到重试不恢复作废路单，原版本、明细和封单均保留；凭据缺失/错误拒绝消费。
- 发送端验证：预告未确认/封单待确认不能叠加作废；本地业务失败请求与状态回滚；重复点击固定首次身份/原因，修改原因拒绝；租约隔离迟到发送者；错误业务响应、布尔假确认、错原因回执不能确认；HTTP 请求成功但本地状态失败时保留原命令重试；回执和最终 `CANCELLED` 同事务提交；明确冲突保持 `CANCELLING`、停止自动投递且继续阻止业务操作。
- 新增 `DispatchCancellationConcurrencyTest` 使用 MyBatis-Plus 实际仓储 SQL、Spring 本地事务和 H2，调用真实建批/封单/作废/交接/关联服务，验证作废与交接、作废与任务关联分别只能一方提交；保留两包明细；确认后不能封单或隐式重新编批，失败建批不留新批次。模拟接收端确认仅用于建立测试前置，不表示实际服务联调完成。
- 旧路径验证：已作废路单不能进入接收/开拆；省略路单号时只关联已作废路单的总包不能回退成无预告接收；不同路单不能冒用另一总包的开拆；作废中批次的交接、到达回写和任务关联均拒绝；transport 读到 `CANCELLING/CANCELLED` 后不创建新任务。改用批次→总包的统一锁顺序，审批/封单也参与批次锁，避免并发旧状态覆盖。
- `mvn -q -Pdatabase -pl database -Dtest=DatabaseManagerTest test` 通过（2 项）；`npm.cmd run build` 通过，108 模块。新增 sorting V4 作废回执表 10 列，dispatch V5 扩展 outbox 动作检查约束；59 张业务表、535 列，字段清单无重复，新表逐列匹配；MySQL 集成测试含台账总表数断言更新至 77。核查 SQL 增加作废版本/摘要/状态一致性、请求与批次双向对应及无交接事实。
- 93 个相关改动/新增文件的 UTF-8 无 BOM、LF 检查通过，`git diff --check` 通过；无关既有文件未做编码转换。

本轮 `docker ps` 仍返回 Docker Desktop API HTTP 500，未执行 sorting V4 / dispatch V5 的真实 MySQL 迁移或 InnoDB 并发验证；没有重启 Docker Desktop、清库或切换原演示环境。新增测试使用 H2 和本地模拟 HTTP，不替代真实 MySQL 及跨服务验收。

本批限制：作废仅支持未交接、未关联运输任务的批次，旧批次与总包唯一关联保留，原包不释放重编；transport 库中可能存在并发已创建但未获 dispatch 关联确认的任务，关联会被拒绝、不能发运，恢复处理需下一批有效分配协议。若接收端在取消消息抵达前已接收，作废冲突且保持源侧冻结，不自动清事实或恢复交接。冲突处理、任务取消释放、总包当前位置和逐路单占用尚未完成，不能宣称一任务多路单、同包多段或取消改派已打通。配置沿用双方共享的 `MANIFEST_INTERNAL_TOKEN`，不新增浏览器可见凭据；界面提供原因输入、状态及 UTC+12 历史回执。

## P1 第五批：首段整单运输分配、取消释放与改派（2026-11-17）

本批增加 dispatch V6 的 2 表、transport V2 的 5 表；字段清单为 66 张业务表、582 列，九库含 Flyway/demo 台账应为 84 表，隔离 MySQL 测试断言已同步。新任务停止写旧 transport_task_bag，保留旧历史及唯一约束。dispatch 旧逐包关联入口关闭，交接要求整单分配确认。

实际执行通过：

- `mvn -q -pl liana-dispatch-service,liana-oms-service,liana-sorting-service,liana-transport-service -am test`：111 项，common 13、OMS 22、dispatch 34、sorting 30、transport 12，零失败/错误。
- `mvn -q -Pdatabase -pl database -Dtest=DatabaseManagerTest test`：2 项通过。
- `npm.cmd run build`：108 模块构建通过。
- `git -c core.safecrlf=false diff --check` 通过；本轮源文件继续使用 UTF-8 无 BOM、LF。

新增测试用真实 H2 事务和 MyBatis 仓储验证整单两包原子预留、同班次一任务多路单、数据库唯一约束下并发只成功一次、取消与出发互斥、所有清单聚合容量和未知重量。H2 测试读取 transport 的规范迁移，只去掉 MySQL 存储/排序规则并将 JSON 适配为文本；这不能证明 MySQL/InnoDB 行为。

可靠投递验证：分配响应丢失后复用首次命令重试、ASSIGN 未确定前不投 RELEASE、取消待确认仍占包和运力、匹配释放回执后改派、旧租约/旧释放回执不能清新占用、错误回执冻结。来源侧验证整单关联与作废互斥、同请求异内容拒绝、提前 RELEASE 墓碑拒绝迟到 ASSIGN、历史重放不覆盖新任务、任务取消不清原交接事实，内部读写接口必须持服务凭据。

限制：本轮 `docker ps` 仍返回 Docker Desktop Linux Engine API HTTP 500，未完成新增 DDL 的真实 MySQL 回放、跨服务 HTTP 联调或浏览器运行验收，未迁移/清理/切换现有演示库。前端仅通过生产构建验证。sorting 总包当前位置、上一段实收后释放、同包多段及原子模拟开拆/重封仍待 P2；已完成运输任务的当前分配暂不自动释放。tracking 仍是尽力投影。上文第四批“下一批”限制由本节对新分配协议补充，不能把本批结果视作整项路单改造验收完成。

## P2 第一批：包级实收与当前位置（2026-11-17）

- 最新相关服务回归成功退出：123 项通过、0 失败、0 错误：common 13，OMS 22，dispatch 35，sorting 37，transport 16。transport 新增并发收齐测试验证同任务最后两个总包同时实收时，两条包级收据均保留，路单及运输任务占用只释放一次。
- `git -c core.safecrlf=false diff --check` 通过。此前编码审计覆盖本批相关文件，UTF-8 无 BOM、LF；追加并发测试后尚未重新执行完整编码审计。
- 仍未完成真实 MySQL 迁移回放、InnoDB 并发验证或服务间 HTTP 联调：Docker Desktop API 查询持续挂起/HTTP 500，未重启 Docker、未迁移或清理现行演示库。H2 并发测试不能证明 MySQL 锁行为、DDL 或真实跨服务部署。

sorting 实收与开拆都遵循路单行后总包位置行的锁顺序；旧手工 `/manifests/arrived` 写入路径保留为固定拒绝。接收仍是一批扫描后一次提交，开拆模拟和实收并非同一事实。下一批继续总包从已开拆状态的可靠交出、新封装/新路单关系及逐段运输历史；当前同包不同路单接收仍明确拒绝，盲收/盈余处理和原子重封尚未完成。

## P2 第二批：同包多段直封转运（2026-11-17）

本轮完成 sorting V6 / dispatch V8、可靠转运预约/消费回执、本段批次起终点和网络边、当前唯一批次占用、历史关系保留，以及接收页面的直封转运入口。原总包封装内容及起终点保留，下一段仍使用标准封单、分配、交接和到达协议。

实际通过：

- `mvn -q -pl liana-dispatch-service,liana-sorting-service,liana-transport-service -am test`：129 项，0 失败/错误（common 13、OMS 22、dispatch 36、sorting 42、transport 16）。
- 新增 6 项测试覆盖两段串联接收、两段独立封单/交接、原收据重放不覆盖新任务/位置、仅一次有效预约、开拆互斥、确认失败保留占用、取消恢复，以及过期 worker 不覆盖新 attempt。
- `npm.cmd run build`：109 模块构建通过。前端构建起初误在仓库根目录执行，修正到 liana-web 后成功。
- 字段清单为 75 张表、667 列，无重复字段键；新增迁移及关键源文件通过 UTF-8 无 BOM/LF 检查；`git -c core.safecrlf=false diff --check` 通过。

本批未执行真实 MySQL 新迁移、服务间 HTTP 或浏览器运行验收；沿用此前 Docker Desktop 异常的验收阻碍记录，未在本批确认 Docker 已恢复。未迁移/清理/切换现行演示库。H2 结果不能替代 MySQL DDL、外键/排序规则及 InnoDB 并发验证。tracking 仍为尽力投影。

剩余范围固定为模拟开拆/结构化重封及差异闭环、最终隔离环境验收。多站车次、GPS、真实扫描设备、旧演示数据兼容等继续延期；新增功能不再作为本轮完成条件。

## P2 第三批开发中：模拟开拆与结构化重封（2026-11-17）

sorting V7/V8、OMS V4、dispatch V9 已加入工作区，字段清单新增 7 表、55 列，共 82 张业务表、722 列，无重复字段键。模拟开拆保存固定响应、扫描事实/差异与 OMS outbox；OMS 原包归属核验、整批解绑和 inbox 同事务，缺件及盈余不进入解绑命令。sorting 重封预留、逐件原包/开拆事件关系与本地 outbox 同事务，dispatch 创建标准装包等待 OMS 确认。旧 sorting 自造路单并自动封单已退出写路径。

本次 `mvn -q -pl liana-dispatch-service,liana-sorting-service,liana-oms-service,liana-transport-service -am test` 成功退出：144 项，零失败/错误（common 13、OMS 27、dispatch 36、sorting 52、transport 16）。开拆新增测试覆盖部分/空扫描、盈余隔离、请求重放、内容冲突、事务回滚、并发、离线重试、过期 worker 与错误 ACK；另有 sorting 重封预留/重放和管理员差异审核测试。dispatch 新重封消费器尚无专项测试，不能把既有 dispatch 回归通过称为重封全链路验收。最新前端生产构建通过（109 模块），包含差异审核与请求恢复界面，尚待浏览器运行验证。

未进行新增 DDL 的真实 MySQL 回放、跨服务 HTTP 或浏览器运行验收；本轮未核实此前 Docker Desktop 故障是否恢复，未迁移/清理/切换现行演示库。少包结束、差异处置后的路单关闭及运输占用释放尚待实现；管理员处置只关闭审核待办，不补造接收。现有不带偏移的旧时间接口、原路由 HTTP 事务边界等风险在最终验收核对，不因当前单元测试而宣称解决。
