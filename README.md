# Diet Agent Learning
我的饮食推荐 Agent学习项目

## 学习目标

- 逐步实现饮食推荐 Agent
- 学习 java和springboot
- 学习 git github 和maven
- 练习操作命令

## 项目入口

- 学习版位于仓库根目录，用根目录的 `pom.xml` 构建。
- 原版后端位于 [original-backend](original-backend/README.md)，有独立的 `pom.xml` 和启动配置。运行原版前，请按目录内的说明创建独立数据库。

## 扩展方向

- [ ] **餐食查询 RPC（gRPC）**。推荐主流程跑通后，将候选餐食查询拆成独立服务。推荐服务通过 `SearchMeals`（按餐次和口味查菜）获取候选，再根据会话历史排除已推荐的菜。已有的 `search_meals` 工具也可以复用同一个 RPC 客户端。
  - 验收时先启动两个服务，跑通「推荐 → 换一批」。再停掉餐食服务，确认推荐接口能提示「餐食查询暂时不可用」，并区分查询失败与没有匹配菜品。
