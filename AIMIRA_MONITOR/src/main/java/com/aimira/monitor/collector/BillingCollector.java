package com.aimira.monitor.collector;

import com.aimira.monitor.entity.BillingHistory;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.bssopenapi.model.v20171214.QueryBillRequest;
import com.aliyuncs.bssopenapi.model.v20171214.QueryBillResponse;
import com.aliyuncs.exceptions.ClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 费用信息采集器
 * 调用 BSSOpenApi QueryBill 接口获取今日/本月费用
 */
@Slf4j
@Component
public class BillingCollector {

    private final AliyunClientFactory clientFactory;

    public BillingCollector(AliyunClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    /**
     * 采集今日费用及本月累计费用
     * @return 费用历史记录，失败时返回 Optional.empty()
     */
    public Optional<BillingHistory> collect() {
        try {
            IAcsClient client = clientFactory.createClient();

            LocalDateTime now = LocalDateTime.now();
            // 今日账单：查询当天
            String todayStr = now.toLocalDate().toString();

            QueryBillRequest request = new QueryBillRequest();
            request.setBillingCycle(now.getYear() + "-" + String.format("%02d", now.getMonthValue()));
            request.setType("PayAsYouGoBill");
            request.setPageNum(1);
            request.setPageSize(100);

            QueryBillResponse response = client.getAcsResponse(request);

            if (response.getCode() != null && "Success".equalsIgnoreCase(response.getCode())) {
                QueryBillResponse.Data data = response.getData();

                BigDecimal dailyCost = BigDecimal.ZERO;
                BigDecimal monthlyCost = BigDecimal.ZERO;

                if (data.getItems() != null && data.getItems().getItem() != null) {
                    for (QueryBillResponse.Data.Items.Item item : data.getItems().getItem()) {
                        BigDecimal amount = parseBigDecimal(item.getPretaxAmount());
                        monthlyCost = monthlyCost.add(amount);

                        // 按日期筛选当日费用
                        if (todayStr.equals(item.getUsageEndDate())) {
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
                        response.getCode(), response.getMessage());
                return Optional.empty();
            }
        } catch (ClientException e) {
            log.error("费用采集 API 调用异常: {}", e.getMessage(), e);
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
