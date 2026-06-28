package com.aimira.monitor.collector;

import com.aimira.monitor.entity.BalanceHistory;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.bssopenapi.model.v20171214.QueryAccountBalanceRequest;
import com.aliyuncs.bssopenapi.model.v20171214.QueryAccountBalanceResponse;
import com.aliyuncs.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 账户余额采集器
 * 调用 BSSOpenApi QueryAccountBalance 接口
 */
@Slf4j
@Component
public class BalanceCollector {

    private final AliyunClientFactory clientFactory;

    public BalanceCollector(AliyunClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * 采集当前账户余额
     * @return 余额历史记录，失败时返回 Optional.empty()
     */
    public Optional<BalanceHistory> collect() {
        try {
            IAcsClient client = clientFactory.createClient();
            QueryAccountBalanceRequest request = new QueryAccountBalanceRequest();
            QueryAccountBalanceResponse response = client.getAcsResponse(request);

            if (response.getCode() != null && "Success".equalsIgnoreCase(response.getCode())) {
                QueryAccountBalanceResponse.Data data = response.getData();

                BalanceHistory history = BalanceHistory.builder()
                        .balance(parseBigDecimal(data.getAvailableCashAmount()))
                        .availableAmount(parseBigDecimal(data.getAvailableAmount()))
                        .currency(data.getCurrency() != null ? data.getCurrency() : "CNY")
                        .syncTime(LocalDateTime.now())
                        .build();

                log.info("余额采集成功: balance={}, available={}, currency={}",
                        history.getBalance(), history.getAvailableAmount(), history.getCurrency());
                return Optional.of(history);
            } else {
                log.warn("余额采集API返回失败: code={}, message={}",
                        response.getCode(), response.getMessage());
                return Optional.empty();
            }
        } catch (ClientException e) {
            log.error("余额采集 API 调用异常: {}", e.getMessage(), e);
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
