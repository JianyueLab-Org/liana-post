🛠️ Liana-Post 技术设计补充文档：全局通用基础模块（liana-common）
1. 模块定位与依赖关系变迁
   liana-common 在物理上作为独立子模块存在，但在逻辑上被所有核心微服务（网关、支局、中心、追踪）强制以 Maven 坐标形式引入。它负责收拢全局数据规约、协议契约及纯公用工具方法。

1.1 依赖拓扑演进
Plaintext
+-----------------------+
|  liana-post (父POM)   |
+-----------------------+
│
▼
+-----------------------+
|     liana-common      | (纯二方库，无启动类)
+-----------------------+
┌─────────────────┼─────────────────┐
▼                 ▼                 ▼
[liana-branch-service]  [liana-center-service]  [liana-tracking-service]
2. 工程结构规范（liana-common）
   Plaintext
   liana-common/
   ├── src/main/java/com/liana/post/common/
   │   ├── constant/          # 全局业务常量（如物流状态编码、事件类型定义）
   │   ├── exception/         # 自定义全局异常（如业务校验异常 BusinessException）
   │   ├── model/             # 全局统一前后端协议契约
   │   │   └── Result.java    # 工业级通用数据响应包装器
   │   └── util/              # 硬核业务工具层
   │       └── UpuBarcodeUtil.java # 【核心攻关】万国邮政联盟 S10 邮件号校验器
   └── pom.xml                # 仅声明工具类所需的最小公共依赖（如 Fastjson2, Jackson）
3. 手动骨架搭建：liana-common 的 pom.xml
   请在父工程下新建一个名为 liana-common 的模块，将其 pom.xml 修改为以下内容（它不需要 Spring Boot 启动插件，只做纯代码打包）：

XML
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-model.4.0.0.xsd">
<modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>com.liana.post</groupId>
        <artifactId>liana-post</artifactId>
        <version>1.0.0</version>
    </parent>

    <artifactId>liana-common</artifactId>

    <dependencies>
        <dependency>
            <groupId>com.fasterxml.jackson.core</groupId>
            <artifactId>jackson-databind</artifactId>
        </dependency>
        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <scope>provided</scope>
        </dependency>
    </dependencies>
</project>
同时，记得在你的父工程 pom.xml 的 <modules> 标签里，加上这个新成员：

XML
<modules>
<module>liana-common</module> <module>liana-gateway</module>
<module>liana-branch-service</module>
<module>liana-center-service</module>
<module>liana-tracking-service</module>
</modules>
最后，在你的 liana-branch-service 的 pom.xml 中，引入这个底层模块：

XML
<dependency>
<groupId>com.liana.post</groupId>
<artifactId>liana-common</artifactId>
<version>1.0.0</version>
</dependency>
4. 指挥 AI 生成底座核心类代码
   现在骨架已经打好，你可以把下面这段导演级指令直接喂给你的 AI 助手，让它帮你把 liana-common 模块内的核心代码一键吐出来：

💡 给 AI 助手的精准开发指令：
“我正在为我的微服务项目搭建 liana-common 公共基础模块。JDK版本为21。请帮我编写该模块下的核心基础代码：

UpuBarcodeUtil.java：实现万国邮政联盟 S10 国际邮件号（13位，格式如 CX123456789LN）的自动生成与校验逻辑。第九位校验码必须严格遵循万国邮政联盟的模 11（Modulo 11）加权算法，加权系数为 8, 6, 4, 2, 3, 5, 9, 7。国家代码固定为 'LN'。

Result.java：包含状态码（code）、消息（message）、数据载荷（data）以及时间戳。支持泛型，并提供静态的 ok()、ok(T data)、error(String msg) 等链式调用方法。

请将它们分别放在 com.liana.post.common.util 和 com.liana.post.common.model 包下，直接输出完整、无截断、无编译错误的生产级 Java 代码。”