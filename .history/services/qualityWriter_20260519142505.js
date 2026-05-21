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

async function rebuildMonth(monthKey, deptCode = '0211', department = '重症医学科') {
  // 7/8/9
  const a = await calc.calcApacheRates(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'ApacheUp15Lv', name: 'APACHEII≥15患者收治率',
    value: a.apacheGte15Rate.denom ? a.apacheGte15Rate.num / a.apacheGte15Rate.denom : 0,
    items: [
      { name: 'APACHEII≥15患者数', value: a.apacheGte15Rate.num, pids: a.apacheGte15Rate.detail },
      { name: '同期ICU收治患者总数', value: a.apacheGte15Rate.denom },
    ],
  });
  await upsertOne({ deptCode, monthKey, code: 'ApacheDown15Lv', name: 'APACHEII<15患者收治率',
    value: a.apacheLt15Rate.denom ? a.apacheLt15Rate.num / a.apacheLt15Rate.denom : 0,
    items: [
      { name: 'APACHEII<15患者数', value: a.apacheLt15Rate.num, pids: a.apacheLt15Rate.detail },
      { name: '同期ICU收治患者总数', value: a.apacheLt15Rate.denom },
    ],
  });
  await upsertOne({ deptCode, monthKey, code: 'ApacheIIZongLv', name: 'APACHEII评分率',
    value: a.apacheScoreRate.denom ? a.apacheScoreRate.num / a.apacheScoreRate.denom : 0,
    items: [
      { name: '已评APACHEII人数', value: a.apacheScoreRate.num, pids: a.apacheScoreRate.detail.scored },
      { name: '本月未评APACHEII人数', value: a.apacheScoreRate.notScored, pids: a.apacheScoreRate.detail.notScored },
      { name: '同期ICU收治患者总数', value: a.apacheScoreRate.denom },
    ],
  });

  // 13/14/15
  const m = await calc.calcMortality(monthKey, department);
  await upsertOne({ deptCode, monthKey, code: 'YuJiDeadLv', name: 'ICU患者预计病死率',
    value: m.predictedMortalityRate.value,
    items: [
      { name: 'ICU收治患者预计病死率总和', value: m.predictedMortalityRate.sum },
      { name: '同期ICU收治患者总数', value: m.predictedMortalityRate.denom },
    ],
  });
  await upsertOne({ deptCode, monthKey, code: 'deathApacheLte15_constant', name: 'APACHEII评分<15的死亡率',
    value: m.apacheLt15DeathRate.denom ? m.apacheLt15DeathRate.num / m.apacheLt15DeathRate.denom : 0,
    items: [
      { name: 'APACHEII<15死亡人数', value: m.apacheLt15DeathRate.num },
      { name: 'APACHEII<15患者数', value: m.apacheLt15DeathRate.denom },
    ],
  });
  await upsertOne({ deptCode, monthKey, code: 'ICUBiaoHuaDeadLv', name: 'ICU患者标化病死指数',
    value: m.standardizedMortalityIndex.value,
    items: [
      { name: 'ICU患者实际病死率', value: m.standardizedMortalityIndex.actualRate },
      { name: 'ICU患者预计病死率', value: m.standardizedMortalityIndex.predictedRate },
    ],
  });

  // 10/12/16/17/19/20/21/22/23/24/25/26/27 同理...
  // 此处省略，逐条对照上面的 calc.* 调用，模式完全一致：
  //   1) calc.xxx 拿到 { num, denom, ... }
  //   2) upsertOne 写入 indicatorData=num/denom，items 写分子分母明细
}

module.exports = { rebuildMonth };
