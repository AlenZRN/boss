package com.aimira.monitor.cloud.volcengine;

/**
 * 火山云 API Action 常量。
 * 每个值必须来自火山引擎官方 API 参考文档，禁止猜测或编造。
 *
 * @see <a href="https://www.volcengine.com/docs">火山引擎 API 文档</a>
 */
public final class VolcengineApiAction {

    private VolcengineApiAction() {}

    /** 余额查询 — 来源：费用中心 API > 资金账户 > QueryBalanceAcct，Version 2022-01-01 */
    public static final String BALANCE = "QueryBalanceAcct";

    /** 账单查询（聚合汇总） — 来源：费用中心 API > 账单管理 > ListBill，Version 2022-01-01 */
    public static final String BILLING = "ListBill";

    /** ECS 实例列表 — 来源：ECS API > DescribeInstances，Version 2020-04-01 */
    public static final String ECS_DESCRIBE_INSTANCES = "DescribeInstances";

    /** RDS 实例列表 — 来源：RDS API > DescribeDBInstances，Version 2022-01-01 */
    public static final String RDS_DESCRIBE_INSTANCES = "DescribeDBInstances";
}
