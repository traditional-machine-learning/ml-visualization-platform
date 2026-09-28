package com.mlplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置数据集自动填充配置。
 * 真实数据集 (iris/boston_housing) 启动时从远程 CSV 下载完整数据；
 * 合成数据集 (make_classification/make_blobs) 由程序生成完整数据。
 * 仅当表内已有数据少于目标条数时才填充，下载/生成失败时保留原有演示数据。
 */
@Component
@ConfigurationProperties(prefix = "dataset-seed")
public class DatasetSeedProperties {

    /** 总开关：false 时完全不执行填充逻辑 */
    private boolean enabled = true;

    /** 单次请求/连接超时（秒） */
    private int timeoutSeconds = 30;

    /** 代理配置（可选）。为空时依次尝试环境变量 DATASET_SEED_PROXY / HTTPS_PROXY / http_proxy */
    private String proxyHost = "";

    private int proxyPort = 0;

    /** 需要从远程下载的真实数据集 */
    private List<RemoteSource> remoteSources = new ArrayList<>();

    /** 需要程序生成的合成数据集：数据集名 -> 目标样本数 */
    private Map<String, Integer> syntheticSamples = new HashMap<>();

    public static class RemoteSource {
        /** 对应 dataset 表的 name，如 iris */
        private String name;
        /** CSV 下载地址（首行为表头，列名需与 dataset 表的 features 元数据一致） */
        private String url;
        /** 目标样本数（不含表头），少于该值才触发下载填充 */
        private int expectedSamples;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public int getExpectedSamples() { return expectedSamples; }
        public void setExpectedSamples(int expectedSamples) { this.expectedSamples = expectedSamples; }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getTimeoutSeconds() { return timeoutSeconds; }
    public void setTimeoutSeconds(int timeoutSeconds) { this.timeoutSeconds = timeoutSeconds; }
    public String getProxyHost() { return proxyHost; }
    public void setProxyHost(String proxyHost) { this.proxyHost = proxyHost; }
    public int getProxyPort() { return proxyPort; }
    public void setProxyPort(int proxyPort) { this.proxyPort = proxyPort; }
    public List<RemoteSource> getRemoteSources() { return remoteSources; }
    public void setRemoteSources(List<RemoteSource> remoteSources) { this.remoteSources = remoteSources; }
    public Map<String, Integer> getSyntheticSamples() { return syntheticSamples; }
    public void setSyntheticSamples(Map<String, Integer> syntheticSamples) { this.syntheticSamples = syntheticSamples; }
}
