const moment = require('moment');
const DoctorQuality = require('../models/DoctorQuality');
const DoctorQualityItem = require('../models/DoctorQualityItem');
const DoctorQualityItemDetail = require('../models/DoctorQualityItemDetail');
const calc = require('./qualityCalcService');

async function upsertOne({ deptCode, monthKey, code, name, value, items }) {
  const [y, m] = monthKey.split('-').map(Number);
  const { start, end } = calc.monthRange(monthKey);
  const q = await DoctorQuality.findOneAndUpdate(
    { deptCode, yearFlag: y, flag: `${m}月`, indicatorCode: code },
    {
      $set: {
        deptCode, deptName: '重症医学科',
        yearFlag: y, flag: `${m}月`,
        startTime: start, endTime: end,
        indicator: name, indicatorCode: code,
        indicatorData: value,
        updateTime: new Date(),
      },
      $setOnInsert: { createTime: new Date() },
    },
    { new: true, upsert: true }
  );

  // 写入分子/分母两条 item
  await DoctorQualityItem.deleteMany({ qualityId: q._id });
  for (let i = 0; i < items.length; i++) {
    const it = items[i];
    const itemDoc = await DoctorQualityItem.create({
      qualityId: q._id, order: i,
      itemName: it.name, itemData: it.value,
    });
    if (it.pids?.length) {
      await DoctorQualityItemDetail.insertMany(
        it.pids.map(pid => ({ itemId: itemDoc._id, pid: String(pid) }))
      );
    }
  }
}

// services/qualityWriter.js —— 补齐完整版（接续上一条的 upsertOne / rebuildMonth）

