# 启动排障记录与原因分析

## 范围

本次记录覆盖 `gateway`、`auth`、`facility`、`oms`、`dispatch`、`tracking` 的实际启动排障过程，重点整理最近暴露出来的 6 个关键故障点、处理动作和根因。

## 结论摘要

- 根因不是单一业务代码，而是 **Spring Boot / Spring Cloud / Alibaba / MyBatis-Plus / Feign / Nacos** 版本与装配方式混杂。
- 通过统一根 `pom.xml` 的 BOM、补齐 `loadbalancer`、修正 Feign 懒加载、修正 MyBatis 扫描与 SQL 初始化路径，所有关键服务已恢复可启动状态。

## 故障点 1：Gateway 启动时 Spring WebFlux / Spring Web 版本不兼容

- **现象**：`PathPatternParser.initFullPathPattern(String)` 不存在，`spring-webflux` 与 `spring-web` 版本冲突。
- **涉及模块**：`gateway`
- **处理动作**：统一根 `pom.xml` 到兼容的 Spring Boot / Spring Cloud 版本，并回收 gateway 的依赖栈。
- **根因分析**：网关模块处在响应式链路上，对 `spring-web`、`spring-webflux`、`reactor-netty` 的版本一致性极其敏感；旧版 BOM 或手工钉版本很容易引发类方法缺失。

## 故障点 2：OMS 启动时 MyBatis-Plus / Spring 6.1 兼容异常

- **现象**：`factoryBeanObjectType` 出现 `java.lang.String` 非法值，ApplicationContext 无法完成初始化。
- **涉及模块**：`oms`
- **处理动作**：将 `mybatis-plus-spring-boot3-starter` 升级到与 Spring Boot 3.2 对齐的版本，并让 Oms 侧 Repository/Feign 依赖更稳妥。
- **根因分析**：旧版 `mybatis-spring` 与 Spring 6.1 的 Bean 元数据处理不兼容，导致 `FactoryBean` 属性解析失败。

## 故障点 3：OMS 的空 Feign 客户端导致启动链不稳定

- **现象**：`DispatchClient`/相关 Feign 代理在启动阶段被强制创建，进一步放大了 Bean 创建失败。
- **涉及模块**：`oms`
- **处理动作**：删除空的 `FacilityClient`，并把跨服务 Feign 改为 `ObjectProvider` 懒加载。
- **根因分析**：Feign 客户端如果没有明确的方法契约，或者在启动阶段就被强制创建，会把远端依赖和本地 Bean 装配耦合到一起，导致启动期失败被放大。

## 故障点 4：Dispatch 启动时 OmsClient 的 FactoryBean 创建失败

- **现象**：`dispatchController -> dispatchServiceImpl -> OmsClient` 链路在 ApplicationContext 阶段报 `FactoryBean threw exception on object creation`。
- **涉及模块**：`dispatch`
- **处理动作**：将 `OmsClient` 改成懒加载注入，业务运行时再获取代理。
- **根因分析**：Dispatch 在启动期过早依赖 Oms 的 Feign 代理；当远端服务、负载均衡或注册中心链路尚未完全就绪时，代理创建会直接拖垮本地启动。

## 故障点 5：Tracking 缺少 Nacos 配置导入与负载均衡依赖

- **现象**：`No spring.config.import property has been defined`，同时 `OMSClient` 的跨服务调用链需要 `loadbalancer` 支撑。
- **涉及模块**：`tracking`
- **处理动作**：在 `application.yml` 中增加 `spring.config.import: optional:nacos:`，并为 `tracking` 以及所有 Feign / Gateway 模块补齐 `spring-cloud-starter-loadbalancer`。
- **根因分析**：Spring Cloud Alibaba 新版要求显式声明配置导入；跨服务 Feign 又必须具备负载均衡能力，否则服务名调用在代理创建或请求阶段都可能异常。

## 故障点 6：Tracking 的 Repository / Mapper / SQL 初始化链路配置错误

- **现象**：
  - `TrackingRepository` 与 `MyBatisTrackingRepository` 存在二义性注入；
  - `TrackingEventMapper` 没有被正确扫描；
  - `schema.sql` 默认路径找不到真实脚本。
- **涉及模块**：`tracking`
- **处理动作**：
  - 将 `TrackingServiceImpl` 的依赖改为明确注入 `MyBatisTrackingRepository`；
  - 将 `@MapperScan` 修正为 `com.liana.post.tracking.mapper`；
  - 将 SQL 初始化路径改为根目录 `db/tracking_db.sql`；
  - 完成编译验证。
- **根因分析**：这是典型的“扫描包错误 + 默认初始化路径错误 + Bean 重名”组合问题，Spring 6.1 对 Bean 解析更严格，因此错误会在启动期直接暴露。

## 额外联动检查

- 已横向检查 `gateway`、`auth`、`facility`、`oms`、`dispatch`、`tracking`、`syncer` 的跨服务调用模块。
- 所有使用 `@FeignClient` 或 Gateway 的模块都已补齐 `spring-cloud-starter-loadbalancer`。
- 根 `pom.xml` 已统一升级到更稳定的 Boot / Cloud / Alibaba 组合，避免后续继续出现方法缺失、Bean 属性不兼容和自动装配异常。

## 验证结果

- `liana-tracking-service`：已通过编译检查。
- `liana-oms-service`：已通过编译检查。
- `liana-dispatch-service`：已通过编译检查。
- 根工程关键模块依赖栈已完成统一对齐。

## 后续建议

- 新增跨服务 Feign 前，统一检查：`openfeign`、`loadbalancer`、Nacos 配置导入、`MapperScan`、SQL 初始化路径。
- 如果继续扩展业务，建议把“服务启动检查清单”作为固定发布前步骤，避免再出现同类启动事故。
