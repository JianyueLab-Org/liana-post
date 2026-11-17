# 数据库初始化与验证

2026-11-18：正式演示 Compose 已切换到统一初始化任务和独立 v2 持久卷；使用根目录 `script/demo.ps1`，操作见 [部署说明](../CONTAINER_DEPLOYMENT.md)。固定业务验收见 [实际验收记录](scenarios/manifest-acceptance-result.md)。历史验收容器及临时库已清理，隔离验证工具仍可在需要时重新创建专用环境。

本目录是结构的唯一来源，九库分别维护 Flyway 台账。后续改造追加迁移，不改写已执行版本。旧 SQL 入口已删除，历史可查 Git `9052993`；工具拒绝接管无 Flyway 台账的非空旧库。

## 本机执行

依赖 Java 17+、Maven 和 MySQL 8。连接参数必须显式设置；工具不会读取默认生产或演示连接，也没有 clean/reset/repair 操作。

```powershell
$env:LIANA_DB_HOST = '127.0.0.1'
$env:LIANA_DB_PORT = '13317'
$env:LIANA_DB_USER = 'root'
$env:LIANA_DB_PASSWORD = '<独立测试库密码>'
$env:LIANA_DEMO_PASSWORD = '<8 字符以上、72 UTF-8 字节以内的演示密码>'
./script/database.ps1 -Action InitializeDemo
```

`InitializeDemo` 在同一初始化锁内顺序执行 migrate、validate、seed-demo、check；任一步失败即停止。可单独使用 Migrate、Validate、SeedDemo、Check。Migrate 遇到没有 Flyway 台账的非空库会在变更任何库之前拒绝接管。

- `migrations/*/V1__baseline.sql`：当前业务空表结构，普通时间为 UTC `DATETIME(3)`。
- `V2__system_reference.sql`：权限、产品目录、设施类型、邮资锁行与发行规则；不依赖 demo。
- `sorting_db/V2__manifest_prealert_versions.sql`：当前清单版本、内容摘要及不可变完整快照。
- `dispatch_db/V2__manifest_reference_and_outbox.sql`：批次独立路单号、固定版本预告和持久化投递状态。
- `dispatch_db/V3__structured_bag_packing.sql`：固定装包命令、结构化邮件明细及可靠确认状态；未知包重允许为空。
- `oms_db/V3__packing_confirmation_inbox.sql`：完整装包命令的幂等回执；与本库邮件归属同事务提交。
- `dispatch_db/V6__manifest_assignment_receipts.sql`：整单有效分配投影及 ASSIGN/RELEASE 幂等回执。
- `sorting_db/V6__sealed_bag_forwarding.sql`：下一段转运预约、当前占用及可靠投递。
- `dispatch_db/V8__multi_leg_batches.sql`：本段起终点/网络边、历史批次关联和转运 inbox。
- `transport_db/V2__manifest_allocations.sql`：完整分配快照、包明细、当前路单/包唯一占用及本地命令 outbox。
- `seeds/demo/v1/`：LN 海岛机构、账号、邮路、运力、班次和初始资费。班次按首次导入时固定 UTC+12 的下一业务日生成，重复运行不会刷新日期。
- `checks/invariants.sql`：结构化引用和必需数据只读核查。

种子按库独立事务，版本和校验和写入 `demo_seed_history`。一个库失败时该库回滚，之前成功的库保留台账，重试跳过它们。重复执行不覆盖管理员修改；同版文件改变会报校验和冲突，需新增种子版本或另建隔离环境。种子不创建邮件、总包、路单或邮资交易；后续演示业务由场景接口生成。

## 独立 MySQL 验证

```powershell
$env:LIANA_DB_PASSWORD = '<独立测试库密码>'
$env:LIANA_VERIFY_MYSQL_PORT = '13317'
docker compose -f database/compose.verify.yml up -d --wait mysql
```