async function rebuildMonth(monthKey, deptCode = '0211', department = '重症医学科') {

  // ===== 5. ICU患者收治率 = 同期ICU收治患者总数 / 同期医院收治患者总数 =====
  // 注意 docx 把分子分母写颠倒了，按医学定义这里按 ICU/医院 计算
  const icuCensus = (await calc.getInIcuPatients(monthKey, department)).length;
  const hosTotal  = await calc.readQualityData(monthKey, 'HosShouZhiHuanZheTotalNum', 'all');
  await upsertOne({ deptCode, monthKey, code: 'ICUHuanZheShouZhiLv', name: 'ICU患者收治率',
    value: hosTotal ? icuCensus / hosTotal : 0,
    items: [
      { name: '同期ICU收治患者总数', value: icuCensus },
      { name: '同期医院收治患者总数', value: hosTotal },
    ],
  });

  // ===== 6. ICU患者收治床日率 = 本科收治患者总床日数 / 同期医院患者收治总床日数 =====
  const icuBedDays = await calcIcuBedDays(monthKey, department);   // 见下方补充函数
  const hosBedDays = await calc.readQualityData(monthKey, 'HosShouZhiHuanZheTotalChuangRiNum', 'all');
  await upsertOne({ deptCode, monthKey, code: 'ICUHuanZheShouZhiChuangRiLv', name: 'ICU患者收治床日率',
    value: hosBedDays ? icuBedDays / hosBedDays : 0,
    items: [
      { name: '本科收治患者总床日数', value: icuBedDays },
      { name: '同期医院患者收治总床日数', value: hosBedDays },
    ],
  });

  // ===== 7/8/9 APACHEII 系列（上一条已写，此处略）=====

  // ===== 10. 感染性休克集束化治疗完成率 =====
  const sb = await calc.calcShockBundle(monthKey);
  await upsertOne({ deptCode, monthKey, code: 'Bundle1Lv', name: '感染性休克集束化治疗完成率',
    value: sb.denom ? sb.num / sb.denom : 0,
    items: [
      { name: '完成集束化治疗患者数', value: sb.num },
      { name: '未下感染性休克护理常规医嘱', value: sb.notDone.length, pids: sb.notDone },
      { name: '同期入ICU诊断为感染性休克患者总数', value: sb.denom },
    ],
  });

  // ===== 11. 抗菌药物治疗前病原学送检率（中台两条数据相除）=====
  const sentNum   = await calc.readQualityData(monthKey, 'KangJunSongJianNum', 'all');
  const antiTotal = await calc.readQualityData(monthKey, 'KangJunTotalNum', 'all');
  await upsertOne({ deptCode, monthKey, code: 'KangJunLv', name: '抗菌药物治疗前病原学送检率',
    value: antiTotal ? sentNum / antiTotal : 0,
    items: [
      { name: '使用抗菌药物前病原学送检病例数', value: sentNum },
      { name: '同期使用抗菌药物治疗病例总数', value: antiTotal },
    ],
  });

  // ===== 12. DVT 预防率 =====
  const dvt = await calc.calcDVT(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'DVTLv', name: '深静脉血栓（DVT）预防率',
    value: dvt.denom ? dvt.num / dvt.denom : 0,
    items: [
      { name: '进行DVT预防的ICU患者数', value: dvt.num },
      { name: '未进行DVT预防的ICU患者数', value: dvt.denom - dvt.num },
      { name: '同期ICU收治患者总数', value: dvt.denom },
    ],
  });

  // ===== 13/14/15 病死率系列（上一条已写，此处略）=====

  // ===== 16/17 非计划拔管、48h 再插管 =====
  const ex = await calc.calcExtubation(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'ICUNoPlanQIGuanBaGuanLv', name: 'ICU非计划气管插管拔管率',
    value: ex.unplannedExtubationRate.denom ? ex.unplannedExtubationRate.num / ex.unplannedExtubationRate.denom : 0,
    items: [
      { name: '非计划拔管例数', value: ex.unplannedExtubationRate.num },
      { name: '气管插管拔管总例数',  value: ex.unplannedExtubationRate.denom },
    ],
  });
  await upsertOne({ deptCode, monthKey, code: 'ICUQIGuanBaGuan48ChaGuanLv', name: 'ICU气管插管拔管后48h内再插管率',
    value: ex.reintubation48hRate.denom ? ex.reintubation48hRate.num / ex.reintubation48hRate.denom : 0,
    items: [
      { name: '48h内再插管例数', value: ex.reintubation48hRate.num },
      { name: '气管插管拔管总例数', value: ex.reintubation48hRate.denom },
    ],
  });

  // ===== 18. 非计划转入 ICU 率（分子读中台，分母用本地 ICU 总数）=====
  const noPlanIn = await calc.readQualityData(monthKey, 'NoPlanInICUNum', 'all');
  await upsertOne({ deptCode, monthKey, code: 'NoPlanInICULv', name: '非计划转入ICU率',
    value: icuCensus ? noPlanIn / icuCensus : 0,
    items: [
      { name: '非计划转入ICU患者数', value: noPlanIn },
      { name: '同期ICU收治患者总数', value: icuCensus },
    ],
  });

  // ===== 19. 转出 ICU 后 48h 重返率 =====
  const ret = await calc.calc48hReturn(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'OutICU48AgainInLv', name: '转出ICU后48h内重返率',
    value: ret.denom ? ret.num / ret.denom : 0,
    items: [
      { name: '转出ICU后48h内重返ICU患者数', value: ret.num },
      { name: '同期转出ICU患者总数', value: ret.denom },
    ],
  });

  // ===== 20. 休克患者超声筛查评估率 =====
  const su = await calc.calcShockUltrasound(monthKey);
  await upsertOne({ deptCode, monthKey, code: 'shock_ultrasound_screen', name: '休克患者超声筛查评估率',
    value: su.denom ? su.num / su.denom : 0,
    items: [
      { name: '完成床旁B超筛查的休克患者数', value: su.num },
      { name: '休克病人数', value: su.denom },
    ],
  });

  // ===== 21. 休克患者血流动力学指标监测率 =====
  const sh = await calc.calcShockHemodynamic(monthKey);
  await upsertOne({ deptCode, monthKey, code: 'shock_blood_flow_detection', name: '休克患者血流动力学指标监测率',
    value: sh.denom ? sh.num / sh.denom : 0,
    items: [
      { name: '完成CVP/PICCO监测的休克患者数', value: sh.num },
      { name: '休克病人数', value: sh.denom },
    ],
  });

  // ===== 22. ARDS 俯卧位通气率 =====
  const ards = await calc.calcARDS(monthKey);
  await upsertOne({ deptCode, monthKey, code: 'ards_constant', name: '急性呼吸窘迫综合征（ARDS）',
    value: ards.denom ? ards.num / ards.denom : 0,
    items: [
      { name: '俯卧位通气患者数', value: ards.num },
      { name: '中重度ARDS患者总数', value: ards.denom },
    ],
  });

  // ===== 23. 48H 肠内营养启动率 =====
  const en = await calc.calcEN48h(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'en_start_in48_constant', name: '48H肠内营养（EN）启动率',
    value: en.denom ? en.num / en.denom : 0,
    items: [
      { name: '入科后48h内启动EN患者数', value: en.num },
      { name: '入科后48h内未启动EN患者数', value: en.denom - en.num },
      { name: '同期入住超过48h的患者人数', value: en.denom },
    ],
  });

  // ===== 24. ICU 镇痛评估率 =====
  const pain = await calc.calcPain(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'icu_analgesia_constant', name: 'ICU镇痛评估率',
    value: pain.denom ? pain.num / pain.denom : 0,
    items: [
      { name: '进行镇痛评估患者人数', value: pain.num },
      { name: '未进行镇痛评估患者人数', value: pain.denom - pain.num },
      { name: '同期ICU收治患者总数', value: pain.denom },
    ],
  });

  // ===== 25. ICU 镇静评估率 =====
  const sed = await calc.calcSedation(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'icu_calm_constant', name: 'ICU镇静评估率',
    value: sed.denom ? sed.num / sed.denom : 0,
    items: [
      { name: '进行镇静评估患者人数', value: sed.num },
      { name: '未进行镇静评估患者人数', value: sed.denom - sed.num },
      { name: '同期ICU收治患者总数', value: sed.denom },
    ],
  });

  // ===== 26. 抢救成功率 =====
  const rs = await calc.calcRescue(monthKey);
  await upsertOne({ deptCode, monthKey, code: 'rescue_success', name: '抢救成功率',
    value: rs.denom ? rs.num / rs.denom : 0,
    items: [
      { name: '抢救成功例数', value: rs.num },
      { name: '抢救死亡人数', value: rs.death },
      { name: '死亡（终末）人数', value: rs.terminal },
      { name: '抢救例数', value: rs.denom },
      { name: '抢救次数', value: rs.rescueCount },        // 详情弹框新增
    ],
  });

  // ===== 27. ICU 急性脑损伤患者意识评估率 =====
  const bi = await calc.calcBrainInjury(monthKey);
  await upsertOne({ deptCode, monthKey, code: 'icu_acute_brain_injury', name: 'ICU急性脑损伤患者意识评估率',
    value: bi.denom ? bi.num / bi.denom : 0,
    items: [
      { name: '完成意识评估的急性脑损伤患者人数', value: bi.num },
      { name: '未完成意识评估的急性脑损伤患者人数', value: bi.denom - bi.num },
      { name: '急性脑损伤患者总数', value: bi.denom },
    ],
  });
}

// ===== 指标 6 用到的床日数辅助函数 =====
// 口径：本月每天 0 点快照在 ICU 的人数之和
async function calcIcuBedDays(monthKey, department) {
  const moment = require('moment');
  const Patient = require('../models/Patient');
  const { start, end } = calc.monthRange(monthKey);
  let total = 0;
  for (let d = moment(start); d.isSameOrBefore(end, 'day'); d.add(1, 'day')) {
    const snap = d.toDate();
    const cnt = await Patient.countDocuments({
      icuAdmissionTime: { $lte: snap },
      $or: [
        { icuDischargeTime: { $gte: snap } },
        { icuDischargeTime: null },
        { icuDischargeTime: { $exists: false } },
      ],
      ...(department ? { $or: [{ deptName: department }, { department }] } : {}),
    });
    total += cnt;
  }
  return total;
}

module.exports = { rebuildMonth, calcIcuBedDays };

