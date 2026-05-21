// services/qualityCalcService.js
const moment = require('moment');
const Patient = require('../models/Patient');
const Order = require('../models/Order');           // VI_ICU_ZYYZ
const Score = require('../models/Score');
const TubeExe = require('../models/TubeExe');
const QualityData = require('../models/QualityData'); // VI_ICU_QUALITY

const EAST8 = 8 * 60;

function monthRange(monthKey) {
  const start = moment.parseZone(`${monthKey}-01T00:00:00+08:00`).startOf('month').toDate();
  const end   = moment.parseZone(`${monthKey}-01T00:00:00+08:00`).endOf('month').toDate();
  return { start, end };
}

// ---------- 通用：本月在科患者（同期ICU收治患者总数）----------
// 口径：本月内任何时间点在 ICU 的患者
async function getInIcuPatients(monthKey, department) {
  const { start, end } = monthRange(monthKey);
  return Patient.find({
    icuAdmissionTime: { $lte: end },
    $or: [
      { icuDischargeTime: { $gte: start } },
      { icuDischargeTime: null },
      { icuDischargeTime: { $exists: false } },
    ],
    ...(department ? { $or: [{ deptName: department }, { department }] } : {}),
  }).lean();
}

// ---------- 通用：本月医嘱（不含撤销）----------
function orderQuery(monthKey, nameKeywords, extra = {}) {
  const { start, end } = monthRange(monthKey);
  const ors = (Array.isArray(nameKeywords) ? nameKeywords : [nameKeywords])
    .map(k => ({ orderName: new RegExp(escapeReg(k)) }));   // 真实字段名以中台为准
  return {
    orderTime: { $gte: start, $lte: end },
    $or: ors,
    orderName: { $not: /撤销/ },
    ...extra,
  };
}
function escapeReg(s) { return String(s).replace(/[.*+?^${}()|[\]\\]/g, '\\$&'); }

function asDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function floorDateToMinute(value) {
  const date = asDate(value);
  if (!date) return null;
  const normalized = new Date(date);
  normalized.setSeconds(0, 0);
  return normalized;
}

function sortPatientsByAdmission(patients) {
  return [...patients].sort((a, b) => {
    const aTime = asDate(a.icuAdmissionTime)?.getTime() ?? 0;
    const bTime = asDate(b.icuAdmissionTime)?.getTime() ?? 0;
    if (aTime !== bTime) return aTime - bTime;
    return String(a._id).localeCompare(String(b._id));
  });
}

function countMatchedOrderPatients(patients, orders, fallbackEndDate) {
  const patientsByMrn = new Map();
  const orderTimesByMrn = new Map();

  patients.forEach(patient => {
    const mrn = String(patient.mrn || '').trim();
    if (!mrn) return;
    if (!patientsByMrn.has(mrn)) patientsByMrn.set(mrn, []);
    patientsByMrn.get(mrn).push(patient);
  });

  orders.forEach(order => {
    const mrn = String(order.mrn || '').trim();
    const orderTime = asDate(order.orderTime);
    if (!mrn || !orderTime) return;
    if (!orderTimesByMrn.has(mrn)) orderTimesByMrn.set(mrn, []);
    orderTimesByMrn.get(mrn).push(orderTime);
  });

  orderTimesByMrn.forEach(times => times.sort((a, b) => a - b));

  let hitCount = 0;
  patientsByMrn.forEach((mrnPatients, mrn) => {
    const sortedPatients = sortPatientsByAdmission(mrnPatients);
    const orderTimes = orderTimesByMrn.get(mrn) || [];

    if (sortedPatients.length === 1) {
      if (orderTimes.length) hitCount += 1;
      return;
    }

    const usedOrders = new Set();
    sortedPatients.forEach(patient => {
      const start = floorDateToMinute(patient.icuAdmissionTime);
      const end = asDate(patient.icuDischargeTime) || fallbackEndDate;
      const matchedOrderIndex = orderTimes.findIndex((time, index) => (
        !usedOrders.has(index)
        && start
        && time >= start
        && time <= end
      ));
      if (matchedOrderIndex >= 0) {
        usedOrders.add(matchedOrderIndex);
        hitCount += 1;
      }
    });
  });

  return hitCount;
}

