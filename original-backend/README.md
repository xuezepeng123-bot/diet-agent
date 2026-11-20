# Diet Agent Learning
我的饮食推荐 Agent学习项目

## 学习目标

- 逐步实现饮食推荐 Agent
- 学习 java和springboot
- 学习 git github 和maven
- 练习操作命令

## 原版后端

此分支从提供的 Diet-Agent 下载包导入 Java 源码、提示词、Mapper 和数据库脚本。建议先读 `DietChatController.dietChat()`，再顺着请求进入 `DietOrchestratorService.handleTurn()`。

用 Java 21 和 Maven 运行。先创建一个独立的空 MySQL 数据库，再导入 `src/main/resources/db/diet_db.sql`。原版 SQL 包含重建表的语句，导入前请确认选中的是新库。

启动前在同一个终端设置以下环境变量。

| 变量 | 用途 |
| --- | --- |
| `DIET_DB_URL` | 原版独立数据库的 JDBC 地址 |
| `DIET_DB_USER` | 原版数据库的应用账号 |
| `DIET_DB_PASSWORD` | 原版数据库的应用账号密码 |
| `DASHSCOPE_API_KEY` | 百炼模型密钥 |

```zsh
SERVER_ADDRESS=127.0.0.1 mvn spring-boot:run
```

启动后可以向 `POST /api/v1/diet/chat` 发送对话，再用返回的 `traceId` 请求 `GET /api/v1/diet/debug/traces/{traceId}` 查看这轮的事件。
