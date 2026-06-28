package com.aimira.monitor.collector;

import com.aimira.monitor.entity.BalanceHistory;
import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 账户余额采集器
 * 使用 CommonRequest 调用 BSSOpenApi QueryAccountBalance
 */
@Slf4j
@Component
public class BalanceCollector {

    private final AliyunClientFactory clientFactory;
    private final ObjectMapper objectMapper;

    public BalanceCollector(AliyunClientFactory clientFactory) {
        this.clientFactory = clientFactory;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 采集当前账户余额
     */
    public Optional<BalanceHistory> collect() {
        try {
            IAcsClient client = clientFactory.createClient();

            CommonRequest request = new CommonRequest();
            request.setSysMethod(MethodType.POST);
            request.setSysDomain("bssopenapi.aliyuncs.com");
            request.setSysVersion("2017-12-14");
            request.setSysAction("QueryAccountBalance");

            CommonResponse response = client.getCommonResponse(request);
            JsonNode root = objectMapper.readTree(response.getData());

            String code = root.path("Code").asText();
            if ("Success".equalsIgnoreCase(code)) {
                JsonNode data = root.path("Data");

                BalanceHistory history = BalanceHistory.builder()
                        .balance(parseBigDecimal(data.path("AvailableCashAmount").asText()))
                        .availableAmount(parseBigDecimal(data.path("AvailableAmount").asText()))
                        .currency(data.path("Currency").asText("CNY"))
                        .syncTime(LocalDateTime.now())
                        .build();

                log.info("余额采集成功: balance={}, available={}, currency={}",
                        history.getBalance(), history.getAvailableAmount(), history.getCurrency());
                return Optional.of(history);
            } else {
                log.warn("余额采集API返回失败: code={}, message={}",
                        code, root.path("Message").asText());
                return Optional.empty();
            }
        } catch (ClientException e) {
            log.error("余额采集 API 调用异常: {}", e.getMessage(), e);
            return Optional.empty();
        } catch (Exception e) {
            log.error("余额采集响应解析异常: {}", e.getMessage(), e);
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