// ========== 7/8/9：APACHEII 系列 ==========
// 取每个患者本月最新一次 valid=true 且 scoreType=apacheII 的评分
function pickApacheScoreForStats(patientScores, start, end) {
  if (!patientScores.length) return null;
  if (patientScores.length === 1) return patientScores[0];

  const inMonthScore = patientScores.find(item => {
    const scoreTime = item.time ? new Date(item.time) : null;
    return scoreTime && !Number.isNaN(scoreTime.getTime()) && scoreTime >= start && scoreTime <= end;
  });
  if (inMonthScore) return inMonthScore;

  return patientScores.find(item => {
    const scoreTime = item.time ? new Date(item.time) : null;
    return scoreTime && !Number.isNaN(scoreTime.getTime()) && scoreTime < start;
  }) || null;
}

async function loadApacheScoreMaps(patients, monthKey) {
  const { start, end } = monthRange(monthKey);
  const pids = patients.map(p => String(p._id));
  if (!pids.length) return { anyScoreByPid: new Map(), selectedScoreByPid: new Map() };
  const scores = await Score.find({
    pid: { $in: pids },
    valid: true,
    scoreType: 'apacheII',
  }).sort({ time: -1 }).lean();

  const grouped = new Map();
  for (const s of scores) {
    const pid = String(s.pid || '');
    if (!pid) continue;
    if (!grouped.has(pid)) grouped.set(pid, []);
    grouped.get(pid).push(s);
  }

  const anyScoreByPid = new Map();
  const selectedScoreByPid = new Map();
  for (const [pid, patientScores] of grouped.entries()) {
    anyScoreByPid.set(pid, patientScores[0]);
    const matched = pickApacheScoreForStats(patientScores, start, end);
    if (matched) selectedScoreByPid.set(pid, matched);
  }
  return { anyScoreByPid, selectedScoreByPid };
}

async function calcApacheRates(monthKey, department) {
  const patients = await getInIcuPatients(monthKey, department);
  const { anyScoreByPid, selectedScoreByPid } = await loadApacheScoreMaps(patients, monthKey);

  const denom = patients.length;                            // 同期ICU收治患者总数
  let gte15 = 0, lt15 = 0, scored = 0, notScored = 0;
  const detail = { gte15: [], lt15: [], scored: [], notScored: [] };

  for (const p of patients) {
    const pid = String(p._id);
    const hasAnyScore = anyScoreByPid.has(pid);
    const s = selectedScoreByPid.get(pid);
    if (!hasAnyScore) { notScored++; detail.notScored.push(p._id); continue; }
    scored++; detail.scored.push(p._id);
    if (!s) continue;
    if (Number(s.total) >= 15) { gte15++; detail.gte15.push(p._id); }
    else                       { lt15++;  detail.lt15.push(p._id);  }
  }

  return {
    apacheGte15Rate: { num: gte15, denom, detail: detail.gte15 },
    apacheLt15Rate:  { num: lt15,  denom, detail: detail.lt15  },
    apacheScoreRate: { num: scored, denom, notScored, detail },
  };
}

