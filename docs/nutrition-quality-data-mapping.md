# 营养质量控制指标 - 数据字段映射

## 1. 数据源

- **数据库**: SmartCare (smartCareMongoTemplate)
- **集合**: 通过配置 `icu-stats.nutrition-quality.collection` 指定
  - 默认候选: `patCnyyRestraintLjrmyy` (从 `_class: PatCnyyRestraintLjrmyy` 推断)
  - 如未配置，可启用 `auto-discover-collection` 自动发现
- **文档类**: `com.digixmed.icu.smartcare.database.entitys.patRestraintLjrmyy.PatCnyyRestraintLjrmyy`

## 2. 已确认字段映射

| 业务含义 | MongoDB字段 | BSON类型 | 说明 |
|---------|------------|---------|------|
| 记录ID | `_id` | ObjectId | |
| 患者ID | `pid` | String | 与 patient._id.toHexString() 关联 |
| 评估时间 | `startTime` | Date (UTC) | 存储为UTC，展示转Asia/Shanghai |
| 营养途径 | `tj` | String | 枚举值待确认 |
| 营养速度(mL/h) | `sd` | String | 需安全转数值 |
| 喂养管深度(cm) | `cd` | String | |
| 通畅性 | `tcx` | String | √ 表示通畅 |
| 胃液颜色 | `wyys` | String | |
| 冲管 | `cg` | String | √ 表示已冲管 |
| 胃残余量(mL) | `wcyl` | String | "/" 表示未测量 |
| 耐受性总分 | `zf` | int/Number | 0-分制 |
| 耐受性分项E | `e` | String | |
| 耐受性分项F | `f` | String | |
| 耐受性分项G | `g` | String | |
| 干预措施 | `cs` | String | 单个干预代码 |
| 干预措施列表 | `csList` | Array/String | 可能是数组或单字符串 |
| 护士签名ID | `nurseSign` | String | |
| 有效状态 | `valid` | String | 有效值: `"valid"` |
| 科室代码 | `deptCode` | String | |
| 病历号 | `mrn` | String | |
| 患者姓名快照 | `name` | String | |
| 床号快照 | `bed` | String | |
| HIS患者ID | `hisPid` | String | |
| 临床诊断 | `clinicalDiagnosis` | String | |
| 营养目标量 | `ymNum` | Number | 含义待完全确认 |

## 3. 干预措施枚举

| 代码 | 含义 | 是否中断 |
|------|------|---------|
| H | 继续肠内营养，维持原速度，对症治疗 | 否 |
| I | 继续肠内营养，减慢速度，2小时后重新评估 | 否 |
| J | 暂停肠内营养，报告医生并记录暂停原因 | **是** |
| K | 更换输入途径，备注中注明 | 待确认 |
| L | 待确认 | 待确认 |

## 4. valid 字段类型兼容

实际值为字符串 `"valid"`。

兼容查询: `{ valid: { $in: ["valid", true, 1] } }` 但默认有效值为 `"valid"`。

## 5. 待确认字段

| 业务含义 | 候选字段 | 状态 |
|---------|---------|------|
| 营养目标量 | `ymNum` | 含义待确认 |
| 完成量 | 未发现 | mapping_required |
| 机械性并发症 | 未发现 | mapping_required |
| 胃肠道并发症 | 未发现 | mapping_required |
| 代谢性并发症 | 未发现 | mapping_required |
| 感染性并发症 | 未发现 | mapping_required |
| 再喂养综合征 | 未发现 | mapping_required |
| 暂停原因 | 未发现 | mapping_required |
| 备注 | 未发现 | mapping_required |

## 6. ObjectId/String 关联处理

- `patient._id` 通常是 ObjectId
- 肠内营养记录 `pid` 是 String
- 关联方式: `patient._id.toHexString() == nutritionRecord.pid`
- 查询时需同时兼容 ObjectId 和 String 类型的 pid

## 7. 建议索引

```javascript
{ valid: 1, startTime: 1, deptCode: 1 }
{ pid: 1, startTime: 1 }
```

> 注意: 未经授权不应在生产库自动创建索引。
