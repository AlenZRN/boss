package com.aimira.monitor.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Configuration
@ConfigurationProperties(prefix = "volcengine")
public class VolcengineConfig {

    /** 火山引擎 AccessKey */
    private String accessKey;

    /** 火山引擎 Secret */
    private String secret;

    /** 采集开关（默认关闭） */
    private boolean enabled = false;

    /** 需要采集的地域列表 */
    private List<String> regions = Collections.emptyList();

    /**
     * 获取所有需要采集的地域。
     * regions 为空时返回默认地域列表。
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
        return List.of("cn-beijing", "cn-shanghai", "cn-guangzhou");
    }
}