// ========== 13/14/15：预计病死率、APACHEII<15 死亡率、标化病死指数 ==========
async function calcMortality(monthKey, department) {
  const patients = await getInIcuPatients(monthKey, department);
  const { selectedScoreByPid: scoreMap } = await loadApacheScoreMaps(patients, monthKey);

  let predictedSum = 0;
  let predictedHit = 0;          // 有评分的人数（分母平均时用）
  let actualDeath = 0;
  let lt15Total = 0, lt15Death = 0;

  for (const p of patients) {
    if (String(p.dischargedType || '').includes('死亡')) actualDeath++;
    const s = scoreMap.get(String(p._id));
    if (s?.apacheII?.calDead?.score != null) {
      predictedSum += Number(s.apacheII.calDead.score);
      predictedHit++;
    }
    if (s && Number(s.total) < 15) {
      lt15Total++;
      if (String(p.dischargedType || '').includes('死亡')) lt15Death++;
    }
  }

  const denom = patients.length;
  const predictedRate = denom ? predictedSum / denom : 0;       // 13
  const actualRate    = denom ? actualDeath  / denom : 0;
  const smr           = predictedRate ? actualRate / predictedRate : 0;  // 15 标化病死指数
  const lt15DeathRate = lt15Total ? lt15Death / lt15Total : 0;           // 14

  return {
    predictedMortalityRate: { value: round(predictedRate, 3), sum: predictedSum, denom },
    apacheLt15DeathRate:    { num: lt15Death, denom: lt15Total },
    standardizedMortalityIndex: { value: round(smr, 3), actualRate, predictedRate },
  };
}
function round(v, n) { return Number(Number(v).toFixed(n)); }

// ========== 10：感染性休克集束化治疗完成率 ==========
async function calcShockBundle(monthKey) {
  const baseOrders = await Order.find(
    orderQuery(monthKey, ['感染性休克护理常规'])
  ).select('mrn').lean();
  const mrnSet = new Set(baseOrders.map(o => o.mrn));
  if (!mrnSet.size) return { num: 0, denom: 0, notDone: [] };

  const bundleOrders = await Order.find({
    ...orderQuery(monthKey, ['感染性休克患者集束化治疗']),
    mrn: { $in: [...mrnSet] },
  }).select('mrn').lean();
  const doneSet = new Set(bundleOrders.map(o => o.mrn));

  return {
    num: doneSet.size,
    denom: mrnSet.size,
    notDone: [...mrnSet].filter(mrn => !doneSet.has(mrn)),
  };
}

// ========== 12：DVT 预防率 ==========
const DVT_DEVICE = ['肢体气压治疗', '梯度压力弹力袜', '腔静脉滤器'];
const DVT_HEPARIN = ['低分子肝素钠', '低分子肝素钙', '那屈肝素', '依诺肝素', '达肝素钠注射液'];
const DVT_RIVA = ['利伐沙班'];
async function calcDVT(monthKey, department) {
  const patients = await getInIcuPatients(monthKey, department);
  const mrnList = patients.map(p => p.mrn).filter(Boolean);

  // 一次查询 + 内存判定，避免 N+1
  const orders = await Order.find({
    mrn: { $in: mrnList },
    orderTime: { $lte: monthRange(monthKey).end },
    orderName: { $not: /撤销/ },
    $or: [
      ...DVT_DEVICE.map(k => ({ orderName: new RegExp(escapeReg(k)) })),
      ...DVT_HEPARIN.map(k => ({ orderName: new RegExp(escapeReg(k)), exeMethod: '皮下注射' })),
      ...DVT_RIVA.map(k => ({ orderName: new RegExp(escapeReg(k)), exeMethod: { $in: ['口服', '胃管置管术注药'] } })),
    ],
  }).select('mrn').lean();
  const hitSet = new Set(orders.map(o => o.mrn));

  const num = patients.filter(p => hitSet.has(p.mrn)).length;
  return { num, denom: patients.length };
}

