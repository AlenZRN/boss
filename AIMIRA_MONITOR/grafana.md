# Grafana 看板 — 多云余额监控

## 使用方式

1. Grafana → **Dashboards** → **New** → **Import**
2. 将下方 JSON 粘贴到 **Import via dashboard JSON model** 中
3. 选择你的 Prometheus 数据源
4. 点击 **Import**

## 看板布局

```
┌────────────────────┬──────────────────────────────────────────────┐
│                    │                                              │
│   当前余额 (Stat)   │        余额趋势 (TimeSeries)                  │
│   aimira_cloud_    │        aimira_cloud_balance                   │
│   balance          │                                              │
│                    │                                              │
├────────────────────┼──────────────────────────────────────────────┤
│                    │                                              │
│  可用金额 (Stat)    │      可用金额趋势 (TimeSeries)                 │
│  aimira_cloud_     │      aimira_cloud_available_amount            │
│  available_amount  │                                              │
│                    │                                              │
└────────────────────┴──────────────────────────────────────────────┘
```

---

## Dashboard JSON

```json
{
  "annotations": {
    "list": [
      {
        "builtIn": 1,
        "datasource": { "type": "grafana", "uid": "-- Grafana --" },
        "enable": true,
        "hide": true,
        "iconColor": "rgba(0, 211, 255, 1)",
        "name": "Annotations & Alerts",
        "type": "dashboard"
      }
    ]
  },
  "editable": true,
  "fiscalYearStartMonth": 0,
  "graphTooltip": 0,
  "id": null,
  "links": [],
  "panels": [
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 0 },
      "id": 100,
      "panels": [],
      "title": "账户余额",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "${datasource}" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "red", "value": null },
              { "color": "yellow", "value": 0 },
              { "color": "green", "value": 10000 }
            ]
          },
          "unit": "cny"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 1 },
      "id": 1,
      "options": {
        "colorMode": "background",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "percentChangeColorMode": "standard",
        "reduceOptions": {
          "calcs": ["lastNotNull"],
          "fields": "",
          "values": false
        },
        "showPercentChange": false,
        "textMode": "auto",
        "wideLayout": true
      },
      "pluginVersion": "10.0.0",
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "${datasource}" },
          "editorMode": "code",
          "expr": "aimira_cloud_balance{cloud_provider=~\"$cloud_provider\"}",
          "instant": true,
          "legendFormat": "{{cloud_provider}}",
          "range": false,
          "refId": "A"
        }
      ],
      "title": "当前余额",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "${datasource}" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": {
            "axisBorderShow": false,
            "axisCenteredZero": false,
            "axisColorMode": "text",
            "axisLabel": "",
            "axisPlacement": "auto",
            "barAlignment": 0,
            "drawStyle": "line",
            "fillOpacity": 10,
            "gradientMode": "none",
            "hideFrom": { "legend": false, "tooltip": false, "viz": false },
            "insertNulls": false,
            "lineInterpolation": "stepAfter",
            "lineWidth": 2,
            "pointSize": 5,
            "scaleDistribution": { "type": "linear" },
            "showPoints": "auto",
            "spanNulls": false,
            "stacking": { "group": "A", "mode": "none" },
            "thresholdsStyle": { "mode": "off" }
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "red", "value": 80 }
            ]
          },
          "unit": "cny"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 16, "x": 8, "y": 1 },
      "id": 2,
      "options": {
        "legend": {
          "calcs": ["lastNotNull", "mean", "max", "min"],
          "displayMode": "table",
          "placement": "bottom",
          "showLegend": true
        },
        "tooltip": { "mode": "multi", "sort": "desc" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "${datasource}" },
          "editorMode": "code",
          "expr": "aimira_cloud_balance{cloud_provider=~\"$cloud_provider\"}",
          "legendFormat": "{{cloud_provider}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "余额趋势",
      "type": "timeseries"
    },
    {
      "collapsed": false,
      "gridPos": { "h": 1, "w": 24, "x": 0, "y": 9 },
      "id": 101,
      "panels": [],
      "title": "可用金额",
      "type": "row"
    },
    {
      "datasource": { "type": "prometheus", "uid": "${datasource}" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "thresholds" },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "red", "value": null },
              { "color": "yellow", "value": 0 },
              { "color": "green", "value": 10000 }
            ]
          },
          "unit": "cny"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 8, "x": 0, "y": 10 },
      "id": 3,
      "options": {
        "colorMode": "background",
        "graphMode": "area",
        "justifyMode": "auto",
        "orientation": "auto",
        "percentChangeColorMode": "standard",
        "reduceOptions": {
          "calcs": ["lastNotNull"],
          "fields": "",
          "values": false
        },
        "showPercentChange": false,
        "textMode": "auto",
        "wideLayout": true
      },
      "pluginVersion": "10.0.0",
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "${datasource}" },
          "editorMode": "code",
          "expr": "aimira_cloud_available_amount{cloud_provider=~\"$cloud_provider\"}",
          "instant": true,
          "legendFormat": "{{cloud_provider}}",
          "range": false,
          "refId": "A"
        }
      ],
      "title": "当前可用金额",
      "type": "stat"
    },
    {
      "datasource": { "type": "prometheus", "uid": "${datasource}" },
      "fieldConfig": {
        "defaults": {
          "color": { "mode": "palette-classic" },
          "custom": {
            "axisBorderShow": false,
            "axisCenteredZero": false,
            "axisColorMode": "text",
            "axisLabel": "",
            "axisPlacement": "auto",
            "barAlignment": 0,
            "drawStyle": "line",
            "fillOpacity": 10,
            "gradientMode": "none",
            "hideFrom": { "legend": false, "tooltip": false, "viz": false },
            "insertNulls": false,
            "lineInterpolation": "stepAfter",
            "lineWidth": 2,
            "pointSize": 5,
            "scaleDistribution": { "type": "linear" },
            "showPoints": "auto",
            "spanNulls": false,
            "stacking": { "group": "A", "mode": "none" },
            "thresholdsStyle": { "mode": "off" }
          },
          "mappings": [],
          "thresholds": {
            "mode": "absolute",
            "steps": [
              { "color": "green", "value": null },
              { "color": "red", "value": 80 }
            ]
          },
          "unit": "cny"
        },
        "overrides": []
      },
      "gridPos": { "h": 8, "w": 16, "x": 8, "y": 10 },
      "id": 4,
      "options": {
        "legend": {
          "calcs": ["lastNotNull", "mean", "max", "min"],
          "displayMode": "table",
          "placement": "bottom",
          "showLegend": true
        },
        "tooltip": { "mode": "multi", "sort": "desc" }
      },
      "targets": [
        {
          "datasource": { "type": "prometheus", "uid": "${datasource}" },
          "editorMode": "code",
          "expr": "aimira_cloud_available_amount{cloud_provider=~\"$cloud_provider\"}",
          "legendFormat": "{{cloud_provider}}",
          "range": true,
          "refId": "A"
        }
      ],
      "title": "可用金额趋势",
      "type": "timeseries"
    }
  ],
  "refresh": "5m",
  "schemaVersion": 39,
  "tags": ["aimira", "cloud", "balance"],
  "templating": {
    "list": [
      {
        "current": {
          "selected": false,
          "text": "Prometheus",
          "value": "Prometheus"
        },
        "hide": 0,
        "includeAll": false,
        "label": "数据源",
        "multi": false,
        "name": "datasource",
        "options": [],
        "query": "prometheus",
        "queryValue": "",
        "refresh": 1,
        "regex": "",
        "skipUrlSync": false,
        "type": "datasource"
      },
      {
        "current": { "selected": true, "text": ["ALIYUN", "VOLCENGINE"], "value": ["ALIYUN", "VOLCENGINE"] },
        "datasource": { "type": "prometheus", "uid": "${datasource}" },
        "definition": "label_values(aimira_cloud_balance, cloud_provider)",
        "hide": 0,
        "includeAll": true,
        "label": "云厂商",
        "multi": true,
        "name": "cloud_provider",
        "options": [],
        "query": { "query": "label_values(aimira_cloud_balance, cloud_provider)", "refId": "StandardVariableQuery" },
        "refresh": 1,
        "regex": "",
        "skipUrlSync": false,
        "sort": 1,
        "type": "query"
      }
    ]
  },
  "time": {
    "from": "now-7d",
    "to": "now"
  },
  "timepicker": {},
  "timezone": "browser",
  "title": "☁️ 多云账户余额",
  "uid": "aimira-cloud-balance",
  "version": 1,
  "weekStart": ""
}
```
