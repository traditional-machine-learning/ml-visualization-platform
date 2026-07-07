# Machine Learning Algorithm Visualization Platform

机器学习算法可视化教学平台 - 交互式教育平台，展示传统 ML 算法的训练过程。

## 功能特性

- 📊 **算法可视化**: 线性回归、逻辑回归、决策树、SVM、K-Means、PCA、随机森林
- 🎯 **决策边界渲染**: 实时展示算法学习到的决策边界
- 📈 **步骤训练**: 分步执行训练过程，可暂停/继续
- 🤖 **AI 助教**: 内置 AI 助手解答算法相关问题

## 技术栈

**后端**: Spring Boot 3.2 + MyBatis + MySQL 8.0  
**前端**: Angular 17 + ECharts  
**容器化**: Docker + Docker Compose

## 快速开始

### Docker 部署（推荐）

```bash
cd 源代码
docker-compose up -d --build
```

访问 http://localhost

### 本地开发

**后端**
```bash
cd 源代码/backend
mvn spring-boot:run
```

**前端**
```bash
cd 源代码/frontend
npm install
npm start
```

## 项目结构

```
源代码/
├── backend/          # Spring Boot 后端
├── frontend/         # Angular 前端
├── doc/              # 项目文档
├── docker-compose.yml
├── Dockerfile.backend
└── Dockerfile.frontend
```

## 文档

详细文档见 `源代码/doc/` 目录：
- 使用文档.md - 用户使用指南
- 开发文档.md - 开发环境配置
- 部署文档.md - 生产部署指南
- 设计文档.md - 系统设计说明
- 分析文档.md - 项目分析报告

## License

MIT