// ========== 16/17：非计划拔管率、48h 内再插管率 ==========
async function calcExtubation(monthKey, department) {
  const { start, end } = monthRange(monthKey);
  // 分母：本月有 endTime 的气插管，valid!=false，replace!=true
  const tubes = await TubeExe.find({
    type: '气插管',
    valid: { $ne: false },
    replace: { $ne: true },
    endTime: { $gte: start, $lte: end },     // endTime 为 null 不算
  }).lean();

  if (!tubes.length) return {
    unplannedExtubationRate: { num: 0, denom: 0 },
    reintubation48hRate:     { num: 0, denom: 0 },
  };

  // 非计划拔管
  const unplanned = tubes.filter(t => t.unPlannedEndTube === true).length;

  // 48h 再插管：按 pid 拉所有气插管历史，同 pid 内寻找下一根 startTime - endTime ≤ 48h
  const pids = [...new Set(tubes.map(t => String(t.pid)))];
  const allHistory = await TubeExe.find({
    pid: { $in: pids },
    type: '气插管',
    valid: { $ne: false },
    replace: { $ne: true },
  }).sort({ startTime: 1 }).lean();

  const byPid = new Map();
  allHistory.forEach(t => {
    const arr = byPid.get(String(t.pid)) || [];
    arr.push(t); byPid.set(String(t.pid), arr);
  });

  let reintubated = 0;
  for (const t of tubes) {
    if (!t.endTime) continue;
    const arr = byPid.get(String(t.pid)) || [];
    const next = arr.find(x => x.startTime && new Date(x.startTime) > new Date(t.endTime));
    if (next && (new Date(next.startTime) - new Date(t.endTime)) <= 48 * 3600 * 1000) {
      reintubated++;
    }
  }

  return {
    unplannedExtubationRate: { num: unplanned, denom: tubes.length },
    reintubation48hRate:     { num: reintubated, denom: tubes.length },
  };
}

// ========== 19：转出 ICU 后 48h 内重返率 ==========
async function calc48hReturn(monthKey, department) {
  const { start, end } = monthRange(monthKey);
  // 分母：本月 icuDischargeTime 在内，dischargedType 含"转出"
  const out = await Patient.find({
    icuDischargeTime: { $gte: start, $lte: end },
    dischargedType: /转出/,
  }).lean();

  if (!out.length) return { num: 0, denom: 0 };

  // 拉每个 mrn 的全部 patient 记录（多次入院）
  const mrns = [...new Set(out.map(p => p.mrn).filter(Boolean))];
  const all = await Patient.find({ mrn: { $in: mrns } })
    .sort({ icuAdmissionTime: 1 }).select('mrn icuAdmissionTime icuDischargeTime').lean();
  const byMrn = new Map();
  all.forEach(p => {
    const arr = byMrn.get(p.mrn) || [];
    arr.push(p); byMrn.set(p.mrn, arr);
  });

  let hit = 0;
  for (const p of out) {
    if (!p.icuDischargeTime || !p.mrn) continue;
    const arr = byMrn.get(p.mrn) || [];
    const next = arr.find(r => r.icuAdmissionTime && new Date(r.icuAdmissionTime) > new Date(p.icuDischargeTime));
    if (next && (new Date(next.icuAdmissionTime) - new Date(p.icuDischargeTime)) <= 48 * 3600 * 1000) {
      hit++;
    }
  }
  return { num: hit, denom: out.length };
}

// ========== 20/21/22：休克超声、休克血流动力学、ARDS 俯卧位 ==========
// 模式：分母 = 某护理常规医嘱命中的 mrn 集合；分子 = 在此 mrn 集合内匹配另一个医嘱关键字
async function calcOrderBased(monthKey, denomKeyword, numKeyword) {
  const denomOrders = await Order.find(orderQuery(monthKey, [denomKeyword])).select('mrn').lean();
  const denomMrns = new Set(denomOrders.map(o => o.mrn));
  if (!denomMrns.size) return { num: 0, denom: 0 };
  const numOrders = await Order.find({
    ...orderQuery(monthKey, [numKeyword]),
    mrn: { $in: [...denomMrns] },
  }).select('mrn').lean();
  return { num: new Set(numOrders.map(o => o.mrn)).size, denom: denomMrns.size };
}
const calcShockUltrasound  = (m) => calcOrderBased(m, '休克护理常规', '重症超声筛查评估');
const calcShockHemodynamic = (m) => calcOrderBased(m, '休克护理常规', 'CVP');
const calcARDS             = (m) => calcOrderBased(m, '中重度ARDS护理常规', '俯卧位通气');