该 Compose 使用专用项目名、本地端口和 tmpfs 数据目录。MySQL 容器停止或重建会失去测试数据；它不挂载现有演示数据卷。为并行验证可用 `-p <新项目名>` 和另一端口，保留原测试环境。

设置前述连接参数，再运行：

```powershell
$env:LIANA_DB_TEST = 'true'
mvn -Pdatabase -pl database clean test
```

MySQL 集成测试要求九库完全为空，且会创建数据、模拟管理员修改、校验种子冲突与事务回滚；只用于新建专用容器。测试成功后留下已初始化的测试库，不能在同一个非空库直接重跑这项测试。普通单元测试不设置 `LIANA_DB_TEST`。

## 实际服务验证

先在空库完成初始化及 demo 导入，再打包八个服务并启动可选覆盖文件：

```powershell
$env:LIANA_PACKING_TOKEN = '<隔离服务之间共享的随机凭据>'
$env:LIANA_MANIFEST_TOKEN = '<dispatch 与 sorting 之间共享的随机凭据>'
mvn -pl liana-auth-service,liana-facility-service,liana-oms-service,liana-dispatch-service,liana-sorting-service,liana-transport-service,liana-tracking-service,liana-postage-service -am -DskipTests package
docker compose -f database/compose.verify.yml -f database/compose.services.yml --profile services up -d --no-deps redis auth facility oms dispatch tracking transport sorting postage
```

覆盖文件关闭 Nacos、SQL 初始化及初始资费自动导入；JVM 和 JDBC 使用 UTC。服务本地端口为 18081—18087、18090，不经过现有演示网关。若 MySQL 用自定义项目名启动，这里必须传相同 `-p` 和端口环境变量。业务服务目前依赖人工先完成显式初始化；独立初始化成功后才允许服务启动的正式部署依赖链在 P4 切换时接入。

当前 dispatch/OMS/sorting/transport 代码依赖 P1 V2—V6 及 transport V2 列和表，必须先在隔离库执行 Migrate，再启动服务。旧初始化 SQL 不含这些结构。V3 不回填旧 JSON 装包记录；隔离环境应使用空业务库，旧总包缺少结构化清单会被核查和编批拦截，不能当作已确认装包继续执行。

配发服务通过 `oms.service.url`（默认 `http://localhost:8083`）投递固定装包命令；双方配置相同 `packing.internal-token` / `PACKING_INTERNAL_TOKEN`，由请求头 `X-Packing-Token` 校验。可选隔离 Compose 从 `LIANA_PACKING_TOKEN` 注入，不保存实际凭据；未配置时拒绝消费，发送端保留待重试。不要将此凭据放入前端。

建袋先提交结构化清单及待投递状态，后台默认每 5 秒处理。只有 OMS 整单确认才转为 `CONFIRMED` 并允许编批；失败响应及超时保留重试，明确的归属冲突转为 `CONFLICT`，停止自动重试并禁止发运，保留错误与原清单供检查。本批不提供冲突总包改清单、取消或人工强制确认入口。并发建袋可能生成多份待确认记录，但 OMS 邮件行锁只允许一个当前装包获确认；失败记录不代表邮件已装入其中。

通过 `sorting.service.url` 投递路单预告时，批次和本地 outbox 同事务提交。预告 `SENT` 仅表示预告获确认，不表示封单、总包实收或运输到达。接收端支持连续增版，发送端目前仅创建第 1 版，尚无增版编辑接口。

预告获确认后，在批次页申请封单（`POST /api/dispatch/batches/{batchNo}/seal`）。身份从登录取得，普通账号仅允许本机构的交出批次；重复点击复用首次命令。outbox 持久化固定请求号、版本和操作人，经 `POST /api/v1/sorting/internal/manifests/seal` 投递。双方配置相同的 `manifest.internal-token` / `MANIFEST_INTERNAL_TOKEN`，由 `X-Manifest-Token` 校验；隔离 Compose 从 `LIANA_MANIFEST_TOKEN` 注入。此凭据用于服务端路单生命周期及运输分配通道，不能放入前端。

