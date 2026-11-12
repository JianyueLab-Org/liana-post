# 邮资服务：最小资费与报价版本

实现资费、报价、工作人员邮资零售及逐件收寄核销。liana-web 提供柜台售卖、现金收款确认、数码邮票发行、查询、管理员作废、发行规则配置及传统/数码邮票组合、差额补购、异常恢复。当前流程与数据库升级见 `../docs/postage-acceptance-v1.md`；零售说明见 `../docs/postage-retail-v1.md`；信函解耦见 `../docs/letter-additional-services.md`。在线支付暂未接入。

## 初始标准

版本 `LIANA-TARIFF-2026-001`，**2026-10-15 00:00:00 UTC+08:00** 生效，币种 **LNR**。全部价格、产品、国家分区、运输方式、重量和附加费在 `src/main/resources/initial-tariff.json`；版本号和生效时间在 `application.yml`。首次导入后，运行时读取数据库，通过管理员接口修改并发布新版本，不需要修改 Java 价格常量。

实际重量必须为正整数克，无体积重、保险费、燃油费、税费或优惠。声明价值不参与计价。重量档包含上限；首重续重采用 `首重价 + ceil(max(0,重量-首重)/续重单位) × 续重价 + 按件附加费`。超重或没有配置的服务组合返回 400，不自动换产品。

国内 AIR/SAL/SEA 同价：

| 产品 | 计价 | 最大重量 |
|---|---|---|
| U 信函 | 重量档 20/50/100/250/500/1000/2000g，基本价 1.50/2.80/4.00/8.50/16.70/31.70/55.80，选择挂号另收 3.00/件 | 2000g |
| C 普通包裹 | 首 1000g/8.00，续 1000g/3.00 | 20000g |
| E EMS | 首 500g/18.00，续 500g/5.00 | 20000g |

国际 Z1 为亚洲/大洋洲，Z2 为欧洲/北美洲，Z3 为非洲/南美洲及默认分区。配置预列国家/地区代码，JP/HK/AU 属 Z1，US/DE 属 Z2，BR 属 Z3；RU/TR/CY 在本项目明确归 Z2。未映射的两位国家代码使用 Z3。商业分区与 UPU 区域字段无关。每格为首重价/续重价：

| 产品/方式 | 首重/续重 | Z1 | Z2 | Z3 | 最大重量 |
|---|---|---|---|---|---|
| U AIR | 20g/10g | 5.00/1.00 | 6.00/1.80 | 7.00/2.30 | 2000g |
| U SAL | 20g/10g | 4.50/0.50 | 5.50/0.70 | 6.50/0.80 | 2000g |
| U SEA | 20g/10g | 4.00/0.40 | 4.00/0.50 | 4.50/0.60 | 2000g |
| C AIR | 1000g/1000g | 45.00/18.00 | 60.00/24.00 | 75.00/30.00 | 30000g |
| C SAL | 1000g/1000g | 35.00/14.00 | 48.00/19.00 | 60.00/24.00 | 30000g |
| C SEA | 1000g/1000g | 25.00/10.00 | 35.00/14.00 | 45.00/18.00 | 30000g |
| E AIR | 500g/500g | 85.00/22.00 | 110.00/28.00 | 140.00/36.00 | 20000g |

国际 U 选择挂号另收 16.00/件；旧 R 接口兼容信函加挂号。E 无额外挂号费，国际 E 不支持 SAL/SEA。内部定价随重量、区域距离及运输优先级增加；挂号费覆盖按件登记和签收成本，EMS 收优先级溢价，没有具体送达期限承诺。

报价仅表示已配置价格，不代表开放收寄。DE 已有报价，但现有 OMS 仍按禁邮配置拒绝收寄；尚未存在的国家要先录入 OMS。当前收寄入口会校验 OMS 开放情况。

## 数据库及启动

先在自己的 MySQL 8 环境建立专用数据库及账号：

