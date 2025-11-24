# LoLSmartStatsWeb - Backend

这是 LoLSmartStatsWeb 的后端服务，基于 Spring Boot 框架构建，提供 RESTful API 接口。

## 🛠️ 技术栈

*   **语言**: Java 17
*   **框架**: Spring Boot 3.2.0
*   **构建工具**: Maven

## ⚙️ 配置说明

配置文件位于 `src/main/resources/application.properties`。

默认配置：
```properties
server.port=8080
spring.application.name=backend
```

如果需要修改端口或其他配置，请直接编辑该文件。

## 🚀 运行指南

### 使用 Maven 运行 (开发模式)

在 `backend` 目录下执行：

```bash
mvn spring-boot:run
```

### 打包并运行 (生产模式)

1.  **打包构建**：

    ```bash
    mvn clean package
    ```

    构建成功后，会在 `target` 目录下生成 `backend-0.0.1-SNAPSHOT.jar` 文件。

2.  **运行 Jar 包**：

    ```bash
    java -jar target/backend-0.0.1-SNAPSHOT.jar
    ```

## 🔌 API 接口

服务启动后，API 基础地址为：`http://localhost:8080`

（在此处可以补充具体的 API 文档链接或 Swagger 地址，如果已集成）