// ========== 23：48h EN 启动率 ==========
async function calcEN48h(monthKey, department) {
  const patients = (await getInIcuPatients(monthKey, department))
    .filter(p => {
      // 在科超过 48h
      const inT = p.icuAdmissionTime ? new Date(p.icuAdmissionTime) : null;
      const outT = p.icuDischargeTime ? new Date(p.icuDischargeTime) : new Date();
      return inT && (outT - inT) >= 48 * 3600 * 1000;
    });
  if (!patients.length) return { num: 0, denom: 0 };

  const mrns = patients.map(p => p.mrn).filter(Boolean);
  const orders = await Order.find({
    mrn: { $in: mrns },
    orderName: { $regex: /流质饮食/, $not: /撤销/ },
  }).select('mrn orderTime').lean();

  // 取每个 mrn 最早的"流质饮食"医嘱
  const earliest = new Map();
  orders.forEach(o => {
    const cur = earliest.get(o.mrn);
    if (!cur || new Date(o.orderTime) < new Date(cur.orderTime)) earliest.set(o.mrn, o);
  });

  let inTime = 0;
  for (const p of patients) {
    const o = earliest.get(p.mrn);
    if (!o || !p.icuAdmissionTime) continue;
    if (new Date(o.orderTime) - new Date(p.icuAdmissionTime) <= 48 * 3600 * 1000) inTime++;
  }
  return { num: inTime, denom: patients.length };
}

// ========== 24/25：镇痛/镇静 评估率 ==========
async function calcOrderHitOnIcu(monthKey, department, keyword) {
  const patients = await getInIcuPatients(monthKey, department);
  if (!patients.length) return { num: 0, denom: 0 };
  const { end } = monthRange(monthKey);
  const mrns = patients.map(p => p.mrn).filter(Boolean);
  const orders = await Order.find({
    ...orderQuery(monthKey, [keyword]),
    mrn: { $in: mrns },
  }).select('mrn orderTime').lean();
  return { num: countMatchedOrderPatients(patients, orders, end), denom: patients.length };
}
const calcPain     = (m, d) => calcOrderHitOnIcu(m, d, '镇痛评估');
const calcSedation = (m, d) => calcOrderHitOnIcu(m, d, '镇静评估');

// ========== 26：抢救成功率 ==========
async function calcRescue(monthKey) {
  const rescueOrders = await Order.find(orderQuery(monthKey, ['抢救'])).select('mrn').lean();
  const mrns = [...new Set(rescueOrders.map(o => o.mrn))];
  if (!mrns.length) return { num: 0, denom: 0, death: 0, terminal: 0, rescueCount: 0 };

  const patients = await Patient.find({ mrn: { $in: mrns } })
    .select('mrn dischargedType').lean();
  const byMrn = new Map(patients.map(p => [p.mrn, p]));

  let denom = 0, success = 0, death = 0, terminal = 0;
  for (const mrn of mrns) {
    const p = byMrn.get(mrn);
    if (!p) continue;
    if (String(p.dischargedType || '').includes('死亡（终末）')) { terminal++; continue; } // 终末不计入分母
    denom++;
    if (String(p.dischargedType || '').includes('死亡')) death++;
    else success++;
  }
  return {
    num: success, denom, death, terminal,
    rescueCount: rescueOrders.length,    // 抢救总次数
  };
}

// ========== 27：急性脑损伤患者意识评估率 ==========
const calcBrainInjury = (m) => calcOrderBased(m, '急性脑损伤护理常规', '格拉斯哥昏迷评分');

// ========== 5/6/11/18：直接读 VI_ICU_QUALITY ==========
async function readQualityData(monthKey, indicatorCode, deptCode = 'all') {
  const [y, m] = monthKey.split('-').map(Number);
  const doc = await QualityData.findOne({
    deptCode, year: y, month: m, indicatorCode,
  }).lean();
  return doc?.indicatorData ?? 0;
}

function orderQueryToMonthEnd(monthKey, nameKeywords, extra = {}) {
  const { end } = monthRange(monthKey);
  const ors = (Array.isArray(nameKeywords) ? nameKeywords : [nameKeywords]).map(k => ({ orderName: new RegExp(escapeReg(k)) }));
  return {
    orderTime: { $lte: end },
    $or: ors,
    orderName: { $not: /撤销/ },
    ...extra,
  };
}

