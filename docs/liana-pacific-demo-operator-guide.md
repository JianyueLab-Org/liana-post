# Liana Pacific Demo 操作指南

## 演示范围

本指南面向 Liana Post 演示环境，介绍利亚纳群岛邮政网络地图和对应的设施、邮路、运输资源与代表班次。该场景是虚构规划数据，不是实时生产数据，也不连接真实航空、航运、支付或海关系统。

需要理解国家背景、四个邮政区域、十二座岛屿和典型邮件旅程时，参阅 [Liana Pacific 世界观设定与开发参考](liana-pacific-world-setting.md)。该文档用于统一后续产品文案、测试场景和业务建模中的设定，不替代接口或数据库定义。

网络基线包括 Namoa、Oro、Miri、Sela 四个邮政区域，12 座虚拟常住岛，15 个规划设施、9 条基础路线和 9 个代表班次。新的演示记录使用 `LN-` 前缀；原数据库中的 `A1`、`B1` 等旧演示记录保留。

## 启动与更新

在项目根目录运行：

```powershell
docker compose -f docker-compose.demo.yml up -d
```

MySQL 持久卷初始化过的环境不会自动重新执行初始化 SQL。要将群岛网络导入当前 demo 数据库，运行：

```powershell
docker exec -e MYSQL_PWD=liana_demo liana-demo-mysql sh -c "mysql --user=root --default-character-set=utf8mb4 < /demo-db-revise/017_liana_pacific_demo_network.sql"
```

该 SQL 可重复运行：按设施、路线、运输资源及班次编码插入或更新；不删除旧行、不重建表。新建空 MySQL 卷时，`script/demo-mysql-init.sh` 会按 `db_revise` 文件顺序自动执行该脚本。不要为了导入它而删除或重置 `mysql_data`。

脚本还会创建 `facility_db.demo_postal_region`、`demo_postal_island`、`demo_facility_profile`、`demo_route_profile` 四张 `demo_` 场景扩展表，分别保存岛群/邮编前缀、12 座岛屿及地图标注位置、设施邮编覆盖与服务信息/点位、邮路频次与运营说明。`facility` 和 `transport` 的现有业务表仍存放正式设施、路线、资源与班次记录。

前端容器需重新构建才能提供最新版地图：

```powershell
docker compose -f docker-compose.demo.yml build liana-web
docker compose -f docker-compose.demo.yml up -d --no-deps liana-web
```