sorting 在路单锁内验证当前版本和未接收状态，将完整请求、版本摘要及 UTC 毫秒回执与 `SEALED` 状态同事务提交。重复请求返回原回执，不倒退后续接收状态；同键异内容拒绝。发送端检查回执身份和版本后落库；超时及凭据配置失败保留重试，明确的 403/409 停在 `CONFLICT`，不提供强制确认。只有封单状态 `SENT` 才允许交接；接收端也验证封单回执。sorting 本地旧重封生成路单时，以 `SORTING_SYSTEM` 服务身份在本库同事务封单，防止旧路径生成不可接收路单；此兼容处理不代表 P2 原子开拆及结构化重封已经完成。

封单回执可在路单详情查看，时间按海岛 UTC+12 展示。当前旧生命周期接入封单及交接前作废节点，尚未实现解封编辑、路单运输占用及改派；不要将任务取消等同于清单解封。迁移可应用到此前新的 Flyway 基线，但不替旧已接收路单补造封单历史；完整验收应使用隔离空业务库。

预告已确认且封单未申请/已确认的批次，可在批次页填写原因申请作废（`POST /api/dispatch/batches/{batchNo}/cancel`，请求体 `{"reason":"本次配发安排撤销"}`）。只支持 `PENDING` / `APPROVED`、所有总包均未交接且未关联运输任务的批次。登录身份决定来源权限和操作人；客户端不提供机构/操作人。请求与批次 `CANCELLING` 同事务保存，立即禁止审批、封单、交接、到达回写和任务关联。重复申请保留首次原因、操作人和请求号；同原因重试返回已有状态，改原因拒绝。

后台使用相同服务凭据投递 `/api/v1/sorting/internal/manifests/cancel`。sorting 锁路单，验证当前版本、来源和未存在接收/开拆/发运/差异事实，保存完整命令及不可变作废回执并置 `CANCELLED`，保留预期明细、版本及原封单。发送端校验回执身份、原因和版本，再将回执及批次 `CANCELLED` 同事务提交。超时保留原请求重试，明确 403/409 转 `CONFLICT`；冲突保持 `CANCELLING` 禁止继续交接，不提供强制成功或自动恢复入口。若取消投递前接收端已开始接收，取消会冲突，不能抹掉事实。

批次页显示作废状态/原因/错误，路单详情显示回执及海岛 UTC+12 时间。已作废路单不再作为接收/开拆依据；总包仅关联已作废路单时，自动匹配拒绝回退成无预告接收。运输查询若发现作废中/已作废批次，禁止新建或推进该总包任务；并发期间已在运输库创建、尚未获 dispatch 关联确认的任务仍可能保留待关联记录，但关联会被拒绝，无法满足发运条件，第五批新协议下可取消该任务并等待可靠 RELEASE 释放本地预留；旧逐包任务仅供查阅，需按新模型重建演示数据。

作废路单仍不释放旧 `dispatch_batch_bag` 关系，原总包不能重新编批。第五批的新运输任务使用整单分配表，旧 `transport_task_bag` 只保留历史、停止新增写入；发前取消任务并确认释放后，可用原路单建立新任务改派。同包不同路单的多段运输仍待 P2 位置/接收占用闭环。封单请求尚未明确确认或冲突未处理时不允许叠加作废；运输任务已经关联时也不允许用作废路单代替取消任务。sorting 内部取消接口仅信任持有共享服务凭据的来源服务，由 dispatch 在本库锁内保证未交接/未关联；这不是供浏览器直接调用的写入接口。

验收包括账号登录、15 个 LN 机构网络、当前资费、空邮件列表、邮资报价/收寄、服务重启后数据不重复；执行结果记录于 `verification.md`。