function matchPatientsByOrders(patients, orders, fallbackEndDate) {
  const patientsByMrn = new Map();
  const orderTimesByMrn = new Map();

  patients.forEach(patient => {
    const mrn = String(patient.mrn || '').trim();
    if (!mrn) return;
    if (!patientsByMrn.has(mrn)) patientsByMrn.set(mrn, []);
    patientsByMrn.get(mrn).push(patient);
  });

  orders.forEach(order => {
    const mrn = String(order.mrn || '').trim();
    const orderTime = asDate(order.orderTime);
    if (!mrn || !orderTime) return;
    if (!orderTimesByMrn.has(mrn)) orderTimesByMrn.set(mrn, []);
    orderTimesByMrn.get(mrn).push(orderTime);
  });

  orderTimesByMrn.forEach(times => times.sort((a, b) => a - b));

  const denominator = [];
  const done = [];
  const notDone = [];

  patientsByMrn.forEach((mrnPatients, mrn) => {
    const sortedPatients = sortPatientsByAdmission(mrnPatients);
    const orderTimes = orderTimesByMrn.get(mrn) || [];
    const usedOrders = new Set();

    sortedPatients.forEach(patient => {
      denominator.push(patient);
      const start = floorDateToMinute(patient.icuAdmissionTime);
      const end = asDate(patient.icuDischargeTime) || fallbackEndDate;
      const matchedOrderIndex = orderTimes.findIndex((time, index) => (
        !usedOrders.has(index)
        && start
        && time >= start
        && time <= end
      ));
      if (matchedOrderIndex >= 0) {
        usedOrders.add(matchedOrderIndex);
        done.push(patient);
      } else {
        notDone.push(patient);
      }
    });
  });

  return { denominator, done, notDone };
}

async function getMatchedPatientsByOrderFilter(monthKey, patients, filter) {
  const mrns = [...new Set(patients.map(patient => patient.mrn).filter(Boolean))];
  if (!mrns.length) return { denominator: [], done: [], notDone: [] };
  const orders = await Order.find({
    ...filter,
    mrn: { $in: mrns },
  }).select('mrn orderTime').lean();
  return matchPatientsByOrders(patients, orders, monthRange(monthKey).end);
}

async function calcShockBundleCarryover(monthKey, department) {
  const patients = await getInIcuPatients(monthKey, department);
  const denominatorMatched = await getMatchedPatientsByOrderFilter(monthKey, patients, orderQueryToMonthEnd(monthKey, ['感染性休克护理常规']));
  const numeratorMatched = await getMatchedPatientsByOrderFilter(monthKey, denominatorMatched.done, orderQueryToMonthEnd(monthKey, ['感染性休克患者集束化治疗']));
  return {
    num: numeratorMatched.done.length,
    denom: denominatorMatched.done.length,
    notDone: numeratorMatched.notDone.map(patient => String(patient._id)),
  };
}

async function calcDVTCarryover(monthKey, department) {
  const patients = await getInIcuPatients(monthKey, department);
  const filter = {
    orderTime: { $lte: monthRange(monthKey).end },
    orderName: { $not: /撤销/ },
    $or: [
      ...DVT_DEVICE.map(k => ({ orderName: new RegExp(escapeReg(k)) })),
      ...DVT_HEPARIN.map(k => ({ orderName: new RegExp(escapeReg(k)), exeMethod: '皮下注射' })),
      ...DVT_RIVA.map(k => ({ orderName: new RegExp(escapeReg(k)), exeMethod: { $in: ['口服', '胃管置管术注药'] } })),
    ],
  };
  const matched = await getMatchedPatientsByOrderFilter(monthKey, patients, filter);
  return { num: matched.done.length, denom: matched.denominator.length };
}

async function calcOrderBasedCarryover(monthKey, department, denomKeyword, numKeyword) {
  const patients = await getInIcuPatients(monthKey, department);
  const denominatorMatched = await getMatchedPatientsByOrderFilter(monthKey, patients, orderQueryToMonthEnd(monthKey, [denomKeyword]));
  const numeratorMatched = await getMatchedPatientsByOrderFilter(monthKey, denominatorMatched.done, orderQueryToMonthEnd(monthKey, [numKeyword]));
  return { num: numeratorMatched.done.length, denom: denominatorMatched.done.length };
}