```sql
CREATE DATABASE postage_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

配置 `POSTAGE_DB_URL`（默认指向本机 postage_db）、`POSTAGE_DB_USERNAME` 和 `POSTAGE_DB_PASSWORD`；不要把密码写入源码。首次启动设置：

```powershell
$env:POSTAGE_SQL_INIT_MODE = 'always'
$env:POSTAGE_SEED_INITIAL_TARIFF = 'true'
java -jar liana-postage-service/target/liana-postage-service-1.0.0-exec.jar --spring.profiles.active=standalone
```

`schema.sql` 创建版本表、报价表与锁表；`retail-schema.sql` 创建零售、发行规则、收款、凭证与审计表；`acceptance-schema.sql` 创建结算、邮票关系及预占锁表。初始导入仅在版本表为空时执行，不覆盖管理员配置。初始化后将 SQL_INIT_MODE 设为 never、SEED_INITIAL_TARIFF 设为 false（均使用 POSTAGE_ 前缀）。已有数据库升级请按顺序执行可重复的 `retail-schema.sql`、`acceptance-schema.sql`，同时升级 OMS 台账，保留原资费与报价。

standalone 关闭 Nacos，但仍需数据库；默认端口 8090，可用 POSTAGE_SERVER_PORT 覆盖。健康及就绪路径为 `/actuator/health`、`/actuator/health/readiness`。资费生效前应用仍可健康运行，但报价返回 409。

正常模式通过 NACOS_SERVER_ADDR 接入 Nacos，Data ID `liana-postage-service.yml`。已有网关发现路由使用 `/liana-postage-service/api/postage/...`。新增 JDBC/MySQL 沿用工程版本，H2 仅供测试，不包含在可执行 JAR。

### Docker 演示部署

项目根目录的 `docker-compose.demo.yml` 已包含邮资服务及 `postage-db-init` 初始化容器：

```powershell
docker compose -f docker-compose.demo.yml up -d --build liana-postage-service
```

初始化容器自动创建 `postage_db` 并执行本服务 `schema.sql`、`retail-schema.sql` 和 `acceptance-schema.sql`，也适用于已有 MySQL 数据卷。OMS 台账升级见收寄文档。应用等待初始化成功后启动，连接容器内 `mysql:3306`，沿用演示环境的数据库账号；无需启用 standalone。表结构由初始化容器管理，`POSTAGE_SQL_INIT_MODE=never`。

首次部署默认启用 `POSTAGE_SEED_INITIAL_TARIFF=true`，仅在版本表为空时导入初始资费。导入成功后可在本地 `.env` 设置 `POSTAGE_SEED_INITIAL_TARIFF=false` 并重建容器。初始资费生效时间仍为 2026-10-15 00:00:00 UTC+08:00。完整部署与维护说明见项目根目录 `CONTAINER_DEPLOYMENT.md`。

## 接口及权限

业务接口要求现有登录签发的 `Authorization: Bearer <JWT>`，服务自行验签，不信任身份头。ADMIN 管理版本；ADMIN 或同时具有 MAIL_CREATE 权限的 CLERK/MANAGER 报价和查询报价。首版沿用现有角色及权限，不添加 Auth 表数据。

| 方法 | 路径（前缀 `/api/postage`） | 请求/用途 |
|---|---|---|
| GET | `/tariffs/current` | 当前生效定义，工作人员可用 |
| GET | `/tariffs` | 管理员列出全部版本 |
| GET | `/tariffs/{id}` | 管理员读取指定版本 |
| POST | `/tariffs` | 创建草稿：`{versionId,definition}` |
| PUT | `/tariffs/{id}` | 替换草稿：`{revision,definition}` |
| POST | `/tariffs/{id}/publish` | 发布：`{revision,effectiveFrom}`；时间 null 为立即生效 |
| POST | `/quotes` | 保存报价 |
| GET | `/quotes/{id}` | 返回历史 snapshot 及当前 valid 标记 |

响应沿用 Result。无有效令牌返回 401，无权限 403，非法字段/规则 400，不存在 404，版本冲突或没有生效资费 409。代码均大写，拒绝小数克重、重复 JSON 键和未知字段。

报价请求：

```json
{"product":"R","scope":"INTERNATIONAL","mode":"AIR","destinationCountry":"JP","weightGrams":31}
```

总额 23.00，基本价 7.00（首重 5.00 + 两次续重 2.00），挂号费 16.00。返回报价 ID、版本、输入、规则及算法、分区、续重次数/档上限、基本费、附加费、总额、有效时间与操作员。

调价流程：读取已发布版本 definition → 用新 versionId 创建草稿 → 修改 definition 并 PUT（携带最新 revision）→ 发布。草稿 revision 从 1 开始，修改/发布均加 1。生效时间带时区，例如 `2026-10-16T00:00:00+08:00`，或 null 立即生效。

## 一致性和扩展限制

- 整个版本以 JSON 文档保存，减少首版关联表数量；使用 BigDecimal。配置支持 TIERS、FIRST_STEP 和按件固定附加费。新增沿用算法的产品及国家可通过新版本完成；新产品进入 OMS 仍需升级其产品校验及运单映射。尺寸等新维度须扩展算法。
- 已发布定义不可编辑、不允许追溯发布，版本按生效时间追加；旧版本有效期截止于下一版本生效时间（右端不包含）。本版暂不支持取消预约或在已预约版本之前插入版本，需安排在其后生效。
- revision 防止草稿覆盖。发布、报价使用 READ_COMMITTED 本地事务，共同数据库行锁支持跨实例一致性；首版串行锁优先保证正确性，吞吐量优化留待实际需要。
- 报价默认 30 分钟，已知的下一生效版本会缩短期限。后来发布的立即生效新版本会使原报价失效，即使 expiresAt 未到；查询 valid 同时校验当前版本。当前结算在预占事务内重新检查报价，不采用客户端金额；预占后保存原计价依据供恢复。
- 已发布版本、操作员、发布时间和完整报价永久保留；尚无完整草稿修改历史及清理功能。所有数据库时间存 UTC epoch 毫秒。
- 发布校验金额精度、正重量、产品引用、唯一规则组合、重量档完整覆盖及不降价、国际服务的所有配置分区覆盖。管理员可改变跨产品/运输方式价差，首版不强制 AIR≥SAL≥SEA 的商业排序。删除某服务全部规则表示未报价，不允许仅删除其部分国际分区。

## 验证

```powershell
mvn -o -pl liana-postage-service,liana-oms-service -am '-Dtest=PostageApplicationTest,TariffCalculatorTest,TariffIntegrationTest,RetailIntegrationTest,AcceptanceIntegrationTest,OmsServiceImplTest,OmsAcceptanceTest' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

85 项相关测试（邮资 73，OMS 12）覆盖价格/重量边界、非法规则、快照持久化、版本切换、并发发布、草稿冲突、HTTP 权限，以及零售金额、重复与并发发行、发行失败回滚、过期、作废、组合核销、旧 R 报价兼容、并发竞争、异常恢复及取消阻挡延迟创建。使用隔离 H2 MySQL 模式、随机 HTTP 端口、固定业务时钟或模拟 OMS 依赖。首次下载依赖时移除 -o。

命令仅执行邮资和 OMS 指定测试，不表示完整工程通过；既有公共模块 JWT 测试曾有已知失败，本轮未修改。当前部署与验证记录见 `../docs/postage-acceptance-v1.md`。