## 首段整单分配与取消改派（P1 第五批）

先迁移 dispatch V6 和 transport V2，dispatch 与 transport 使用相同的 `MANIFEST_INTERNAL_TOKEN`（隔离 Compose 已通过公共环境项注入）。新接口：

- 创建：`POST /api/transport/tasks`，请求 `{"manifestNo":"<已封路单号>","scheduleId":<可选班次ID>}`；传旧 dispatchBagId 时解析所属整张路单。选择班次可加入该班次已有待发任务；不选班次为 CREATED。
- 加单：`POST /api/transport/tasks/{taskCode}/manifests`，请求 `{"manifestNo":"<另一张已封路单>"}`，必须是同起讫、同邮路。指派班次与发运时校验全部包的克重、资源方式和容量，未知重量不可指派。
- 分配：每 5 秒投递固定 ASSIGN；内部快照和命令接口使用 X-Manifest-Token。来源侧整单写入任务号并保存不可变回执；任务详情中的 manifestAssignments 显示 PENDING/CONFIRMED/CONFLICT 及错误。
- 出发：所有分配 CONFIRMED，全部总包已交接且任务号一致，班次仍可用，才允许 DEPARTED。旧逐包关联接口固定返回 409。
- 取消：状态请求 `{"status":"CANCELLED","reason":"风暴停航，待改派"}`，仅未出发任务允许；返回 CANCELLING 表示仍占用。ASSIGN 超时必须先重试确定结果，RELEASE 确认才释放路单/包及容量，全部完成才变 CANCELLED。
- 改派：确认 CANCELLED 后，以原 manifestNo 创建新任务或加入另一可用同路段任务；原分配 RELEASED、版本、明细、取消原因及 UTC+12 时间在历史中保留。不可把空气/海运资源直接互换成不存在的网络边。

`POST /tasks/retry-links` 保留接口名称，实际触发新 outbox 的到期投递，不再写旧逐包关联。旧任务只读；P2 新任务必须收齐全部包并收到匹配实收回执才可 COMPLETED。每包确认释放该包占用，路单全部包确认后释放当前路单占用；不能用释放代替下一段交出位置核验。冲突保留占用，不提供强制确认或直接删除。来源侧交接事实不因取消任务而清除，路单本身也不被取消任务自动作废。

## P2 首批：包级实收与当前位置

新增 sorting V5、dispatch V7、transport V3。先对隔离库执行全部迁移；三个服务配置相同的 `MANIFEST_INTERNAL_TOKEN`，sorting 另配置 `TRANSPORT_SERVICE_URL` 和 `DISPATCH_SERVICE_URL`。隔离 Compose 已补运输服务地址，现行 demo Compose 仍未切换。

- `POST /api/v1/sorting/receive`：必填已封 `manifestNo`、目的 `stationCode`、`receiveMode=PREALERT`、本次实际扫描的 `packageNos` 和固定 `idempotencyKey`。操作人从登录身份读取，普通用户限本机构。运输任务必须 ARRIVED，整张固定清单的版本、摘要、两端机构和包集合必须一致。
- 同一请求键同内容返回原收据（含固定 eventNo/receivedAt），不再查询运输端；同键异内容拒绝。业务事实、包位置、预期总包状态及双目的地 outbox 同事务；预期邮件仍为 EXPECTED，不因收到总包就当作已开拆实收。
- sorting 每 5 秒领取到期 outbox，按 TRANSPORT/DISPATCH 独立重试；租约 60 秒，过期 attempt 的回执不可覆盖新 attempt。完整 ACK 不匹配及 403/409 停为 CONFLICT，网络/配置故障保留 RETRY。失败不撤销实收，不提供强制删除/确认。
- 运输端每包确认只释放该包，收齐后分配为 RECEIVED、释放路单当前占用；完成时间取所有物理接收时间的最大值，不取投递次序。全部路单收齐才可完成任务。配发端保留 inbox 和物理交接记录，收齐结束当前分配；原 ASSIGN 重放不会恢复占用。
- `GET /api/v1/sorting/manifests/{manifestNo}/bag-receipts`：双方机构或管理员可查看实收记录、当前位置及两端同步状态。路单详情按 UTC+12 显示；接收页面保留模拟连续扫码、一次提交，未确定结果保存原请求并允许原样重试。

