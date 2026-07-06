package com.aimira.monitor.cloud;

/**
 * 阿里云 API Action 常量（记录现有 V1 Collector 使用的 Action）。
 * 来源：阿里云 BSS OpenAPI / ECS / SWAS / RDS 官方 API 参考文档。
 * 现有 collector/ 包内硬编码不修改，仅做文档记录和后续引用。
 */
public final class AliyunApiAction {

    private AliyunApiAction() {}

    /** 余额查询 — 来源：BSS OpenAPI QueryAccountBalance */
    public static final String BALANCE = "QueryAccountBalance";

    /** 账单查询（按量付费） — 来源：BSS OpenAPI QueryBill */
    public static final String BILLING = "QueryBill";

    /** ECS 实例列表 — 来源：ECS API DescribeInstances */
    public static final String ECS_DESCRIBE_INSTANCES = "DescribeInstances";

    /** 轻量应用服务器列表 — 来源：SWAS API ListInstances */
    public static final String SWAS_LIST_INSTANCES = "ListInstances";

    /** RDS 实例列表 — 来源：RDS API DescribeDBInstances */
    public static final String RDS_DESCRIBE_INSTANCES = "DescribeDBInstances";
}
