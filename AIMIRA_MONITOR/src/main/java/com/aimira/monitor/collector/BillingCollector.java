package com.aimira.monitor.collector;

import com.aimira.monitor.entity.BillingHistory;
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 费用信息采集器
 * 使用 CommonRequest 调用 BSSOpenApi QueryBill
 */
@Slf4j
@Component
public class BillingCollector {

    private final AliyunClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    public BillingCollector(AliyunClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 采集今日费用及本月累计费用
     */
    public Optional<BillingHistory> collect() {
        try {
            IAcsClient client = clientFactory.createClient();

            LocalDateTime now = LocalDateTime.now();
            String billingCycle = now.getYear() + "-" + String.format("%02d", now.getMonthValue());
            String todayStr = now.toLocalDate().toString();

            CommonRequest request = new CommonRequest();
            request.setSysProtocol(ProtocolType.HTTPS);
            request.setSysMethod(MethodType.POST);
            request.setSysDomain("business.aliyuncs.com");
            request.setSysVersion("2017-12-14");
            request.setSysAction("QueryBill");
            request.putQueryParameter("BillingCycle", billingCycle);
            request.putQueryParameter("Type", "PayAsYouGoBill");
            request.putQueryParameter("PageNum", "1");
            request.putQueryParameter("PageSize", "100");

            CommonResponse response = client.getCommonResponse(request);
            JsonNode root = objectMapper.readTree(response.getData());

            String code = root.path("Code").asText();
            if ("Success".equalsIgnoreCase(code)) {
                JsonNode data = root.path("Data");
                JsonNode items = data.path("Items").path("Item");

                BigDecimal dailyCost = BigDecimal.ZERO;
                BigDecimal monthlyCost = BigDecimal.ZERO;

                if (items.isArray()) {
                    for (JsonNode item : items) {
                        BigDecimal amount = parseBigDecimal(item.path("PretaxAmount").asText());
                        monthlyCost = monthlyCost.add(amount);

                        if (todayStr.equals(item.path("UsageEndDate").asText())) {
                            dailyCost = dailyCost.add(amount);
                        }
                    }
                }

                BillingHistory history = BillingHistory.builder()
                        .dailyCost(dailyCost)
                        .monthlyCost(monthlyCost)
                        .currency("CNY")
                        .billDate(now)
                        .syncTime(now)
                        .build();

                log.info("费用采集成功: dailyCost={}, monthlyCost={}", dailyCost, monthlyCost);
                return Optional.of(history);
            } else {
                log.warn("费用采集API返回失败: code={}, message={}",
                        code, root.path("Message").asText());
                return Optional.empty();
            }
        } catch (ClientException e) {
            log.error("费用采集 API 调用异常: {}", e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("费用采集响应解析异常: {}", e.getMessage(), e);
            return Optional.empty();
        }
    }

    private BigDecimal parseBigDecimal(String value) {
        if (value == null || value.isBlank()) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            log.warn("金额解析失败: {}", value);
            return BigDecimal.ZERO;
        }
    }
}