本批仅支持已封路单的正常实收。无预告/盈余/强制接收需后续差异工作流，旧到达回写接口固定拒绝。接收位置参与既有模拟开拆事务，开拆完成改为 OPENED；可靠原子开拆/邮件解绑/结构化重封仍待后续批次。首批曾拒绝同包下一段接收；后续 V6/V8 直封转运流程见下节，必须保留交出位置、新路单和历史关系约束。

H2 已验证事务和乱序保护，但尚未完成真实 MySQL 与实际服务联调；部署边界见 [verification.md](verification.md)。

## P2 第二批：同包多段直封转运

先在隔离库迁移 sorting V6、dispatch V8；现行演示环境仍不切换。新结构支持从 V5/V7 已确认实收的单段业务继续运行：迁移结束旧有效批次占用并保存对应接收事件，不删除旧关联。

- 接收勾核页面增加“直封转运”。`GET /api/v1/sorting/forwarding/candidates?facilityCode=...` 只列出本机构未拆、未预约、无未结差异且运输/配发两端均确认的总包。
- `POST /api/v1/sorting/forwarding` 提交固定 requestNo、previousManifestNo、facilityCode、destinationOrgCode、facilityRouteCode 和 bagNos。同一请求原样重放；预约与本地 outbox 同事务，预约期间禁止开拆和另一段预约。
- sorting 每 5 秒可靠投递到 dispatch 内部接口。dispatch 核验上一段最新实收事件和启用网络边，新建独立批次与路单，用原结构化装包清单发出标准预告。之后仍需批次封单、整单分配、交接、出发及到达，预约本身不代表发运。
- dispatch_bag 保留最初封装起终点；dispatch_batch 保存本段起终点/网络边，dispatch_batch_bag 用 nullable active_flag 约束当前唯一批次，历史按 batch_no + bag_no 保留。交接按本段批次判断，上一段交接不能阻止下一段。
- 下一段实收必须匹配已确认预约、旧位置和新路单；实收后更新当前位置，结束预约，保留两段接收与交出关系。旧收据重试不改变新位置或当前任务。
- 交接前作废获确认后释放预约及当前批次关系。重编直封路单仍从 sorting 发起，不允许通过最初封装机构的普通编批入口绕过当前位置。
- 网络或服务暂不可用保留 RETRY；400/403/409、错误 ACK 为 CONFLICT 并保留占用。当前没有人工强制释放入口，处理冲突需核查原请求与对端事实，不能删历史解锁。

海岛演示可按已配置边选择 `LN-OR-01 → LN-OR-RC → LN-NAM-AIR → LN-NAM-HQ`（LAND/AIR/LAND）。North Reef 和 Far Reef 的海运连接仍只能选配置的 SEA 边，不能因停航构造陆运捷径。本批未改变班次、天气或运力模型。

本批 H2 串联与并发回归、前端构建通过；真实 MySQL DDL、InnoDB 与跨服务 HTTP/浏览器联调仍待最终验收。模拟开拆/重封和少包/盈余差异闭环属于剩余开发，未在本批宣称完成。

## P2 第三批开发状态：模拟开拆与结构化重封

新增迁移为 sorting V7/V8、OMS V4、dispatch V9，须通过统一迁移入口应用到隔离库。现行 demo 初始化路径及数据库尚未切换。

