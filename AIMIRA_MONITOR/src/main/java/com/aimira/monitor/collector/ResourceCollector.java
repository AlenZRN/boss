package com.aimira.monitor.collector;

import com.aimira.monitor.config.AliyunConfig;
import com.aimira.monitor.entity.ResourceInfo;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesRequest;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse;
import com.aliyuncs.ecs.model.v20140526.DescribeInstancesResponse.Instance;
import com.aliyuncs.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资源信息采集器
 * 采集 ECS、RDS、SLB 等云资源基本信息及到期时间
 * MVP 先实现 ECS 采集，后续扩展其他产品
 */
@Slf4j
@Component
public class ResourceCollector {

    private final AliyunClientFactory clientFactory;
    private final AliyunConfig aliyunConfig;

    private static final DateTimeFormatter ALIYUN_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public ResourceCollector(AliyunClientFactory clientFactory, AliyunConfig aliyunConfig) {
        this.clientFactory = clientFactory;
        this.aliyunConfig = aliyunConfig;
    }

    /**
     * 采集所有支持资源类型的基础信息
     * @return 资源信息列表
     */
    public List<ResourceInfo> collectAll() {
        List<ResourceInfo> allResources = new ArrayList<>();
        allResources.addAll(collectEcsInstances());
        // TODO: V2 - 添加 RDS、SLB、Redis 等资源采集
        log.info("资源采集完成, 共 {} 条", allResources.size());
        return allResources;
    }

    /**
     * 采集 ECS 实例信息
     */
    private List<ResourceInfo> collectEcsInstances() {
        List<ResourceInfo> resources = new ArrayList<>();
        try {
            IAcsClient client = clientFactory.createClient();
            DescribeInstancesRequest request = new DescribeInstancesRequest();
            request.setPageSize(100);

            DescribeInstancesResponse response = client.getAcsResponse(request);
            if (response.getInstances() == null || response.getInstances().isEmpty()) {
                log.info("当前 Region 无 ECS 实例");
                return resources;
            }

            LocalDateTime now = LocalDateTime.now();
            for (Instance instance : response.getInstances()) {
                ResourceInfo info = ResourceInfo.builder()
                        .resourceId(instance.getInstanceId())
                        .resourceName(instance.getInstanceName())
                        .resourceType("ECS")
                        .region(aliyunConfig.getRegion())
                        .status(instance.getStatus())
                        .expireTime(parseExpireTime(instance.getExpiredTime()))
                        .syncTime(now)
                        .build();
                resources.add(info);
            }
            log.info("ECS 采集成功: region={}, count={}", aliyunConfig.getRegion(), resources.size());
        } catch (ClientException e) {
            log.error("ECS 采集 API 调用异常: {}", e.getMessage(), e);
        }
        return resources;
    }

    /**
     * 解析阿里云返回的到期时间字符串
     * 格式: "yyyy-MM-dd'T'HH:mm:ss'Z'" (UTC)
     */
    private LocalDateTime parseExpireTime(String expiredTime) {
        if (expiredTime == null || expiredTime.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(expiredTime, ALIYUN_TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("到期时间解析失败: {}", expiredTime);
            return null;
        }
    }
}