async function calcEN48hCarryover(monthKey, department) {
  const { end } = monthRange(monthKey);
  const patients = (await getInIcuPatients(monthKey, department)).filter(patient => {
    const admission = asDate(patient.icuAdmissionTime);
    const discharge = asDate(patient.icuDischargeTime) || end;
    return admission && discharge - admission >= 48 * 3600 * 1000;
  });
  const mrns = [...new Set(patients.map(patient => patient.mrn).filter(Boolean))];
  const orders = await Order.find({
    mrn: { $in: mrns },
    orderTime: { $lte: end },
    orderName: { $regex: /流质饮食/, $not: /撤销/ },
  }).select('mrn orderTime').lean();
  const orderTimesByMrn = new Map();
  orders.forEach(order => {
    const mrn = String(order.mrn || '').trim();
    const orderTime = asDate(order.orderTime);
    if (!mrn || !orderTime) return;
    if (!orderTimesByMrn.has(mrn)) orderTimesByMrn.set(mrn, []);
    orderTimesByMrn.get(mrn).push(orderTime);
  });
  orderTimesByMrn.forEach(times => times.sort((a, b) => a - b));

  let num = 0;
  const patientsByMrn = new Map();
  patients.forEach(patient => {
    const mrn = String(patient.mrn || '').trim();
    if (!mrn) return;
    if (!patientsByMrn.has(mrn)) patientsByMrn.set(mrn, []);
    patientsByMrn.get(mrn).push(patient);
  });

  patientsByMrn.forEach((mrnPatients, mrn) => {
    const sortedPatients = sortPatientsByAdmission(mrnPatients);
    const orderTimes = orderTimesByMrn.get(mrn) || [];
    const usedOrders = new Set();
    sortedPatients.forEach(patient => {
      const admission = floorDateToMinute(patient.icuAdmissionTime);
      const discharge = asDate(patient.icuDischargeTime) || end;
      const latestStart = admission ? new Date(admission.getTime() + 48 * 3600 * 1000) : null;
      const matchedOrderIndex = orderTimes.findIndex((time, index) => (
        !usedOrders.has(index)
        && admission
        && latestStart
        && time >= admission
        && time <= discharge
        && time <= latestStart
      ));
      if (matchedOrderIndex >= 0) {
        usedOrders.add(matchedOrderIndex);
        num += 1;
      }
    });
  });

  return { num, denom: patients.length };
}

async function calcOrderHitOnIcuCarryover(monthKey, department, keyword) {
  const patients = await getInIcuPatients(monthKey, department);
  const matched = await getMatchedPatientsByOrderFilter(monthKey, patients, orderQueryToMonthEnd(monthKey, [keyword]));
  return { num: matched.done.length, denom: matched.denominator.length };
}

module.exports = {
  // 算法实现
  calcApacheRates, calcMortality, calcShockBundle: calcShockBundleCarryover, calcDVT: calcDVTCarryover,
  calcExtubation, calc48hReturn,
  calcShockUltrasound: (m, d) => calcOrderBasedCarryover(m, d, '休克护理常规', '重症超声筛查评估'),
  calcShockHemodynamic: (m, d) => calcOrderBasedCarryover(m, d, '休克护理常规', 'CVP'),
  calcARDS: (m, d) => calcOrderBasedCarryover(m, d, '中重度ARDS护理常规', '俯卧位通气'),
  calcEN48h: calcEN48hCarryover,
  calcPain: (m, d) => calcOrderHitOnIcuCarryover(m, d, '镇痛评估'),
  calcSedation: (m, d) => calcOrderHitOnIcuCarryover(m, d, '镇静评估'),
  calcRescue,
  calcBrainInjury: (m, d) => calcOrderBasedCarryover(m, d, '急性脑损伤护理常规', '格拉斯哥昏迷评分'),
  readQualityData,
  // 工具
  monthRange, getInIcuPatients,
};