然后打开 [http://localhost](http://localhost)，使用现有管理员账号登录。演示账号及口令按本地项目的演示环境配置提供；不要在演示材料中记录个人或生产环境凭据。

## 管理员地图操作

登录后可在「工作台」顶部直接查看群岛邮政覆盖图，也可从左侧「邮政网络 → 群岛邮政地图」打开独立页面。地图页面只有管理员角色可见。

1. 选择「全部邮路」「航空」「海运」或「陆运」，查看对应运输网络；通过区域选项聚焦 Namoa、Oro、Miri 或 Sela。
2. 点击地图上的圆形设施标记，查看设施编码、类型、邮编范围、服务覆盖、处理能力、运营时间和相连邮路。
3. 点击地图上的路线，查看始发/到达设施、运输方式、计划时长、代表班次、距离和天气/潮汐等运营说明。
4. 点击右上角重置按钮可恢复全岛群和全部运输方式。
5. 地图右侧的区域列表可快速聚焦岛群；颜色图例区分处理中心、交换/运输设施和邮局/代理点。

地图是面向业务展示的虚拟规划视图，岛屿轮廓按群岛示意图绘制，设施点位、岛屿标签、邮编覆盖和邮路由 demo 数据库动态读取；不能用于导航或精确地理测距。管理员改动区域、岛屿、设施、服务档案或路线后，重新打开/刷新地图即可看到最新结果。

## 数据面板与管理页面

在管理员首页下方，业务数据面板继续汇总邮件、设施、运输、同步和系统监控指标。地图和这些实时业务汇总的来源不同：地图展示 Liana Pacific 规划基线；下列管理页面读取 demo MySQL 中的设施和运输记录。

- 「邮政网络 → 网点管理」：查看邮局和岛屿代理点。
- 「邮政网络 → 分拣中心」：查看国家中心和三个区域邮件中心。
- 「邮政网络 → 运输路线」：维护设施节点之间的封发/转运关系。
- 「运输管理 → 运输资源」：查看飞机、邮船和邮车演示资产。
- 「运输管理 → 运输线路」：查看跨服务可引用的运输线路。
- 「运输管理 → 运输计划」：查看 2026-11-05 周四的代表性班次模板。该日期是本演示快照的一部分，不会自动滚动到当天。

## 导入结果核验

可以在项目根目录运行以下只读查询检查导入数：

```powershell
docker exec -e MYSQL_PWD=liana_demo liana-demo-mysql mysql --user=root --table -e "SELECT 'LN facilities' AS item, COUNT(*) AS row_count FROM facility_db.facility WHERE facility_code LIKE 'LN-%' UNION ALL SELECT 'LN facility routes', COUNT(*) FROM facility_db.facility_route WHERE route_code LIKE 'LN-%' UNION ALL SELECT 'LN transport assets', COUNT(*) FROM transport_db.transport_asset WHERE code LIKE 'LN-%' UNION ALL SELECT 'LN transport routes', COUNT(*) FROM transport_db.transport_route WHERE route_code LIKE 'LN-%' UNION ALL SELECT 'demo schedules', COUNT(*) FROM transport_db.transport_schedule WHERE schedule_code LIKE 'DEMO-LN-%' UNION ALL SELECT 'postal regions', COUNT(*) FROM facility_db.demo_postal_region UNION ALL SELECT 'postal islands', COUNT(*) FROM facility_db.demo_postal_island UNION ALL SELECT 'facility profiles', COUNT(*) FROM facility_db.demo_facility_profile WHERE facility_code LIKE 'LN-%' UNION ALL SELECT 'route profiles', COUNT(*) FROM facility_db.demo_route_profile WHERE route_code LIKE 'LN-%';"
```

预期数量依次是 `15`、`9`、`3`、`9`、`9`、`4`、`12`、`15`、`9`。重复执行导入后数量保持不变。地图显示的 `12` 是覆盖岛屿数，不是设施数量。

## 演示讲解路径

1. 从管理员工作台介绍 LNPS 全国网络规模，再按区域筛选 Oro，点选区域中心和 North Reef 代理点。
2. 展示 Namoa 至 Oro 航空主线，随后切换到海运备用路线，说明航班停航时的改运选择。
3. 点选 Sela 至 Far Reef 船线，说明风暴季停航和安全网点暂存；状态变化应由实际调度/扫描产生，地图本身不模拟实时天气。
4. 进入运输路线和运输计划页面，核对路线编码、起讫设施和代表班次，再进入网点/中心页面查看主数据。
5. 以「跨岛挂号信」或「风暴季投递」作为后续业务流程演示入口。地图不创建邮件、邮袋或运输任务，也不生成轨迹事件。

## 数据和功能边界

- `latitude/longitude` 是虚构设施坐标；群岛示意图上的业务点位使用 `demo_facility_profile.map_x/map_y` 与 `demo_postal_island.map_x/map_y`。
- Facility 与 Transport 两库用 `LN-*` 稳定代码对齐，不依赖两库自增 ID 相同。
- `facility_route` 中的基础路线描述设施间拓扑；`transport_route` 对应运输服务的运输方式和计划耗时；`transport_schedule` 每条路线只放一条代表班次，不表达一周全部真实班次。`demo_route_profile` 补充频次和天气/潮汐说明。
- 地图通过 Facility 服务的只读 `/api/facilities/demo-network` 接口读取规划数据；设施和运输管理页继续读取各自业务接口。新增的岛屿、邮编和频次元数据存放在 `demo_` 扩展表中。
- 示例包含合成的岛名、设施地址和参数，没有真实居民资料或真实联系方式。
