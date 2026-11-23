# Diet Agent · 饮食推荐 Agent

个人项目。用户用自然语言描述想吃什么，服务识别餐次和口味，在信息不足时追问，条件齐全后从 MySQL 菜单中推荐餐食。同一会话可以继续补充偏好或要求「换一批」。

**技术栈** Java 21 · Spring Boot · MyBatis · MySQL · AgentScope · 通义千问 · Maven

## 已实现功能

- **多轮澄清与会话持久化**。通过 `sessionId` 恢复会话，合并新一轮提取的偏好，再决定追问还是查菜。 [会话服务](src/main/java/com/diet/learning/MealConversationService.java)
- **结构化偏好与规则兜底**。校验千问返回的 JSON 及字段可选值，模型未配置、超时或输出校验失败时回退到关键词匹配。 [偏好提取](src/main/java/com/diet/learning/MealPreferenceExtractor.java)
- **餐食查询与换一批**。MyBatis 按餐次和口味查询，在「换一批」时排除同一会话已推荐的菜品。 [餐食查询](src/main/resources/mapper/MealMapper.xml)
- **Agent 工具调用**。独立的 ReAct 接口注册 `search_meals`，由模型选择调用，Agent 根据工具结果回复。 [Agent 接口](src/main/java/com/diet/learning/MealAgentController.java)
- **对话 Trace**。会话接口返回 `X-Trace-Id`，记录路由、偏好提取方式、兜底原因、提取耗时和推荐结果，支持按 ID 查询。 [Trace 服务](src/main/java/com/diet/learning/AgentTraceService.java)
- **Trace 人工标注**。按 `traceId` 保存期望结果和备注，供后续离线评估与实际结果对比。
- **离线规则评估**。批量对比已标注 Trace 的实际结果和人工预期，返回准确率、平均耗时和待复查样本。

## 一轮请求怎么走

1. `MealConversationController` 接收消息，校验参数并生成本轮 Trace ID。
2. `MealPreferenceExtractor` 提取本轮餐次和口味。遇到「换一批」时直接进入换批分支。
3. `MealConversationService` 更新会话，调用 `RecommendationService` 追问缺失字段或查询餐食，并保存推荐历史。
4. `AgentTraceService` 保存本轮事件，调用方通过响应头中的 Trace ID 查看处理结果。

| 接口 | 功能 |
| --- | --- |
| `POST /api/conversations/recommendations` | 多轮澄清、推荐与换批 |
| `POST /api/agent/recommendations` | 千问 ReAct 工具调用 |
| `GET /api/meals` | 按餐次和口味查询菜单 |
| `GET /api/traces/{traceId}` | 查询单轮会话 Trace |
| `PUT /api/traces/{traceId}/label` | 新增或覆盖人工标注 |
| `GET /api/traces/{traceId}/label` | 查询该轮的人工标注 |
| `GET /api/evaluations/traces` | 批量评估有人工标注的 Trace |
| `GET /api/health` | 检查 HTTP 服务是否启动 |

## 本地运行

需要 Java 21、Maven 和 MySQL。默认连接 `127.0.0.1:3306/diet_agent_learning`，使用 `diet_app` 账号。可以通过 `SPRING_DATASOURCE_URL` 和 `SPRING_DATASOURCE_USERNAME` 覆盖连接地址和账号。

在空数据库中建立 `meal_item` 并录入演示数据。

```sql
CREATE TABLE meal_item (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    meal_time VARCHAR(20) NOT NULL,
    taste VARCHAR(20) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO meal_item (name, meal_time, taste) VALUES
    ('清蒸鲈鱼', '晚餐', '清淡'),
    ('冬瓜虾仁', '晚餐', '清淡');
```

在同一个数据库中依次执行 [会话表](sql/meal_conversation.sql)、[推荐历史字段](sql/add_last_recommendation_ids.sql)、[Trace 表](sql/meal_agent_trace.sql)和 [Trace 标注表](sql/meal_agent_trace_label.sql)脚本。已有前三项结构的数据库只需创建标注表。应用账号应能读取 `meal_item`、读写 `meal_conversation`、查询和插入 `meal_agent_trace`，并查询、插入和更新 `meal_agent_trace_label`。

在运行 Maven 的终端设置 `DIET_DB_PASSWORD` 环境变量。如果要体验千问偏好提取或调用 ReAct 接口，再设置 `DASHSCOPE_API_KEY` 和 `DASHSCOPE_BASE_URL`。

```sh
mvn spring-boot:run
```

## 接口演示

第一轮只提供餐次，预期返回 `ASK`（等待补充口味）及 `sessionId`。

```sh
curl -i http://127.0.0.1:8080/api/conversations/recommendations \
  -H 'Content-Type: application/json' \
  -d '{"message":"我想吃晚餐"}'
```

沿用返回的 `sessionId`，依次发送「清淡」和「换一批」。使用上面的演示数据，第二轮返回清蒸鲈鱼，换批返回冬瓜虾仁。用任一轮响应头的 `X-Trace-Id` 查询 `GET /api/traces/{traceId}`。

普通对话的 Trace 包含 `PREFERENCES_EXTRACTED`。查看 `details.extraction.source` 可以区分千问提取（`MODEL`）和关键词匹配（`KEYWORDS`）。关键词兜底会记录 `fallbackReason`，例如 `MODEL_NOT_CONFIGURED` 或 `MODEL_FAILURE`。`preferences` 中只记录本轮抽到的餐次和口味。换批直接沿用会话中的偏好，Trace 记录 `CHANGE_BATCH` 路由。`REQUEST_FINISHED` 的 `mealCount` 是本轮返回的餐食数量。

### 标注一轮对话

用第一轮响应头的 `X-Trace-Id` 替换示例中的 ID。`expectedStatus` 可填 `ASK`（追问）、`RECOMMEND`（推荐）或 `NO_MATCH`（无匹配）。`note` 可省略，最多 500 字符。每条 Trace 保留一份标注，再次 `PUT` 会覆盖原标注。

```sh
TRACE_ID='<第一轮 X-Trace-Id>'
curl -i -X PUT "http://127.0.0.1:8080/api/traces/$TRACE_ID/label" \
  -H 'Content-Type: application/json' \
  -d '{"expectedStatus":"ASK","note":"缺少口味，应该先追问"}'
curl -sS "http://127.0.0.1:8080/api/traces/$TRACE_ID/label"
```

保存成功返回 `204`。用 Trace 的 `REQUEST_FINISHED.details.resultStatus` 对比标注中的 `expectedStatus`，即可判断这一轮的结果是否符合预期。

### 批量评估标注样本

```sh
curl -sS 'http://127.0.0.1:8080/api/evaluations/traces?limit=20'
```

默认按创建时间从新到旧取最近 100 条已标注 Trace，可用 `limit` 指定 1 到 500 条，也可用 `sessionId` 限定会话。评估读取已保存的结果，无需再次请求模型。

- `accuracyPercent` 用命中数量除以可评分样本数，再乘以 100。明确记录了请求失败的样本计入分母，缺少有效结果事件的样本记为无法评分。没有可评分样本时返回 `null`。
- `averageDurationMs` 是所选样本的平均请求处理耗时。`issues` 包含结果不符、请求失败和缺少有效结果的样本，可用其中的 `traceId` 查询原始事件。

## 计划扩展

- **餐食查询 RPC（gRPC）**。将 `SearchMeals` 查询拆为独立服务，推荐流程和 `search_meals` 共用同一个 RPC 客户端。验收覆盖推荐、换批、空结果和远程服务不可用四种情况。
