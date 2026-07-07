-- Database schema for ML Platform
SET NAMES utf8mb4;
SET character_set_client = utf8mb4;
SET character_set_connection = utf8mb4;
SET character_set_results = utf8mb4;

CREATE DATABASE IF NOT EXISTS ml_platform
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;
USE ml_platform;

-- Dataset table
CREATE TABLE IF NOT EXISTS dataset (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    file_name VARCHAR(255),
    feature_count INT,
    sample_count INT,
    features JSON,
    data_content JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Algorithm table
CREATE TABLE IF NOT EXISTS algorithm (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100) NOT NULL UNIQUE,
    display_name VARCHAR(100) NOT NULL,
    description TEXT,
    category VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    parameters JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Training session table (for other team members to extend)
CREATE TABLE IF NOT EXISTS training_session (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    algorithm_id BIGINT NOT NULL,
    dataset_id BIGINT NOT NULL,
    parameters JSON,
    status VARCHAR(50) DEFAULT 'idle',
    current_step INT DEFAULT 0,
    total_steps INT DEFAULT 100,
    metrics JSON,
    model_data JSON,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (algorithm_id) REFERENCES algorithm(id),
    FOREIGN KEY (dataset_id) REFERENCES dataset(id)
);

-- Deduplicate datasets created by repeated schema initialization and prevent future duplicates
DELETE d1 FROM dataset d1
INNER JOIN dataset d2
ON d1.name = d2.name AND d1.id > d2.id;

ALTER TABLE dataset ADD UNIQUE INDEX uk_dataset_name (name);

-- Insert predefined algorithms
INSERT INTO algorithm (name, display_name, description, category, type, parameters) VALUES
('linear_regression', '线性回归', '通过拟合一条直线来预测连续值，是监督学习中回归问题的基础算法。', 'supervised', 'regression',
 '[{"name":"learningRate","displayName":"学习率","type":"number","default":0.01,"min":0.0001,"max":1,"step":0.0001},{"name":"iterations","displayName":"迭代次数","type":"number","default":100,"min":10,"max":1000,"step":10}]'),

('logistic_regression', '逻辑回归', '用于二分类问题，通过sigmoid函数将线性输出转换为概率值。', 'supervised', 'classification',
 '[{"name":"learningRate","displayName":"学习率","type":"number","default":0.1,"min":0.0001,"max":1,"step":0.0001},{"name":"iterations","displayName":"迭代次数","type":"number","default":100,"min":10,"max":1000,"step":10},{"name":"regularization","displayName":"正则化系数","type":"number","default":0.01,"min":0,"max":1,"step":0.001}]'),

('decision_tree', '决策树', '通过一系列规则对数据进行分类或回归，结构清晰易于理解。', 'supervised', 'classification',
 '[{"name":"maxDepth","displayName":"最大深度","type":"number","default":5,"min":1,"max":20,"step":1},{"name":"minSamplesSplit","displayName":"最小分裂样本数","type":"number","default":2,"min":2,"max":50,"step":1},{"name":"criterion","displayName":"分裂准则","type":"select","default":"gini","options":["gini","entropy"]}]'),

('kmeans', 'K均值聚类', '将数据划分为K个簇，是最经典的无监督聚类算法。', 'unsupervised', 'clustering',
 '[{"name":"k","displayName":"聚类数K","type":"number","default":3,"min":2,"max":10,"step":1},{"name":"maxIterations","displayName":"最大迭代次数","type":"number","default":100,"min":10,"max":500,"step":10},{"name":"initMethod","displayName":"初始化方法","type":"select","default":"random","options":["random","kmeans++"]}]'),

('svm', '支持向量机(SVM)', '寻找最优超平面进行分类，适用于高维数据。', 'supervised', 'classification',
 '[{"name":"kernel","displayName":"核函数","type":"select","default":"rbf","options":["linear","rbf","poly"]},{"name":"c","displayName":"正则化参数C","type":"number","default":1,"min":0.1,"max":10,"step":0.1},{"name":"gamma","displayName":"核系数gamma","type":"number","default":0.1,"min":0.001,"max":1,"step":0.001},{"name":"iterations","displayName":"迭代次数","type":"number","default":100,"min":10,"max":1000,"step":10}]');

-- Insert predefined datasets (Iris dataset for demonstration)
INSERT IGNORE INTO dataset (name, description, category, feature_count, sample_count, features, data_content) VALUES
('iris', '鸢尾花数据集', 'supervised', 4, 150,
 '[{"name":"sepal_length","displayName":"花萼长度","type":"number"},{"name":"sepal_width","displayName":"花萼宽度","type":"number"},{"name":"petal_length","displayName":"花瓣长度","type":"number"},{"name":"petal_width","displayName":"花瓣宽度","type":"number"},{"name":"species","displayName":"种类","type":"label"}]',
 '[{"sepal_length":5.1,"sepal_width":3.5,"petal_length":1.4,"petal_width":0.2,"species":"setosa"},{"sepal_length":4.9,"sepal_width":3,"petal_length":1.4,"petal_width":0.2,"species":"setosa"},{"sepal_length":7,"sepal_width":3.2,"petal_length":4.7,"petal_width":1.4,"species":"versicolor"},{"sepal_length":6.4,"sepal_width":3.2,"petal_length":4.5,"petal_width":1.5,"species":"versicolor"},{"sepal_length":6.3,"sepal_width":3.3,"petal_length":6,"petal_width":2.5,"species":"virginica"},{"sepal_length":5.8,"sepal_width":2.7,"petal_length":5.1,"petal_width":1.9,"species":"virginica"}]'),

('boston_housing', '波士顿房价数据集', 'supervised', 13, 506,
 '[{"name":"crim","displayName":"城镇人均犯罪率","type":"number"},{"name":"zn","displayName":"住宅用地所占比例","type":"number"},{"name":"indus","displayName":"城镇非零售商用土地比例","type":"number"},{"name":"chas","displayName":"是否临河","type":"number"},{"name":"nox","displayName":"一氧化氮浓度","type":"number"},{"name":"rm","displayName":"住宅平均房间数","type":"number"},{"name":"age","displayName":"1940年前建成的自住单位比例","type":"number"},{"name":"dis","displayName":"到五个波士顿就业中心的加权距离","type":"number"},{"name":"rad","displayName":"距离高速公路的便利指数","type":"number"},{"name":"tax","displayName":"每一万美元的不动产税率","type":"number"},{"name":"ptratio","displayName":"城镇中学生教师比例","type":"number"},{"name":"b","displayName":"城镇中黑人比例","type":"number"},{"name":"lstat","displayName":"人口中地位低下者的比例","type":"number"},{"name":"medv","displayName":"房屋中位价","type":"label"}]',
 '[{"crim":0.00632,"zn":18,"indus":2.31,"chas":0,"nox":0.538,"rm":6.575,"age":65.2,"dis":4.09,"rad":1,"tax":296,"ptratio":15.3,"b":396.9,"lstat":4.98,"medv":24},{"crim":0.02731,"zn":0,"indus":7.07,"chas":0,"nox":0.469,"rm":6.421,"age":78.9,"dis":4.9671,"rad":2,"tax":242,"ptratio":17.8,"b":396.9,"lstat":9.14,"medv":21.6}]'),

('make_classification', '合成分类数据集', 'supervised', 2, 100,
 '[{"name":"x","displayName":"特征X","type":"number"},{"name":"y","displayName":"特征Y","type":"number"},{"name":"label","displayName":"标签","type":"label"}]',
 '[{"x":1.2,"y":2.3,"label":0},{"x":1.5,"y":2.1,"label":0},{"x":3.2,"y":4.5,"label":1},{"x":3.5,"y":4.2,"label":1},{"x":2.1,"y":3.0,"label":0},{"x":3.8,"y":5.1,"label":1}]'),

('make_blobs', '合成聚类数据集', 'unsupervised', 2, 150,
 '[{"name":"x","displayName":"特征X","type":"number"},{"name":"y","displayName":"特征Y","type":"number"}]',
 '[{"x":1.2,"y":2.3},{"x":1.5,"y":2.1},{"x":5.2,"y":6.5},{"x":5.5,"y":6.2},{"x":8.1,"y":9.0},{"x":8.5,"y":9.1}]');

-- Insert PCA and Random Forest algorithms if not present
INSERT IGNORE INTO algorithm (name, display_name, description, category, type, parameters) VALUES
('pca', 'PCA', '主成分分析，用于降维和可视化', 'unsupervised', 'pca',
 '[{"name":"n_components","displayName":"主成分数","type":"number","default":2,"min":1,"max":10,"step":1}]'),

('random_forest', 'Random Forest', '随机森林（简化模拟），用于分类/回归的集成方法', 'supervised', 'ensemble',
 '[{"name":"n_estimators","displayName":"树的数量","type":"number","default":5,"min":1,"max":100,"step":1}]');

-- Update SVM to add iterations parameter if it already exists
UPDATE algorithm SET parameters = '[{"name":"kernel","displayName":"核函数","type":"select","default":"rbf","options":["linear","rbf","poly"]},{"name":"c","displayName":"正则化参数C","type":"number","default":1,"min":0.1,"max":10,"step":0.1},{"name":"gamma","displayName":"核系数gamma","type":"number","default":0.1,"min":0.001,"max":1,"step":0.001},{"name":"iterations","displayName":"迭代次数","type":"number","default":100,"min":10,"max":1000,"step":10}]' WHERE name = 'svm';
