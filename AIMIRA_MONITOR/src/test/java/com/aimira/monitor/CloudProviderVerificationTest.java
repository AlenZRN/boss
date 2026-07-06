package com.aimira.monitor;

import com.aimira.monitor.entity.BalanceHistory;
import com.aimira.monitor.entity.ResourceInfo;
import com.aimira.monitor.repository.BalanceHistoryRepository;
import com.aimira.monitor.repository.ResourceInfoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CloudProviderVerificationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceInfoRepository resourceInfoRepository;

    @Autowired
    private BalanceHistoryRepository balanceHistoryRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void resourceInfoJsonShouldContainCloudProvider() throws Exception {
        // Insert test data
        ResourceInfo resource = ResourceInfo.builder()
                .resourceId("test-ecs-verify-001")
                .resourceName("验证ECS")
                .resourceType("ECS")
                .region("cn-hangzhou")
                .status("Running")
                .expireTime(LocalDateTime.now().plusDays(365))
                .cloudProvider("ALIYUN")
                .syncTime(LocalDateTime.now())
                .build();
        resourceInfoRepository.save(resource);

        // Verify serialization includes cloudProvider
        String json = objectMapper.writeValueAsString(resource);
        System.out.println("ResourceInfo JSON: " + json);
        assert json.contains("\"cloudProvider\"") : "cloudProvider field missing from ResourceInfo JSON serialization!";
        assert json.contains("\"ALIYUN\"") : "cloudProvider value 'ALIYUN' missing!";

        // Verify API endpoint returns cloudProvider
        mockMvc.perform(get("/api/query/resources?size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].cloudProvider").value("ALIYUN"));
    }

    @Test
    void balanceHistoryJsonShouldContainCloudProvider() throws Exception {
        BalanceHistory balance = BalanceHistory.builder()
                .balance(new BigDecimal("12345.67"))
                .availableAmount(new BigDecimal("10000.00"))
                .currency("CNY")
                .cloudProvider("ALIYUN")
                .syncTime(LocalDateTime.now())
                .build();
        balanceHistoryRepository.save(balance);

        String json = objectMapper.writeValueAsString(balance);
        System.out.println("BalanceHistory JSON: " + json);
        assert json.contains("\"cloudProvider\"") : "cloudProvider field missing from BalanceHistory JSON serialization!";
        assert json.contains("\"ALIYUN\"") : "cloudProvider value 'ALIYUN' missing!";

        mockMvc.perform(get("/api/query/balances?size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].cloudProvider").value("ALIYUN"));
    }

    @Test
    void builderDefaultShouldSetCloudProvider() throws Exception {
        // Verify @Builder.Default works — do NOT set cloudProvider explicitly
        ResourceInfo resource = ResourceInfo.builder()
                .resourceId("test-builder-default")
                .resourceName("Builder默认值测试")
                .resourceType("SWAS")
                .region("cn-guangzhou")
                .status("Running")
                .syncTime(LocalDateTime.now())
                .build();

        assert "ALIYUN".equals(resource.getCloudProvider()) :
                "@Builder.Default failed! Expected 'ALIYUN' but got: " + resource.getCloudProvider();
    }

    @Test
    void cloudProviderFilterShouldWork() throws Exception {
        // Insert ALIYUN resource
        ResourceInfo aliyunRes = ResourceInfo.builder()
                .resourceId("filter-test-aliyun")
                .resourceName("阿里云资源")
                .resourceType("ECS")
                .region("cn-hangzhou")
                .status("Running")
                .cloudProvider("ALIYUN")
                .syncTime(LocalDateTime.now())
                .build();
        resourceInfoRepository.save(aliyunRes);

        // Query with cloudProvider filter
        mockMvc.perform(get("/api/query/resources?cloudProvider=ALIYUN&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].cloudProvider").value("ALIYUN"));

        // Query with wrong cloudProvider should return empty
        mockMvc.perform(get("/api/query/resources?cloudProvider=VOLCENGINE&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    void dashboardExpiringResourcesShouldContainCloudProvider() throws Exception {
        // Insert expiring resource
        ResourceInfo expiring = ResourceInfo.builder()
                .resourceId("dash-expire-001")
                .resourceName("即将到期资源")
                .resourceType("RDS")
                .region("cn-beijing")
                .status("Running")
                .expireTime(LocalDateTime.now().plusDays(15))
                .cloudProvider("ALIYUN")
                .syncTime(LocalDateTime.now())
                .build();
        resourceInfoRepository.save(expiring);

        // Verify dashboard expiring-resources endpoint includes cloudProvider
        mockMvc.perform(get("/api/dashboard/expiring-resources?size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].cloudProvider").value("ALIYUN"));
    }
}