开拆继续采用本地逐件模拟扫码、完成时一次提交。固定请求、原响应、扫描明细、差异、OPENED 状态与 OMS outbox 在 sorting 本库事务保存；OMS inbox 与邮件解绑在 OMS 本库事务保存。只解绑实际扫描且属于预期的邮件；空扫描形成全部缺件，盈余单独隔离。开拆确认前禁止相关邮件进入路由/重封；回执重放不撤销新包归属。

重封请求保存新包号、逐件来源总包和开拆事件，预留与 outbox 同事务。dispatch 接收后核验实际岛内网络边、原结构化装包及来源总包接收状态，再创建新包和标准 OMS 装包确认任务。`SENT` 仅表示 dispatch 已建包，仍须在邮袋管理中等待 OMS `CONFIRMED`，再按标准编批、封单、分配和交接。旧 sorting 自动造路单/系统封单已退出写路径，旧国家格口直接封包入口固定拒绝。

管理员差异审核入口仅支持 `MISSING → CONFIRMED_NOT_RECEIVED`、`SURPLUS → QUARANTINED` 并保存理由与原请求号。审核关闭待办，保留原预期/扫描事实；缺件不变成实收，盈余继续隔离。少包与路单关闭见下一节。

## P2 收尾：结束总包勾核与路单关闭

应用 sorting V9、dispatch V10、transport V4。迁移只新增台账和状态，不回填旧演示业务数据，不改写已发布迁移。

1. 接收人员在路单详情点击“确认结束总包勾核”，对应 `POST /api/v1/sorting/manifests/{manifestNo}/finish-check`，提交固定 `requestNo` 和目的 `facilityCode`。后端验证已到达运输分配并冻结实收/缺失分区；允许零包实收。未收预期包记为 MISSING 和待审核差异，原预期邮件不动。此后拒绝新接收，原请求仍可离线重放。
2. 保留现有逐件模拟开拆；少件/盈余及少包均由管理员按原事实审核。关闭前每个实收包须完成 OMS 开拆确认，或完成下一段接收/缺袋勾核；仅创建待发转运预约不足以关闭，以免其后取消恢复未处理总包。实收双端及所有已发起开拆均须确认。
3. 管理员调用 `POST /api/v1/sorting/manifests/{manifestNo}/close`，请求格式同上。固定关闭命令与双端 outbox 同事务，状态 CLOSING。`GET .../closure` 查询勾核分区、原命令与双方状态；页面保留首次请求，响应丢失复用原内容。
4. dispatch/transport 内部 `/manifest-closures` 使用 `X-Manifest-Token`；两端分别验证封单版本、原分配和真实实收分区，事务提交结算 inbox。缺失包从匹配的当前占用释放，dispatch 标记 MISSING；不伪造任何接收或 OMS 邮件解绑。运输缺袋分配为 SETTLED，实际收齐仍为 RECEIVED；SETTLED 的 received_at 不虚构收齐时间，结算时间保存在 inbox。任务所有分配 RECEIVED/SETTLED 后可正常完成。
5. 同包下一段缺失时，sorting 原位置标记 MISSING 并保留交出引用，不恢复为发送地可用封包。已实收且转入后续任务的包不被旧路单关闭改写。迟到新实收被拒绝；原实收/关闭命令重放返回固定回执。
6. 只有两个匹配 ACK 均持久化才变为 CLOSED；重启可恢复最后 ACK 与状态投影之间的中断。网络失败保留退避重试，明确 400/403/409 或错误 ACK 留在 CONFLICT 供核查，不提供强制确认/删历史入口。

既定三项开发收尾已实现并进入验收。相关服务 159 项回归和前端 109 模块构建通过；独立 MySQL 8 九库迁移/重跑/种子保护回归通过，87 表 752 列字段清单核对通过。跨服务 HTTP、真实并发与浏览器场景结果见 [verification.md](verification.md)。验收只修复既定范围的问题，不增加 GPS、多站车次、真实扫码设备或旧数据兼容；现行 demo 不自动切换。
