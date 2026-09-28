package com.mlplatform.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mlplatform.config.DatasetSeedProperties;
import com.mlplatform.model.Dataset;
import com.mlplatform.repository.DatasetRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 内置数据集自动填充器。
 * <p>
 * 在 Spring 初始化（schema.sql 已写入演示数据）之后运行：
 * <ul>
 *   <li>iris / boston_housing：从远程 CSV 下载完整真实数据；</li>
 *   <li>make_classification / make_blobs：由程序生成完整合成数据。</li>
 * </ul>
 * 幂等策略：只有当表内已有数据条数少于目标条数时才填充；
 * 下载/解析/生成任何一步失败都只记日志、保留原有演示数据，绝不影响应用启动。
 */
@Service
public class DatasetSeedInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DatasetSeedInitializer.class);

    private final DatasetRepository datasetRepository;
    private final ObjectMapper objectMapper;
    private final DatasetSeedProperties properties;

    private HttpClient httpClient;

    public DatasetSeedInitializer(DatasetRepository datasetRepository,
                                  ObjectMapper objectMapper,
                                  DatasetSeedProperties properties) {
        this.datasetRepository = datasetRepository;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!properties.isEnabled()) {
                log.info("[数据集填充] 已通过配置 dataset-seed.enabled=false 关闭");
                return;
            }
            log.info("[数据集填充] 开始检查内置数据集，目标数量不足时自动下载/生成完整数据");
            for (DatasetSeedProperties.RemoteSource source : properties.getRemoteSources()) {
                fillFromRemote(source);
            }
            for (Map.Entry<String, Integer> entry : properties.getSyntheticSamples().entrySet()) {
                fillSynthetic(entry.getKey(), entry.getValue());
            }
            log.info("[数据集填充] 检查完毕");
        } catch (Exception e) {
            // 兜底：任何意外都不影响应用启动，只记录日志
            log.error("[数据集填充] 执行异常（保留原有数据继续启动）: {}", e.getMessage());
        }
    }

    // ==================== 远程真实数据集 ====================

    private void fillFromRemote(DatasetSeedProperties.RemoteSource source) {
        String name = source.getName();
        try {
            Dataset dataset = datasetRepository.findByName(name);
            if (dataset == null) {
                log.warn("[数据集填充] 数据集 {} 不存在（schema.sql 未预置？），跳过", name);
                return;
            }
            int current = countRows(dataset);
            if (current >= source.getExpectedSamples()) {
                log.info("[数据集填充] {} 已有 {} 条，无需填充", name, current);
                return;
            }
            log.info("[数据集填充] {} 现有 {} 条，尝试从 {} 下载 {} 条完整数据",
                    name, current, source.getUrl(), source.getExpectedSamples());

            String csv = downloadCsv(source.getUrl());
            List<Map<String, Object>> rows = parseCsv(csv, name, source.getExpectedSamples());
            saveRows(dataset, rows, name, "下载");
        } catch (Exception e) {
            log.warn("[数据集填充] {} 填充失败，保留原有演示数据: {}", name, e.getMessage());
        }
    }

    private String downloadCsv(String urlString) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(urlString))
                .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                .header("User-Agent", "ml-visualization-platform/1.0")
                .GET()
                .build();
        HttpResponse<String> response = httpClient().send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IOException("HTTP " + response.statusCode());
        }
        return response.body();
    }

    /** 解析 CSV：表头列名与 dataset 表 features 元数据对齐，行内 key 顺序与 features 声明一致 */
    private List<Map<String, Object>> parseCsv(String csv, String datasetName, int expectedSamples)
            throws Exception {
        List<FeatureMeta> features = parseFeatures(datasetName);

        String[] lines = csv.split("\r?\n");
        if (lines.length == 0 || lines[0].isBlank()) {
            throw new IOException("CSV 为空或缺少表头");
        }
        if (lines[0].startsWith("\uFEFF")) {
            lines[0] = lines[0].substring(1); // 去掉 UTF-8 BOM
        }
        String[] header = lines[0].split(",");
        Map<String, Integer> columnIndex = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            columnIndex.put(cleanCsvCell(header[i]), i);
        }
        for (FeatureMeta feature : features) {
            if (!columnIndex.containsKey(feature.name())) {
                throw new IOException("CSV 表头缺少数据集声明的列 '" + feature.name()
                        + "'（实际表头: " + lines[0] + "）");
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int skipped = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cells = line.split(",");
            Map<String, Object> row = new LinkedHashMap<>();
            boolean valid = true;
            for (FeatureMeta feature : features) {
                int col = columnIndex.get(feature.name());
                String raw = col < cells.length ? cleanCsvCell(cells[col]) : "";
                if (feature.label()) {
                    // 标签列：数值（如波士顿 medv）存为数字，文本（如 species）存为字符串
                    Double number = parseNumber(raw);
                    row.put(feature.name(), number != null ? number : raw);
                } else {
                    Double number = parseNumber(raw);
                    if (number == null) {
                        valid = false; // 数值列缺值/格式错误，丢弃该行
                        break;
                    }
                    row.put(feature.name(), number);
                }
            }
            if (valid) {
                rows.add(row);
            } else {
                skipped++;
            }
        }

        if (rows.size() < expectedSamples) {
            throw new IOException("有效数据仅 " + rows.size() + " 条（期望 ≥ " + expectedSamples
                    + (skipped > 0 ? "，跳过 " + skipped + " 行格式错误数据" : "") + "）");
        }
        log.info("[数据集填充] {} CSV 解析完成：{} 条有效数据{}", datasetName, rows.size(),
                skipped > 0 ? "（跳过 " + skipped + " 行）" : "");
        return rows;
    }

    // ==================== 合成数据集 ====================

    private void fillSynthetic(String name, int samples) {
        try {
            Dataset dataset = datasetRepository.findByName(name);
            if (dataset == null) {
                log.warn("[数据集填充] 数据集 {} 不存在（schema.sql 未预置？），跳过", name);
                return;
            }
            int current = countRows(dataset);
            if (current >= samples) {
                log.info("[数据集填充] {} 已有 {} 条，无需填充", name, current);
                return;
            }
            log.info("[数据集填充] {} 现有 {} 条，程序生成 {} 条完整数据", name, current, samples);

            List<Map<String, Object>> rows;
            if ("make_classification".equals(name)) {
                rows = generateClassification(samples);
            } else if ("make_blobs".equals(name)) {
                rows = generateBlobs(samples);
            } else {
                log.warn("[数据集填充] 未知的合成数据集名 {}，跳过", name);
                return;
            }
            saveRows(dataset, rows, name, "生成");
        } catch (Exception e) {
            log.warn("[数据集填充] {} 填充失败，保留原有演示数据: {}", name, e.getMessage());
        }
    }

    /** 两个高斯簇：类别 0 中心 (2.0,2.0)，类别 1 中心 (4.5,4.5)，两簇轻微重叠便于展示分类边界 */
    private List<Map<String, Object>> generateClassification(int samples) throws Exception {
        Random random = new Random(42L);
        List<Map<String, Object>> rows = new ArrayList<>();
        int perClass = samples / 2;
        double[][] centers = { { 2.0, 2.0 }, { 4.5, 4.5 } };
        for (int label = 0; label < 2; label++) {
            for (int i = 0; i < perClass; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("x", round4(centers[label][0] + random.nextGaussian() * 0.9));
                row.put("y", round4(centers[label][1] + random.nextGaussian() * 0.9));
                row.put("label", String.valueOf(label));
                rows.add(row);
            }
        }
        return rows;
    }

    /** 三个高斯簇（50 条 × 3 簇），附带 clusterId 便于未训练前按簇着色 */
    private List<Map<String, Object>> generateBlobs(int samples) {
        Random random = new Random(42L);
        List<Map<String, Object>> rows = new ArrayList<>();
        int perCluster = samples / 3;
        double[][] centers = { { 1.5, 2.0 }, { 5.3, 6.3 }, { 8.3, 9.0 } };
        for (int cluster = 0; cluster < 3; cluster++) {
            for (int i = 0; i < perCluster; i++) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("x", round4(centers[cluster][0] + random.nextGaussian() * 0.6));
                row.put("y", round4(centers[cluster][1] + random.nextGaussian() * 0.6));
                row.put("clusterId", cluster);
                rows.add(row);
            }
        }
        return rows;
    }

    // ==================== 公共辅助 ====================

    /** 写回数据库：sample_count / feature_count（数值特征数）与数据内容一起更新 */
    private void saveRows(Dataset dataset, List<Map<String, Object>> rows, String name, String mode)
            throws Exception {
        String json = objectMapper.writeValueAsString(rows);
        dataset.setDataContent(json);
        dataset.setSampleCount(rows.size());
        int numericCount = (int) parseFeatures(name).stream().filter(f -> !f.label()).count();
        dataset.setFeatureCount(numericCount);
        datasetRepository.update(dataset);
        log.info("[数据集填充] {} {}完成，共 {} 条数据已写入", name, mode, rows.size());
    }

    /** 当前 dataset 表中已存的数据条数 */
    private int countRows(Dataset dataset) {
        try {
            if (dataset.getDataContent() == null || dataset.getDataContent().isBlank()) {
                return 0;
            }
            return objectMapper.readValue(dataset.getDataContent(), new TypeReference<List<Object>>() {}).size();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 读取 dataset.features JSON：按声明顺序返回每个特征的名字与是否标签列 */
    private List<FeatureMeta> parseFeatures(String datasetName) throws Exception {
        Dataset dataset = datasetRepository.findByName(datasetName);
        if (dataset == null || dataset.getFeatures() == null || dataset.getFeatures().isBlank()) {
            throw new IOException("数据集 " + datasetName + " 缺少 features 元数据");
        }
        List<Map<String, Object>> raw = objectMapper.readValue(
                dataset.getFeatures(), new TypeReference<List<Map<String, Object>>>() {});
        List<FeatureMeta> result = new ArrayList<>();
        for (Map<String, Object> feature : raw) {
            result.add(new FeatureMeta(
                    String.valueOf(feature.get("name")),
                    "label".equals(feature.get("type"))));
        }
        return result;
    }

    /** 清理 CSV 单元格：去首尾空白；若被双引号包裹则剥掉引号并把内部 "" 还原为 "（兼容 boston 等全字段带引号的 CSV） */
    private String cleanCsvCell(String cell) {
        if (cell == null) {
            return "";
        }
        String cleaned = cell.trim();
        if (cleaned.length() >= 2 && cleaned.startsWith("\"") && cleaned.endsWith("\"")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).replace("\"\"", "\"");
        }
        return cleaned;
    }

    private Double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private double round4(double value) {
        return (double) Math.round(value * 10000) / 10000;
    }

    private record FeatureMeta(String name, boolean label) {}

    // ==================== HTTP 客户端（带代理支持） ====================

    private HttpClient httpClient() {
        if (httpClient == null) {
            HttpClient.Builder builder = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                    .followRedirects(HttpClient.Redirect.NORMAL);
            InetSocketAddress proxy = resolveProxy();
            if (proxy != null) {
                builder.proxy(ProxySelector.of(proxy));
                log.info("[数据集填充] 走代理 {}:{} 下载", proxy.getHostString(), proxy.getPort());
            }
            httpClient = builder.build();
        }
        return httpClient;
    }

    /**
     * 代理解析优先级：环境变量 DATASET_SEED_PROXY → HTTPS_PROXY/https_proxy/HTTP_PROXY/http_proxy
     * → 配置文件 proxy-host/proxy-port。容器里无法直连宿主机代理时，
     * 可在 docker-compose 给 backend 加 HTTPS_PROXY=http://host.docker.internal:7890。
     */
    private InetSocketAddress resolveProxy() {
        String fromEnv = firstNonBlank(
                System.getenv("DATASET_SEED_PROXY"),
                System.getenv("HTTPS_PROXY"), System.getenv("https_proxy"),
                System.getenv("HTTP_PROXY"), System.getenv("http_proxy"));
        if (fromEnv != null && !fromEnv.isBlank()) {
            return parseProxyUri(fromEnv);
        }
        if (properties.getProxyHost() != null && !properties.getProxyHost().isBlank()
                && properties.getProxyPort() > 0) {
            return new InetSocketAddress(properties.getProxyHost(), properties.getProxyPort());
        }
        return null;
    }

    private InetSocketAddress parseProxyUri(String raw) {
        try {
            String text = raw.contains("://") ? raw : "http://" + raw;
            URI uri = URI.create(text);
            if (uri.getHost() == null) {
                return null;
            }
            return new InetSocketAddress(uri.getHost(), uri.getPort() > 0 ? uri.getPort() : 80);
        } catch (Exception e) {
            log.warn("[数据集填充] 代理地址解析失败（忽略，直连）: {}", raw);
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
