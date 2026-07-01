package com.aimira.monitor.collector;

import com.aimira.monitor.config.AliyunConfig;
import com.aimira.monitor.entity.ResourceInfo;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.http.ProtocolType;
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
 * 支持: ECS / SWAS(轻量应用服务器) / RDS
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
        allResources.addAll(collectSwasInstances());
        allResources.addAll(collectRdsInstances());
        log.info("资源采集完成, 共 {} 条", allResources.size());
        return allResources;
    }

    /**
     * 采集 ECS 实例（遍历所有配置的地域）
     */
    private List<ResourceInfo> collectEcsInstances() {
        List<ResourceInfo> resources = new ArrayList<>();
        for (String region : aliyunConfig.getEffectiveRegions()) {
            try {
                IAcsClient client = clientFactory.createClient(region);

                CommonRequest request = new CommonRequest();
                request.setSysProtocol(ProtocolType.HTTPS);
                request.setSysMethod(MethodType.POST);
                request.setSysDomain("ecs.aliyuncs.com");
                request.setSysVersion("2014-05-26");
                request.setSysAction("DescribeInstances");
                request.putQueryParameter("RegionId", region);
                request.putQueryParameter("PageSize", "100");

                CommonResponse response = client.getCommonResponse(request);
                String responseData = response.getData();
                log.debug("ECS API 响应: region={}, body={}", region, responseData);
                JsonNode root = objectMapper.readTree(responseData);

                JsonNode instances = root.path("Instances").path("Instance");
                if (!instances.isArray() || instances.isEmpty()) {
                    log.info("ECS: region={} 无实例", region);
                    continue;
                }

                LocalDateTime now = LocalDateTime.now();
                for (JsonNode instance : instances) {
                    resources.add(ResourceInfo.builder()
                            .resourceId(instance.path("InstanceId").asText())
                            .resourceName(instance.path("InstanceName").asText())
                            .resourceType("ECS")
                            .region(region)
                            .status(instance.path("Status").asText())
                            .expireTime(parseExpireTime(instance.path("ExpiredTime").asText()))
                            .syncTime(now)
                            .build());
                }
                log.info("ECS 采集成功: region={}, count={}", region, instances.size());
            } catch (ClientException e) {
                log.error("ECS 采集 API 异常: region={}, errorCode={}", region, e.getErrCode());
                log.debug("ECS 采集完整异常", e);
            } catch (Exception e) {
                log.error("ECS 采集解析异常: region={}, {}", region, e.getMessage(), e);
            }
        }
        return resources;
    }

    /**
     * 采集轻量应用服务器 (SWAS) 实例（遍历所有配置的地域）
     * 注意：SWAS 不支持中心域名，必须使用区域域名 swas.{region}.aliyuncs.com
     */
    private List<ResourceInfo> collectSwasInstances() {
        List<ResourceInfo> resources = new ArrayList<>();
        for (String region : aliyunConfig.getEffectiveRegions()) {
            try {
                IAcsClient client = clientFactory.createClient(region);

                CommonRequest request = new CommonRequest();
                request.setSysProtocol(ProtocolType.HTTPS);
                request.setSysMethod(MethodType.POST);
                request.setSysDomain("swas." + region + ".aliyuncs.com");
                request.setSysVersion("2020-06-01");
                request.setSysAction("ListInstances");
                request.putQueryParameter("RegionId", region);
                request.putQueryParameter("PageSize", "100");

                CommonResponse response = client.getCommonResponse(request);
                String responseData = response.getData();
                log.debug("SWAS API 响应: region={}, body={}", region, responseData);
                JsonNode root = objectMapper.readTree(responseData);

                JsonNode instances = root.path("Instances");
                if (!instances.isArray() || instances.isEmpty()) {
                    log.info("SWAS: region={} 无实例", region);
                    continue;
                }

                LocalDateTime now = LocalDateTime.now();
                for (JsonNode instance : instances) {
                    String rawExpiredTime = instance.path("ExpiredTime").asText();
                    log.info("SWAS 实例到期时间原始值: resourceId={}, resourceName={}, ExpiredTime='{}'",
                            instance.path("InstanceId").asText(),
                            instance.path("InstanceName").asText(),
                            rawExpiredTime);
                    resources.add(ResourceInfo.builder()
                            .resourceId(instance.path("InstanceId").asText())
                            .resourceName(instance.path("InstanceName").asText())
                            .resourceType("SWAS")
                            .region(region)
                            .status(instance.path("Status").asText())
                            .expireTime(parseExpireTime(rawExpiredTime))
                            .syncTime(now)
                            .build());
                }
                log.info("SWAS 采集成功: region={}, count={}", region, instances.size());
            } catch (ClientException e) {
                log.error("SWAS 采集 API 异常: region={}, errorCode={}", region, e.getErrCode());
                log.debug("SWAS 采集完整异常", e);
            } catch (Exception e) {
                log.error("SWAS 采集解析异常: region={}, {}", region, e.getMessage(), e);
            }
        }
        return resources;
    }

    /**
     * 采集 RDS 数据库实例（遍历所有配置的地域）
     */
    private List<ResourceInfo> collectRdsInstances() {
        List<ResourceInfo> resources = new ArrayList<>();
        for (String region : aliyunConfig.getEffectiveRegions()) {
            try {
                IAcsClient client = clientFactory.createClient(region);

                CommonRequest request = new CommonRequest();
                request.setSysProtocol(ProtocolType.HTTPS);
                request.setSysMethod(MethodType.POST);
                request.setSysDomain("rds.aliyuncs.com");
                request.setSysVersion("2014-08-15");
                request.setSysAction("DescribeDBInstances");
                request.putQueryParameter("RegionId", region);
                request.putQueryParameter("PageSize", "100");

                CommonResponse response = client.getCommonResponse(request);
                String responseData = response.getData();
                log.debug("RDS API 响应: region={}, body={}", region, responseData);
                JsonNode root = objectMapper.readTree(responseData);

                JsonNode instances = root.path("Items").path("DBInstance");
                if (!instances.isArray() || instances.isEmpty()) {
                    log.info("RDS: region={} 无实例", region);
                    continue;
                }

                LocalDateTime now = LocalDateTime.now();
                for (JsonNode instance : instances) {
                    resources.add(ResourceInfo.builder()
                            .resourceId(instance.path("DBInstanceId").asText())
                            .resourceName(instance.path("DBInstanceDescription").asText())
                            .resourceType("RDS")
                            .region(region)
                            .status(instance.path("DBInstanceStatus").asText())
                            .expireTime(parseExpireTime(instance.path("ExpireTime").asText()))
                            .syncTime(now)
                            .build());
                }
                log.info("RDS 采集成功: region={}, count={}", region, instances.size());
            } catch (ClientException e) {
                log.error("RDS 采集 API 异常: region={}, errorCode={}", region, e.getErrCode());
                log.debug("RDS 采集完整异常", e);
            } catch (Exception e) {
                log.error("RDS 采集解析异常: region={}, {}", region, e.getMessage(), e);
            }
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
