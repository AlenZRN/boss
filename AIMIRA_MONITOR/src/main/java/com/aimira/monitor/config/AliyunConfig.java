package com.aimira.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "aliyun")
public class AliyunConfig {

    /** 阿里云 AccessKey */
    private String accessKey;

    /** 阿里云 Secret */
    private String secret;

    /** 默认 Region（单区域兼容，regions 为空时使用） */
    private String region = "cn-hangzhou";

    /** 需要采集的地域列表，配置为空时回退到 region */
    private List<String> regions = Collections.emptyList();

    /**
     * 获取所有需要采集的地域。
     * 优先使用 regions 列表，为空则回退到单 region。
     */
    public List<String> getEffectiveRegions() {
        if (regions != null && !regions.isEmpty()) {
            List<String> valid = regions.stream()
                    .filter(r -> r != null && !r.isBlank())
                    .collect(Collectors.toList());
            if (!valid.isEmpty()) {
                return valid;
            }
        }
        return Collections.singletonList(region != null && !region.isBlank() ? region : "cn-hangzhou");
    }
}
