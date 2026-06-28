package com.aimira.monitor.collector;

import com.aimira.monitor.config.AliyunConfig;
import com.aimira.monitor.entity.ResourceInfo;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资源信息采集器
 * 使用 CommonRequest 调用各产品 API 采集云资源信息
 * MVP 先实现 ECS，后续扩展 RDS/SLB/Redis
 */
@Slf4j
@Component
public class ResourceCollector {

    private final AliyunClientFactory clientFactory;
    private final AliyunConfig aliyunConfig;
    private final ObjectMapper objectMapper;

    private static final DateTimeFormatter ALIYUN_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'");

    public ResourceCollector(AliyunClientFactory clientFactory, AliyunConfig aliyunConfig) {
        this.clientFactory = clientFactory;
        this.aliyunConfig = aliyunConfig;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 采集所有支持的资源类型
     */
    public List<ResourceInfo> collectAll() {
        List<ResourceInfo> allResources = new ArrayList<>();
        allResources.addAll(collectEcsInstances());
        // TODO: V2 - 添加 RDS、SLB、Redis 等资源采集
        log.info("资源采集完成, 共 {} 条", allResources.size());
        return allResources;
    }

    /**
     * 采集 ECS 实例
     */
    private List<ResourceInfo> collectEcsInstances() {
        List<ResourceInfo> resources = new ArrayList<>();
        try {
            IAcsClient client = clientFactory.createClient();

            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain("ecs.aliyuncs.com");
            request.setSysVersion("2014-05-26");
            request.setSysAction("DescribeInstances");
            request.putQueryParameter("PageSize", "100");

            CommonResponse response = client.getCommonResponse(request);
            JsonNode root = objectMapper.readTree(response.getData());

            JsonNode instances = root.path("Instances").path("Instance");
            if (!instances.isArray() || instances.isEmpty()) {
                log.info("当前 Region 无 ECS 实例");
                return resources;
            }

            LocalDateTime now = LocalDateTime.now();
            for (JsonNode instance : instances) {
                ResourceInfo info = ResourceInfo.builder()
                        .resourceId(instance.path("InstanceId").asText())
                        .resourceName(instance.path("InstanceName").asText())
                        .resourceType("ECS")
                        .region(aliyunConfig.getRegion())
                        .status(instance.path("Status").asText())
                        .expireTime(parseExpireTime(instance.path("ExpiredTime").asText()))
                        .syncTime(now)
                        .build();
                resources.add(info);
            }
            log.info("ECS 采集成功: region={}, count={}", aliyunConfig.getRegion(), resources.size());
        } catch (ClientException e) {
            log.error("ECS 采集 API 调用异常: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("ECS 采集响应解析异常: {}", e.getMessage(), e);
        }
        return resources;
    }

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
