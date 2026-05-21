const moment = require('moment');
const Patient = require('../models/Patient');
const DoctorQuality = require('../models/DoctorQuality');
const DoctorQualityItem = require('../models/DoctorQualityItem');
const DoctorQualityItemDetail = require('../models/DoctorQualityItemDetail');
const DoctorQC = require('../models/DoctorQC');
const DoctorQCIData = require('../models/DoctorQCIData');
const DoctorQCIDetail = require('../models/DoctorQCIDetail');
const BedRecord = require('../models/BedRecord');
const QualityData = require('../models/QualityData');
const Score = require('../models/Score');
const Order = require('../models/Order');
const TubeExe = require('../models/TubeExe');

const EAST_8_OFFSET_MINUTES = 8 * 60;
const DEPARTMENT_FIELDS = ['department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName', 'dept'];
const PATIENT_SELECT = [
  '_id', 'mrn', 'hospitalNo', 'hospitalNumber', 'zyh', 'zyhm', 'hisPid', 'name', 'birthday', 'age', 'gender',
  'hisBed', 'bedNo', 'bedCode', 'bedName', 'bedNumber', 'hospitalTime', 'icuAdmissionTime', 'icuDischargeTime',
  'department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName', 'dept', 'deptCode',
  'admissionDoctor', 'admissionDoctorName', 'attendingDoctor', 'attendingDoctorName', 'chargeDoctorName', 'tubeDoctorName',
  'bedDoctor', 'admissionSource', 'inSource', 'source', 'dischargedType', 'dischargeType', 'outType',
  'dischargedDepartment', 'transferDept', 'outDeptName',
  'admissionDiagnosis', 'diagnosis', 'clinicalDiagnosis', 'primaryDiagnosis',
  'status',
].join(' ');

const PATIENT_DETAIL_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'statMonth', title: '统计月份' },
  { key: 'department', title: '科室' },
  { key: 'bedNo', title: '床号' },
  { key: 'name', title: '姓名' },
  { key: 'age', title: '年龄' },
  { key: 'hospitalNo', title: '住院号' },
  { key: 'icuAdmissionTime', title: '入科时间' },
  { key: 'icuDischargeTime', title: '出科时间' },
  { key: 'icuDays', title: '在科天数' },
  { key: 'admissionDoctor', title: '收治医生' },
  { key: 'attendingDoctor', title: '管床医生' },
  { key: 'admissionSource', title: '入科来源' },
  { key: 'dischargeType', title: '出科类型' },
  { key: 'transferDept', title: '转出科室' },
  { key: 'diagnosis', title: '临床诊断' },
];

const OCCUPIED_BED_DAY_COLUMNS = [
  ...PATIENT_DETAIL_COLUMNS.slice(0, 9),
  { key: 'occupiedBedDays', title: '占床日数' },
  ...PATIENT_DETAIL_COLUMNS.slice(9),
];

const APACHE_DETAIL_COLUMNS = [
  ...PATIENT_DETAIL_COLUMNS.slice(0, 7),
  { key: 'apacheScore', title: 'APACHEⅡ分数' },
  ...PATIENT_DETAIL_COLUMNS.slice(7),
];

const MORTALITY_DETAIL_COLUMNS = [
  ...PATIENT_DETAIL_COLUMNS.slice(0, 7),
  { key: 'apacheScore', title: 'APACHEⅡ分数' },
  { key: 'predictedMortality', title: '预计病死率' },
  ...PATIENT_DETAIL_COLUMNS.slice(7),
];

const RESCUE_DETAIL_COLUMNS = [
  ...PATIENT_DETAIL_COLUMNS.slice(0, 7),
  { key: 'rescueCount', title: '抢救次数' },
  ...PATIENT_DETAIL_COLUMNS.slice(7),
];

const SUMMARY_COLUMNS = [
  { key: 'index', title: '序号' },
  { key: 'item', title: '指标' },
  { key: 'value', title: '数值' },
  { key: 'action', title: '操作', type: 'action' },
];

const QUALITY_SPECS = [
  { id: 1, key: 'newAdmissions', code: 'ICUShouZhiHuanZheTotalNum', newCode: 'ICUShouZhiHuanZheTotalNum', name: '本科新收患者数', type: 'count' },
  { id: 2, key: 'icuCensus', code: 'ICUShouZhiNum', name: '同期ICU收治患者总数', type: 'count' },
  { id: 3, key: 'bedUsage', code: 'BenKeShouChuangRiLv', newCode: 'BenKeShouChuangRiLv', name: '本科床位使用率', type: 'percent' },
  { id: 4, key: 'avgLengthOfStay', code: 'icu_pingjunzhuyuanr', name: '平均住院日数', type: 'decimal' },
  { id: 5, key: 'icuAdmissionRate', code: 'ICUHuanZheShouZhiLv', name: 'ICU患者收治率', type: 'percent' },
  { id: 6, key: 'icuBedDayRate', code: 'ICUHuanZheShouZhiChuangRiLv', name: 'ICU患者收治床日率', type: 'percent' },
  { id: 7, key: 'apacheGte15Rate', code: 'ApacheUp15Lv', newCode: 'ApacheUp15Lv', name: 'APACHEII≥15患者收治率', type: 'percent' },
  { id: 8, key: 'apacheLt15Rate', code: 'ApacheDown15Lv', name: 'APACHEII<15患者收治率', type: 'percent' },
  { id: 9, key: 'apacheScoreRate', code: 'ApacheIIZongLv', name: 'APACHEII评分率', type: 'percent' },
  { id: 10, key: 'shockBundleRate', code: 'Bundle1Lv', newCode: 'Bundle1Lv', name: '感染性休克集束化治疗完成率', type: 'percent' },
  { id: 11, key: 'antibioticCultureRate', code: 'KangJunLv', newCode: 'KangJunLv', name: '抗菌药物治疗前病原学送检率', type: 'percent' },
  { id: 12, key: 'dvtRate', code: 'DVTLv', newCode: 'DVTLv', name: '深静脉血栓（DVT）预防率', type: 'percent' },
  { id: 13, key: 'predictedMortalityRate', code: 'YuJiDeadLv', name: 'ICU患者预计病死率', type: 'percent' },
  { id: 14, key: 'apacheLt15DeathRate', code: 'deathApacheLte15_constant', name: 'APACHEII评分<15的死亡率', type: 'percent' },
  { id: 15, key: 'standardizedMortalityIndex', code: 'ICUBiaoHuaDeadLv', newCode: 'ICUBiaoHuaDeadLv', name: 'ICU患者标化病死指数', type: 'percent' },
  { id: 16, key: 'unplannedExtubationRate', code: 'ICUNoPlanQIGuanBaGuanLv', newCode: 'ICUNoPlanQIGuanBaGuanLv', name: 'ICU非计划气管插管拔管率', type: 'percent' },
  { id: 17, key: 'reintubation48hRate', code: 'ICUQIGuanBaGuan48ChaGuanLv', newCode: 'ICUQIGuanBaGuan48ChaGuanLv', name: 'ICU气管插管拔管后48h内再插管率', type: 'percent' },
  { id: 18, key: 'unplannedIcuTransferRate', code: 'NoPlanInICULv', newCode: 'NoPlanInICULv', name: '非计划转入ICU率', type: 'percent' },
  { id: 19, key: 'icuReturn48hRate', code: 'OutICU48AgainInLv', newCode: 'OutICU48AgainInLv', name: '转出ICU后48h内重返率', type: 'percent' },
  { id: 20, key: 'shockUltrasoundRate', code: 'shock_ultrasound_screen', name: '休克患者超声筛查评估率', type: 'percent' },
  { id: 21, key: 'shockHemodynamicRate', code: 'shock_blood_flow_detection', name: '休克患者血流动力学指标监测率', type: 'percent' },
  { id: 22, key: 'ardsRate', code: 'ards_constant', newCode: 'ARDSLv', name: '急性呼吸窘迫综合征（ARDS）', type: 'percent' },
  { id: 23, key: 'en48hRate', code: 'en_start_in48_constant', newCode: 'StartEnIn48Lv', name: '48H肠内营养（EN）启动率', type: 'percent' },
  { id: 24, key: 'painRate', code: 'icu_analgesia_constant', newCode: 'PAINLv', name: 'ICU镇痛评估率', type: 'percent' },
  { id: 25, key: 'sedationRate', code: 'icu_calm_constant', newCode: 'RASSLv', name: 'ICU镇静评估率', type: 'percent' },
  { id: 26, key: 'rescueSuccessRate', code: 'rescue_success', name: '抢救成功率', type: 'percent' },
  { id: 27, key: 'acuteBrainInjuryRate', code: 'icu_acute_brain_injury', newCode: 'ICUBrainHurtLv', name: 'ICU急性脑损伤患者意识评估率', type: 'percent' },
];

const SPEC_BY_KEY = new Map(QUALITY_SPECS.map(item => [item.key, item]));
const SPEC_BY_CODE = new Map(QUALITY_SPECS.map(item => [item.code, item]));
const DEFAULT_DEPT_CODE_MAP = {
  '重症医学科': '0211',
  ICU: '0211',
};
const COMPUTED_RATIO_KEYS = new Set(['bedUsage', 'avgLengthOfStay', 'icuAdmissionRate', 'icuBedDayRate', 'antibioticCultureRate']);
const CALCULATED_FALLBACK_KEYS = new Set([
  'shockBundleRate',
  'apacheGte15Rate',
  'apacheLt15Rate',
  'apacheScoreRate',
  'predictedMortalityRate',
  'apacheLt15DeathRate',
  'shockUltrasoundRate',
  'shockHemodynamicRate',
  'ardsRate',
  'en48hRate',
  'painRate',
  'sedationRate',
  'dvtRate',
  'rescueSuccessRate',
  'acuteBrainInjuryRate',
  'standardizedMortalityIndex',
  'unplannedExtubationRate',
  'reintubation48hRate',
  'unplannedIcuTransferRate',
  'icuReturn48hRate',
]);
const APACHE_DETAIL_KEYS = new Set(['apacheGte15Rate', 'apacheLt15Rate', 'apacheScoreRate']);
const APACHE_UNSCORED_ORDER = 2;
const ICU_CENSUS_DENOMINATOR_KEYS = new Set([
  'apacheGte15Rate',
  'apacheLt15Rate',
  'apacheScoreRate',
  'dvtRate',
  'predictedMortalityRate',
  'standardizedMortalityIndex',
  'unplannedIcuTransferRate',
  'painRate',
  'sedationRate',
  'acuteBrainInjuryRate',
]);

function normalizeText(value) {
  return String(value ?? '').trim();
}

function asDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

function formatDateTime(value) {
  const date = asDate(value);
  return date ? moment(date).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM-DD HH:mm') : '';
}

function firstValue(doc, fields) {
  for (const field of fields) {
    const value = doc[field];
    if (value !== undefined && value !== null && value !== '') return value;
  }
  return '';
}

function calcAge(patient) {
  const explicitAge = firstValue(patient, ['age']);
  if (explicitAge !== '') return String(explicitAge).includes('岁') ? String(explicitAge) : `${explicitAge}岁`;
  const birthday = asDate(patient.birthday);
  if (!birthday) return '';
  return `${moment().utcOffset(EAST_8_OFFSET_MINUTES).diff(moment(birthday).utcOffset(EAST_8_OFFSET_MINUTES), 'years')}岁`;
}

function calcIcuDays(patient) {
  const start = asDate(patient.icuAdmissionTime);
  if (!start) return '';
  const end = asDate(patient.icuDischargeTime) || new Date();
  const startDay = moment(start).utcOffset(EAST_8_OFFSET_MINUTES).startOf('day');
  const endDay = moment(end).utcOffset(EAST_8_OFFSET_MINUTES).startOf('day');
  return `${Math.max(1, endDay.diff(startDay, 'days') + 1)}天`;
}

function calcOccupiedBedDays(patient, monthKey) {
  const admission = asDate(patient.icuAdmissionTime);
  if (!admission) return 0;

  const { startDate, endDate } = getMonthRange(monthKey);
  const discharge = asDate(patient.icuDischargeTime) || endDate;
  const start = moment.max(
    moment(admission).utcOffset(EAST_8_OFFSET_MINUTES),
    moment(startDate).utcOffset(EAST_8_OFFSET_MINUTES),
  );
  const end = moment.min(
    moment(discharge).utcOffset(EAST_8_OFFSET_MINUTES),
    moment(endDate).utcOffset(EAST_8_OFFSET_MINUTES),
  );
  if (end.isBefore(start)) return 0;
  return end.clone().startOf('day').diff(start.clone().startOf('day'), 'days') + 1;
}

function toPatientDetailRow(patient, index, statMonth, extra = {}) {
  return {
    index,
    statMonth,
    department: firstValue(patient, ['department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName', 'dept']) || '重症医学科',
    bedNo: firstValue(patient, ['hisBed', 'bedNo', 'bedName', 'bedCode', 'bedNumber']),
    name: firstValue(patient, ['name']),
    age: calcAge(patient),
    hospitalNo: firstValue(patient, ['hospitalNo', 'hospitalNumber', 'mrn', 'zyh', 'zyhm']),
    icuAdmissionTime: formatDateTime(patient.icuAdmissionTime),
    icuDischargeTime: formatDateTime(patient.icuDischargeTime),
    icuDays: calcIcuDays(patient),
    admissionDoctor: firstValue(patient, ['admissionDoctor', 'admissionDoctorName', 'bedDoctor']),
    attendingDoctor: firstValue(patient, ['attendingDoctor', 'attendingDoctorName', 'bedDoctor', 'chargeDoctorName', 'tubeDoctorName']),
    admissionSource: firstValue(patient, ['admissionSource', 'inSource', 'source']),
    dischargeType: firstValue(patient, ['dischargedType', 'dischargeType', 'outType']),
    transferDept: firstValue(patient, ['dischargedDepartment', 'transferDept', 'outDeptName']),
    diagnosis: firstValue(patient, ['clinicalDiagnosis', 'diagnosis', 'admissionDiagnosis', 'primaryDiagnosis']),
    ...extra,
  };
}

function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildDepartmentOr(department) {
  if (process.env.ENABLE_DEPT_FILTER !== 'true' || !department) return [];
  const regex = new RegExp(escapeRegExp(department), 'i');
  return DEPARTMENT_FIELDS.map(field => ({ [field]: regex }));
}

function buildPatientFilter(extra = {}, department = '') {
  const and = [{ status: { $ne: 'invalid' } }, extra];
  const deptOr = buildDepartmentOr(department);
  if (deptOr.length) and.push({ $or: deptOr });
  return { $and: and };
}

function validateYear(year) {
  const n = Number(year);
  if (!Number.isInteger(n) || n < 2000 || n > 2099) throw new Error('年份格式不正确');
  return n;
}

function validateMonth(month, fieldName) {
  if (!moment(month, 'YYYY-MM', true).isValid()) throw new Error(`${fieldName}格式不正确，应为 YYYY-MM`);
  return month;
}

function buildMonths(startMonth, endMonth) {
  validateMonth(startMonth, '开始月份');
  validateMonth(endMonth, '结束月份');
  const cur = moment(startMonth, 'YYYY-MM');
  const end = moment(endMonth, 'YYYY-MM');
  if (cur.isAfter(end)) throw new Error('开始月份不能晚于结束月份');
  if (end.diff(cur, 'months') > 36) throw new Error('查询范围不能超过 36 个月');

  const months = [];
  while (cur.isSameOrBefore(end)) {
    months.push(cur.format('YYYY-MM'));
    cur.add(1, 'month');
  }
  return months;
}

function getRangeFromQuery({ year, startMonth, endMonth }) {
  if (year) {
    const y = validateYear(year);
    const months = Array.from({ length: 12 }, (_, i) => `${y}-${String(i + 1).padStart(2, '0')}`);
    return {
      months,
      startMonth: `${y}-01`,
      endMonth: `${y}-12`,
    };
  }

  if (!startMonth || !endMonth) throw new Error('月份参数缺失');
  const months = buildMonths(startMonth, endMonth);
  return {
    months,
    startMonth,
    endMonth,
  };
}

function getMonthRange(monthKey) {
  return {
    startDate: moment.parseZone(`${monthKey}-01T00:00:00+08:00`).startOf('month').toDate(),
    endDate: moment.parseZone(`${monthKey}-01T00:00:00+08:00`).endOf('month').toDate(),
  };
}

function buildMonthlyOverlapFilter(startDate, endDate, department) {
  return buildPatientFilter({
    icuAdmissionTime: { $lte: endDate },
    $or: [
      { icuDischargeTime: { $gte: startDate } },
      { icuDischargeTime: null },
      { icuDischargeTime: { $exists: false } },
    ],
  }, department);
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

function buildPatientEntriesByOrderMatch(patients, orders, fallbackEndDate) {
  const patientsByMrn = new Map();
  const orderTimesByMrn = new Map();

  patients.forEach(patient => {
    const mrn = normalizeText(patient.mrn);
    if (!mrn) return;
    if (!patientsByMrn.has(mrn)) patientsByMrn.set(mrn, []);
    patientsByMrn.get(mrn).push(patient);
  });

  orders.forEach(order => {
    const mrn = normalizeText(order.mrn);
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

    sortedPatients.forEach(patient => denominator.push(patient));

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
        done.push(patient);
      } else {
        notDone.push(patient);
      }
    });
  });

  return { denominator, done, notDone };
}

async function getMatchedPatientEntriesByOrderFilter(patients, orderFilter, fallbackEndDate) {
  const mrns = [...new Set(patients.map(patient => patient.mrn).filter(Boolean))];
  if (!mrns.length) return { denominator: [], done: [], notDone: [] };
  const orders = await Order.find({
    ...orderFilter,
    mrn: { $in: mrns },
  }).select('mrn orderTime').lean();
  return buildPatientEntriesByOrderMatch(patients, orders, fallbackEndDate);
}

async function getOrderPairEntriesForMonth(patients, endDate, denominatorKeyword, numeratorKeyword) {
  const denominatorMatched = await getMatchedPatientEntriesByOrderFilter(
    patients,
    buildOrderUpToEndQuery(endDate, denominatorKeyword),
    endDate,
  );
  const denominatorPatients = denominatorMatched.done;
  if (!denominatorPatients.length) {
    return { denominator: [], done: [], notDone: [] };
  }
  const numeratorMatched = await getMatchedPatientEntriesByOrderFilter(
    denominatorPatients,
    buildOrderUpToEndQuery(endDate, numeratorKeyword),
    endDate,
  );
  return {
    denominator: denominatorPatients,
    done: numeratorMatched.done,
    notDone: numeratorMatched.notDone,
  };
}

function buildDvtOrderFilter(endDate) {
  const DVT_DEVICE = ['肢体气压治疗', '梯度压力弹力袜', '腔静脉滤器'];
  const DVT_HEPARIN = ['低分子肝素钠', '低分子肝素钙', '那曲肝素', '依诺肝素', '达肝素钠注射液'];
  const DVT_RIVA = ['利伐沙班'];
  return {
    orderTime: { $lte: endDate },
    orderName: { $not: /撤销/ },
    $or: [
      ...DVT_DEVICE.map(keyword => ({ orderName: new RegExp(escapeOrderRegExp(keyword)) })),
      ...DVT_HEPARIN.map(keyword => ({ orderName: new RegExp(escapeOrderRegExp(keyword)), exeMethod: '皮下注射' })),
      ...DVT_RIVA.map(keyword => ({ orderName: new RegExp(escapeOrderRegExp(keyword)), exeMethod: { $in: ['口服', '胃管置管术注药'] } })),
    ],
  };
}

async function getEn48hEntriesForMonth(patients, endDate) {
  const mrns = [...new Set(patients.map(patient => patient.mrn).filter(Boolean))];
  if (!mrns.length) return { denominator: [], done: [], notDone: [] };

  const orders = await Order.find({
    mrn: { $in: mrns },
    orderTime: { $lte: endDate },
    orderName: { $regex: /流质饮食/, $not: /撤销/ },
  }).select('mrn orderTime').lean();

  const orderTimesByMrn = new Map();
  orders.forEach(order => {
    const mrn = normalizeText(order.mrn);
    const orderTime = asDate(order.orderTime);
    if (!mrn || !orderTime) return;
    if (!orderTimesByMrn.has(mrn)) orderTimesByMrn.set(mrn, []);
    orderTimesByMrn.get(mrn).push(orderTime);
  });
  orderTimesByMrn.forEach(times => times.sort((a, b) => a - b));

  const patientsByMrn = new Map();
  patients.forEach(patient => {
    const mrn = normalizeText(patient.mrn);
    if (!mrn) return;
    if (!patientsByMrn.has(mrn)) patientsByMrn.set(mrn, []);
    patientsByMrn.get(mrn).push(patient);
  });

  const denominator = [];
  const done = [];
  const notDone = [];

  patientsByMrn.forEach((mrnPatients, mrn) => {
    const sortedPatients = sortPatientsByAdmission(mrnPatients);
    const orderTimes = orderTimesByMrn.get(mrn) || [];
    const usedOrders = new Set();

    sortedPatients.forEach(patient => {
      denominator.push(patient);
      const admission = floorDateToMinute(patient.icuAdmissionTime);
      const discharge = asDate(patient.icuDischargeTime) || endDate;
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
        done.push(patient);
      } else {
        notDone.push(patient);
      }
    });
  });

  return { denominator, done, notDone };
}

function getDepartmentCodeMap() {
  if (!process.env.QUALITY_DEPT_CODE_MAP) return DEFAULT_DEPT_CODE_MAP;
  try {
    return { ...DEFAULT_DEPT_CODE_MAP, ...JSON.parse(process.env.QUALITY_DEPT_CODE_MAP) };
  } catch (err) {
    console.warn('QUALITY_DEPT_CODE_MAP 解析失败，已回退到默认科室编码映射');
    return DEFAULT_DEPT_CODE_MAP;
  }
}

function resolveDepartmentCode(department) {
  const codeMap = getDepartmentCodeMap();
  if (department && codeMap[department]) return codeMap[department];
  return process.env.QUALITY_DEFAULT_DEPT_CODE || codeMap['重症医学科'] || '';
}

function safeNumber(value) {
  const num = Number(value);
  return Number.isFinite(num) ? num : 0;
}

function parseMonthFromFlag(flag) {
  const match = String(flag || '').match(/(\d{1,2})/);
  return match ? Number(match[1]) : null;
}

function buildMonthDocMap(docs) {
  const map = new Map();
  docs.forEach(doc => {
    const month = parseMonthFromFlag(doc.flag);
    if (!doc.yearFlag || !month) return;
    map.set(`${doc.yearFlag}-${String(month).padStart(2, '0')}`, doc);
  });
  return map;
}

function shouldMultiplyBy100(spec, rawValue) {
  if (spec.type !== 'percent') return false;
  return safeNumber(rawValue) <= 1.000001;
}

function formatMetricValue(spec, rawValue) {
  if (rawValue === '' || rawValue === null || rawValue === undefined) return spec.type === 'count' ? '0' : '/';
  const num = safeNumber(rawValue);
  if (spec.type === 'count') return String(Math.round(num));
  if (spec.type === 'decimal') {
    return trimTrailingZeros(num.toFixed(4));
  }
  const percentValue = shouldMultiplyBy100(spec, rawValue) ? num * 100 : num;
  return `${percentValue.toFixed(2)}%`;
}

function trimTrailingZeros(text) {
  return String(text).replace(/(\.\d*?[1-9])0+$/u, '$1').replace(/\.0+$/u, '');
}

function escapeOrderRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildOrderQuery(monthKey, keyword) {
  const { startDate, endDate } = getMonthRange(monthKey);
  return {
    orderTime: { $gte: startDate, $lte: endDate },
    orderName: { $regex: new RegExp(escapeOrderRegExp(keyword)), $not: /撤销/ },
  };
}

function buildOrderUpToEndQuery(endDate, keyword) {
  return {
    orderTime: { $lte: endDate },
    orderName: { $regex: new RegExp(escapeOrderRegExp(keyword)), $not: /撤销/ },
  };
}

async function fetchQualityDocs(months, department) {
  const years = [...new Set(months.map(month => Number(month.slice(0, 4))))];
  const monthSet = new Set(months.map(month => Number(month.slice(5, 7))));
  const deptCode = resolveDepartmentCode(department);
  const docs = await DoctorQuality.find({
    ...(deptCode ? { deptCode } : {}),
    yearFlag: { $in: years },
    indicatorCode: { $in: QUALITY_SPECS.map(item => item.code) },
  }).lean();

  return docs.filter(doc => monthSet.has(parseMonthFromFlag(doc.flag)));
}

async function fetchQualityQcDocs(months, department) {
  const years = [...new Set(months.map(month => Number(month.slice(0, 4))))];
  const monthSet = new Set(months.map(month => Number(month.slice(5, 7))));
  const deptCode = resolveDepartmentCode(department);
  const docs = await DoctorQC.find({
    ...(deptCode ? { deptCode } : {}),
    yearFlag: { $in: years },
    indicatorCode: { $in: QUALITY_SPECS.map(item => item.newCode).filter(Boolean) },
  }).lean();

  return docs.filter(doc => monthSet.has(parseMonthFromFlag(doc.flag)));
}

async function fetchItemsByQualityIds(qualityIds) {
  if (!qualityIds.length) return [];
  return DoctorQualityItem.find({ qualityId: { $in: qualityIds } }).lean();
}

async function fetchQcItemsByQualityIds(qualityIds) {
  if (!qualityIds.length) return [];
  return DoctorQCIData.find({ qualityId: { $in: qualityIds } }).lean();
}

async function fetchDetailRowsByItemIds(itemIds) {
  if (!itemIds.length) return [];
  return DoctorQualityItemDetail.find({ itemId: { $in: itemIds } }).lean();
}

async function fetchQcDetailRowsByItemIds(itemIds) {
  if (!itemIds.length) return [];
  return DoctorQCIDetail.find({ itemId: { $in: itemIds } }).lean();
}

function buildItemsByQualityId(items) {
  const map = new Map();
  items.forEach(item => {
    const key = String(item.qualityId);
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(item);
  });
  for (const itemList of map.values()) {
    itemList.sort((a, b) => safeNumber(a.order) - safeNumber(b.order));
  }
  return map;
}

function buildDetailsByItemId(details) {
  const map = new Map();
  details.forEach(detail => {
    const key = String(detail.itemId);
    if (!map.has(key)) map.set(key, []);
    map.get(key).push(detail);
  });
  return map;
}

function aggregateItemValue(docs, itemsByQualityId, order) {
  return docs.reduce((sum, doc) => {
    const item = (itemsByQualityId.get(String(doc._id)) || []).find(entry => safeNumber(entry.order) === order);
    return sum + safeNumber(item?.itemData);
  }, 0);
}

function buildIndicatorRow(spec, months, monthDocMap, itemsByQualityId, basicStats = new Map()) {
  const monthDocs = months.map(monthKey => monthDocMap.get(monthKey)).filter(Boolean);
  const numeratorTotal = aggregateItemValue(monthDocs, itemsByQualityId, 0);
  const denominatorTotal = ICU_CENSUS_DENOMINATOR_KEYS.has(spec.key)
    ? months.reduce((sum, monthKey) => sum + safeNumber(basicStats.get(monthKey)?.icuCensus), 0)
    : aggregateItemValue(monthDocs, itemsByQualityId, 1);
  const row = {
    id: spec.id,
    key: spec.key,
    code: spec.code,
    name: spec.name,
    ratio: spec.type === 'count' ? '/' : formatAggregateRatio(spec, numeratorTotal, denominatorTotal, monthDocs),
    numerator: spec.type === 'count' ? '/' : formatAggregateNumber(numeratorTotal),
    denominator: formatAggregateDenominator(denominatorTotal, monthDocs, spec),
    months: {},
  };

  months.forEach(monthKey => {
    const doc = monthDocMap.get(monthKey);
    if (ICU_CENSUS_DENOMINATOR_KEYS.has(spec.key) && doc) {
      const numeratorItem = (itemsByQualityId.get(String(doc._id)) || []).find(entry => safeNumber(entry.order) === 0);
      const numerator = safeNumber(numeratorItem?.itemData);
      const denominator = safeNumber(basicStats.get(monthKey)?.icuCensus);
      row.months[monthKey] = {
        display: formatComputedRatio(spec, numerator, denominator),
      };
      return;
    }
    row.months[monthKey] = {
      display: doc ? formatMetricValue(spec, doc.indicatorData) : (spec.type === 'count' ? '0' : '/'),
    };
  });

  return row;
}

function buildBasicCountRow(spec, months, basicStats) {
  const total = months.reduce((sum, monthKey) => sum + safeNumber(basicStats.get(monthKey)?.[spec.key]), 0);
  return {
    id: spec.id,
    key: spec.key,
    code: spec.code,
    name: spec.name,
    ratio: '/',
    numerator: '/',
    denominator: String(total),
    months: Object.fromEntries(months.map(monthKey => [
      monthKey,
      { display: String(safeNumber(basicStats.get(monthKey)?.[spec.key])) },
    ])),
  };
}

function formatAggregateRatio(spec, numeratorTotal, denominatorTotal, docs) {
  if (!docs.length) return '/';
  if (spec.type === 'decimal') {
    if (!denominatorTotal) return '/';
    return trimTrailingZeros((numeratorTotal / denominatorTotal).toFixed(4));
  }
  if (denominatorTotal) {
    return `${((numeratorTotal / denominatorTotal) * 100).toFixed(2)}%`;
  }
  const first = docs[0];
  return first ? formatMetricValue(spec, first.indicatorData) : '/';
}

function formatAggregateNumber(value) {
  return trimTrailingZeros(value.toFixed(2));
}

function formatAggregateDenominator(value, docs, spec) {
  if (spec.type === 'count') {
    if (value > 0) return String(Math.round(value));
    return String(Math.round(docs.reduce((sum, doc) => sum + safeNumber(doc.indicatorData), 0)));
  }
  return trimTrailingZeros(value.toFixed(2));
}

function formatComputedRatio(spec, numerator, denominator) {
  if (!denominator) return spec.type === 'decimal' ? '/' : '0.00%';
  const value = numerator / denominator;
  if (spec.type === 'decimal') return trimTrailingZeros(value.toFixed(4));
  return `${(value * 100).toFixed(2)}%`;
}

function buildComputedIndicatorRow(spec, months, computedStats) {
  const totals = months.reduce((result, monthKey) => {
    const stats = computedStats.get(monthKey) || {};
    const values = getComputedMetricValues(spec.key, stats);
    result.numerator += values.numerator;
    result.denominator += values.denominator;
    return result;
  }, { numerator: 0, denominator: 0 });

  return {
    id: spec.id,
    key: spec.key,
    code: spec.code,
    name: spec.name,
    ratio: formatComputedRatio(spec, totals.numerator, totals.denominator),
    numerator: formatAggregateNumber(totals.numerator),
    denominator: formatAggregateNumber(totals.denominator),
    months: Object.fromEntries(months.map(monthKey => {
      const stats = computedStats.get(monthKey) || {};
      const values = getComputedMetricValues(spec.key, stats);
      return [monthKey, { display: formatComputedRatio(spec, values.numerator, values.denominator) }];
    })),
  };
}

function getComputedMetricValues(key, stats) {
  if (key === 'bedUsage') {
    return { numerator: safeNumber(stats.occupiedBedDays), denominator: safeNumber(stats.configuredBedDays) };
  }
  if (key === 'avgLengthOfStay') {
    return { numerator: safeNumber(stats.occupiedBedDays), denominator: safeNumber(stats.icuCensus) };
  }
  if (key === 'icuAdmissionRate') {
    return { numerator: safeNumber(stats.icuCensus), denominator: safeNumber(stats.hospitalAdmissions) };
  }
  if (key === 'icuBedDayRate') {
    return { numerator: safeNumber(stats.occupiedBedDays), denominator: safeNumber(stats.hospitalBedDays) };
  }
  if (key === 'antibioticCultureRate') {
    return { numerator: safeNumber(stats.antibioticCultureCases), denominator: safeNumber(stats.antibioticTreatmentCases) };
  }
  return { numerator: 0, denominator: 0 };
}

function getCalculatedMetricValues(key, stats) {
  if (key === 'shockBundleRate') return { numerator: stats.shockBundleDone, denominator: stats.shockBundleDenominator };
  if (key === 'apacheGte15Rate') return { numerator: stats.apacheGte15, denominator: stats.icuCensus };
  if (key === 'apacheLt15Rate') return { numerator: stats.apacheLt15, denominator: stats.icuCensus };
  if (key === 'apacheScoreRate') return { numerator: stats.apacheScored, denominator: stats.icuCensus };
  if (key === 'predictedMortalityRate') return { numerator: stats.predictedMortalitySum, denominator: stats.icuCensus };
  if (key === 'apacheLt15DeathRate') return { numerator: stats.apacheLt15Death, denominator: stats.apacheLt15 };
  if (key === 'standardizedMortalityIndex') return { numerator: stats.actualMortalityRate, denominator: stats.predictedMortalityRate };
  if (key === 'unplannedExtubationRate') return { numerator: stats.unplannedExtubationNum, denominator: stats.extubationDenominator };
  if (key === 'reintubation48hRate') return { numerator: stats.reintubationNum, denominator: stats.extubationDenominator };
  if (key === 'unplannedIcuTransferRate') return { numerator: stats.unplannedIcuTransferNum, denominator: stats.icuCensus };
  if (key === 'icuReturn48hRate') return { numerator: stats.icuReturn48hNum, denominator: stats.icuReturnDenominator };
  if (key === 'shockUltrasoundRate') return { numerator: stats.shockUltrasoundNum, denominator: stats.shockDenominator };
  if (key === 'shockHemodynamicRate') return { numerator: stats.shockHemodynamicNum, denominator: stats.shockDenominator };
  if (key === 'ardsRate') return { numerator: stats.ardsNum, denominator: stats.ardsDenominator };
  if (key === 'en48hRate') return { numerator: stats.en48hNum, denominator: stats.en48hDenominator };
  if (key === 'painRate') return { numerator: stats.painNum, denominator: stats.icuCensus };
  if (key === 'sedationRate') return { numerator: stats.sedationNum, denominator: stats.icuCensus };
  if (key === 'dvtRate') return { numerator: stats.dvtNum, denominator: stats.icuCensus };
  if (key === 'rescueSuccessRate') return { numerator: stats.rescueSuccess, denominator: stats.rescueDenominator };
  if (key === 'acuteBrainInjuryRate') return { numerator: stats.brainInjuryNum, denominator: stats.brainInjuryDenominator };
  return { numerator: 0, denominator: 0 };
}

function getDisplayValuesForCalculatedMetric(key, stats) {
  if (key === 'predictedMortalityRate') {
    return { numerator: safeNumber(stats.predictedMortalitySum) * 1000, denominator: safeNumber(stats.icuCensus) };
  }
  return getCalculatedMetricValues(key, stats);
}

function buildCalculatedFallbackRow(spec, months, monthDocMap, itemsByQualityId, basicStats, calculatedStats) {
  const totals = { numerator: 0, denominator: 0 };
  const ratioTotals = { numerator: 0, denominator: 0 };
  const row = {
    id: spec.id,
    key: spec.key,
    code: spec.code,
    name: spec.name,
    ratio: '/',
    numerator: '0',
    denominator: '0',
    months: {},
  };

  months.forEach(monthKey => {
    const monthStats = calculatedStats.get(monthKey) || {};
    const values = getDisplayValuesForCalculatedMetric(spec.key, monthStats);
    const ratioValues = getCalculatedMetricValues(spec.key, monthStats);
    totals.numerator += values.numerator;
    totals.denominator += values.denominator;
    ratioTotals.numerator += ratioValues.numerator;
    ratioTotals.denominator += ratioValues.denominator;
    row.months[monthKey] = { display: formatComputedRatio(spec, ratioValues.numerator, ratioValues.denominator) };
  });

  row.ratio = formatComputedRatio(spec, ratioTotals.numerator, ratioTotals.denominator);
  row.numerator = formatAggregateNumber(totals.numerator);
  row.denominator = formatAggregateNumber(totals.denominator);
  return row;
}

function isIcuCensusDenominatorItem(spec, order, itemName) {
  if (!ICU_CENSUS_DENOMINATOR_KEYS.has(spec.key) || order === 0) return false;
  return /ICU.*(总数|人数)/.test(String(itemName || ''));
}

async function getQualityStats(params) {
  const { months, startMonth, endMonth } = getRangeFromQuery(params);
  const department = params.department || '';
  const [docs, qcDocs] = await Promise.all([
    fetchQualityDocs(months, department),
    fetchQualityQcDocs(months, department),
  ]);
  const [items, qcItems] = await Promise.all([
    fetchItemsByQualityIds(docs.map(doc => String(doc._id))),
    fetchQcItemsByQualityIds(qcDocs.map(doc => String(doc._id))),
  ]);
  const basicStats = await getBasicMonthlyStats(months, department);
  const computedStats = await getComputedMonthlyStats(months, department, basicStats);
  const calculatedStats = await getCalculatedMonthlyStats(months, department, basicStats);
  const itemsByQualityId = buildItemsByQualityId(items);
  const qcItemsByQualityId = buildItemsByQualityId(qcItems);

  const rows = QUALITY_SPECS.map(spec => {
    if (spec.key === 'newAdmissions' || spec.key === 'icuCensus') {
      return buildBasicCountRow(spec, months, basicStats);
    }
    if (COMPUTED_RATIO_KEYS.has(spec.key)) {
      return buildComputedIndicatorRow(spec, months, computedStats);
    }
    const monthDocMap = buildMonthDocMap(docs.filter(doc => doc.indicatorCode === spec.code));
    const qcMonthDocMap = buildMonthDocMap(qcDocs.filter(doc => doc.indicatorCode === spec.newCode));
    const mergedMonthDocMap = new Map();
    months.forEach(monthKey => {
      mergedMonthDocMap.set(monthKey, monthDocMap.get(monthKey) || qcMonthDocMap.get(monthKey));
    });
    if (CALCULATED_FALLBACK_KEYS.has(spec.key)) {
      return buildCalculatedFallbackRow(spec, months, mergedMonthDocMap, new Map([
        ...itemsByQualityId,
        ...qcItemsByQualityId,
      ]), basicStats, calculatedStats);
    }
    return buildIndicatorRow(spec, months, mergedMonthDocMap, new Map([
      ...itemsByQualityId,
      ...qcItemsByQualityId,
    ]), basicStats);
  });

  return {
    indicators: rows,
    months,
    startMonth,
    endMonth,
    department,
  };
}

async function getBasicMonthlyStats(months, department) {
  const entries = await Promise.all(months.map(async monthKey => {
    const { startDate, endDate } = getMonthRange(monthKey);
    const [newAdmissions, icuCensus] = await Promise.all([
      Patient.countDocuments(buildPatientFilter({
        icuAdmissionTime: { $gte: startDate, $lte: endDate },
      }, department)),
      Patient.countDocuments(buildMonthlyOverlapFilter(startDate, endDate, department)),
    ]);
    return [monthKey, { newAdmissions, icuCensus }];
  }));
  return new Map(entries);
}

async function getQualityDataValue(monthKey, indicatorCode, indicatorName = '') {
  const [year, month] = monthKey.split('-').map(Number);
  const doc = await QualityData.findOne({
    deptCode: 'all',
    year,
    month,
    $or: [
      { indicatorCode },
      ...(indicatorName ? [{ indicator: indicatorName }] : []),
    ],
  }).lean();
  return safeNumber(doc?.indicatorData);
}

async function getQualityDataDoc(monthKey, indicatorCode, indicatorName = '') {
  const [year, month] = monthKey.split('-').map(Number);
  return QualityData.findOne({
    deptCode: 'all',
    year,
    month,
    $or: [
      { indicatorCode },
      ...(indicatorName ? [{ indicator: indicatorName }] : []),
    ],
  }).lean();
}

async function getComputedMonthlyStats(months, department, basicStats = new Map()) {
  const entries = await Promise.all(months.map(async monthKey => {
    const [occupiedBedDays, configuredBedDays, hospitalAdmissions, hospitalBedDays, antibioticCultureCases, antibioticTreatmentCases] = await Promise.all([
      calcOccupiedBedDayTotal([monthKey], department),
      calcConfiguredBedDayTotal([monthKey], department),
      getQualityDataValue(monthKey, 'HosShouZhiHuanZheTotalNum', '同期医院收治患者总数'),
      getQualityDataValue(monthKey, 'HosShouZhiHuanZheTotalChuangRiNum', '同期医院患者收治总床日数'),
      getQualityDataValue(monthKey, 'KangJunSongJianNum', '使用抗菌药物前病原学送检病例数'),
      getQualityDataValue(monthKey, 'KangJunTotalNum', '同期使用抗菌药物治疗病例总数'),
    ]);
    return [monthKey, {
      occupiedBedDays,
      configuredBedDays,
      hospitalAdmissions,
      hospitalBedDays,
      antibioticCultureCases,
      antibioticTreatmentCases,
      icuCensus: safeNumber(basicStats.get(monthKey)?.icuCensus),
    }];
  }));
  return new Map(entries);
}

function pickApacheScoreForStats(patientScores, startDate, endDate) {
  if (!patientScores.length) return null;
  if (patientScores.length === 1) return patientScores[0];

  const inMonthScore = patientScores.find(item => {
    const scoreTime = asDate(item.time);
    return scoreTime && scoreTime >= startDate && scoreTime <= endDate;
  });
  if (inMonthScore) return inMonthScore;

  return patientScores.find(item => {
    const scoreTime = asDate(item.time);
    return scoreTime && scoreTime < startDate;
  }) || null;
}

async function getApacheScoreMaps(patients, startDate, endDate) {
  const pids = patients.map(patient => String(patient._id));
  if (!pids.length) return { anyScoreByPid: new Map(), selectedScoreByPid: new Map() };
  const scores = await Score.find({
    pid: { $in: pids },
    scoreType: 'apacheII',
    valid: true,
  }).sort({ time: -1 }).lean();

  const grouped = new Map();
  scores.forEach(score => {
    const pid = String(score.pid);
    if (!pid) return;
    if (!grouped.has(pid)) grouped.set(pid, []);
    grouped.get(pid).push(score);
  });

  const anyScoreByPid = new Map();
  const selectedScoreByPid = new Map();
  grouped.forEach((patientScores, pid) => {
    anyScoreByPid.set(pid, patientScores[0]);
    const matched = pickApacheScoreForStats(patientScores, startDate, endDate);
    if (matched) selectedScoreByPid.set(pid, matched);
  });
  return { anyScoreByPid, selectedScoreByPid };
}

async function calcOrderPair(monthKey, department, denominatorKeyword, numeratorKeyword) {
  const { startDate, endDate } = getMonthRange(monthKey);
  const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
    .select(PATIENT_SELECT)
    .lean();
  const sets = await getOrderPairEntriesForMonth(patients, endDate, denominatorKeyword, numeratorKeyword);
  return {
    numerator: sets.done.length,
    denominator: sets.denominator.length,
  };
}

async function calcShockBundleStats(monthKey, department) {
  const { startDate, endDate } = getMonthRange(monthKey);
  const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
    .select(PATIENT_SELECT)
    .lean();
  const sets = await getOrderPairEntriesForMonth(patients, endDate, '感染性休克护理常规', '感染性休克患者集束化治疗');
  return {
    done: sets.done.length,
    denominator: sets.denominator.length,
    notDone: sets.notDone.length,
  };
}

async function calcRescueStats(monthKey) {
  const rescueOrders = await Order.find(buildOrderQuery(monthKey, '抢救')).select('mrn').lean();
  const mrns = [...new Set(rescueOrders.map(item => item.mrn).filter(Boolean))];
  if (!mrns.length) return { success: 0, denominator: 0, death: 0, terminal: 0, rescueCountByMrn: new Map() };
  const patients = await Patient.find(buildPatientFilter({ mrn: { $in: mrns } }))
    .select('mrn dischargedType dischargeType')
    .lean();
  const patientByMrn = new Map(patients.map(patient => [patient.mrn, patient]));
  const rescueCountByMrn = new Map();
  rescueOrders.forEach(order => rescueCountByMrn.set(order.mrn, safeNumber(rescueCountByMrn.get(order.mrn)) + 1));
  let success = 0;
  let denominator = 0;
  let death = 0;
  let terminal = 0;
  mrns.forEach(mrn => {
    const patient = patientByMrn.get(mrn);
    if (!patient) return;
    const dischargeType = String(firstValue(patient, ['dischargedType', 'dischargeType']));
    if (dischargeType.includes('死亡（终末）')) {
      terminal += 1;
      return;
    }
    denominator += 1;
    if (dischargeType === '死亡' || (dischargeType.includes('死亡') && !dischargeType.includes('终末'))) death += 1;
    else success += 1;
  });
  return { success, denominator, death, terminal, rescueCountByMrn };
}

async function getCalculatedMonthlyStats(months, department, basicStats = new Map()) {
  const entries = await Promise.all(months.map(async monthKey => {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(`${PATIENT_SELECT} dischargedType dischargeType`)
      .lean();
    const { anyScoreByPid, selectedScoreByPid } = await getApacheScoreMaps(patients, startDate, endDate);
    let apacheLt15 = 0;
    let apacheGte15 = 0;
    let apacheLt15Death = 0;
    let predictedMortalitySum = 0;
    selectedScoreByPid.forEach((score, pid) => {
      const total = safeNumber(score.total ?? score.apacheII?.totalScore);
      if (total < 15) {
        apacheLt15 += 1;
        const patient = patients.find(item => String(item._id) === pid);
        if (String(firstValue(patient || {}, ['dischargedType', 'dischargeType'])).includes('死亡')) {
          apacheLt15Death += 1;
        }
      } else {
        apacheGte15 += 1;
      }
      predictedMortalitySum += safeNumber(score.apacheII?.calDead?.score);
    });
    const deathCount = patients.filter(patient => String(firstValue(patient, ['dischargedType', 'dischargeType'])).includes('死亡')).length;
    const [shockBundle, shockUltrasound, shockHemodynamic, ards, en48h, pain, sedation, dvt, brainInjury, rescue, extubation, icuReturn, unplannedIcuTransferNum] = await Promise.all([
      calcShockBundleStats(monthKey, department),
      calcOrderPair(monthKey, department, '休克护理常规', '重症超声筛查评估'),
      calcOrderPair(monthKey, department, '休克护理常规', 'CVP'),
      calcOrderPair(monthKey, department, '中重度ARDS护理常规', '俯卧位通气'),
      getEN48hSets([monthKey], department),
      getIcuOrderHitSets([monthKey], department, '镇痛评估'),
      getIcuOrderHitSets([monthKey], department, '镇静评估'),
      getDvtPatientSets([monthKey], department),
      getOrderPairPatientSets([monthKey], department, '急性脑损伤护理常规', '格拉斯哥昏迷评分'),
      calcRescueStats(monthKey),
      getExtubationSets([monthKey], department),
      getIcuReturnSets([monthKey], department),
      getQualityDataValue(monthKey, 'NoPlanInICUNum', '非计划转入ICU患者数'),
    ]);
    const icuCensus = safeNumber(basicStats.get(monthKey)?.icuCensus);
    const predictedMortalityRate = icuCensus ? predictedMortalitySum / icuCensus : 0;
    return [monthKey, {
      icuCensus,
      apacheGte15,
      apacheLt15,
      apacheScored: anyScoreByPid.size,
      apacheUnscored: Math.max(0, icuCensus - anyScoreByPid.size),
      predictedMortalitySum,
      predictedMortalityRate,
      actualMortalityRate: icuCensus ? deathCount / icuCensus : 0,
      apacheLt15Death,
      shockBundleDone: shockBundle.done,
      shockBundleDenominator: shockBundle.denominator,
      shockBundleNotDone: shockBundle.notDone,
      shockUltrasoundNum: shockUltrasound.numerator,
      shockHemodynamicNum: shockHemodynamic.numerator,
      shockDenominator: shockUltrasound.denominator || shockHemodynamic.denominator,
      ardsNum: ards.numerator,
      ardsDenominator: ards.denominator,
      en48hNum: en48h.done.length,
      en48hDenominator: en48h.denominator.length,
      painNum: pain.done.length,
      sedationNum: sedation.done.length,
      dvtNum: dvt.done.length,
      brainInjuryNum: brainInjury.done.length,
      brainInjuryDenominator: brainInjury.denominator.length,
      rescueSuccess: rescue.success,
      rescueDenominator: rescue.denominator,
      rescueDeath: rescue.death,
      rescueTerminal: rescue.terminal,
      extubationDenominator: extubation.denominator.length,
      unplannedExtubationNum: extubation.unplanned.length,
      reintubationNum: extubation.reintubated.length,
      unplannedIcuTransferNum,
      icuReturn48hNum: icuReturn.returned.length,
      icuReturnDenominator: icuReturn.denominator.length,
    }];
  }));
  return new Map(entries);
}

async function getBasicPatientRows(indicatorKey, months, department) {
  const rows = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const filter = indicatorKey === 'newAdmissions'
      ? buildPatientFilter({ icuAdmissionTime: { $gte: startDate, $lte: endDate } }, department)
      : buildMonthlyOverlapFilter(startDate, endDate, department);
    const patients = await Patient.find(filter).select(PATIENT_SELECT).lean();
    rows.push(...patients.map(patient => ({ patient, statMonth: monthKey })));
  }
  return rows.map((item, index) => toPatientDetailRow(item.patient, index + 1, item.statMonth));
}

function isOccupiedBedDayIndicator(spec) {
  return spec.key === 'bedUsage' || spec.key === 'avgLengthOfStay';
}

async function getMonthlyOccupiedPatientEntries(months, department) {
  const entries = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1, _id: 1 })
      .lean();

    patients.forEach(patient => {
      const occupiedDays = calcOccupiedBedDays(patient, monthKey);
      if (occupiedDays > 0) {
        entries.push({ patient, statMonth: monthKey, occupiedDays });
      }
    });
  }
  return entries;
}

async function getOccupiedBedDayRows(months, department) {
  const entries = await getMonthlyOccupiedPatientEntries(months, department);
  return entries.map((item, index) => toPatientDetailRow(item.patient, index + 1, item.statMonth, {
    occupiedBedDays: `${item.occupiedDays}天`,
  }));
}

async function getApachePatientRows(indicatorKey, itemOrder, months, department) {
  const rows = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1, _id: 1 })
      .lean();
    const { anyScoreByPid, selectedScoreByPid } = await getApacheScoreMaps(patients, startDate, endDate);
    patients.forEach(patient => {
      const pid = String(patient._id);
      const selectedScore = selectedScoreByPid.get(pid);
      const anyScore = anyScoreByPid.get(pid);
      const scoreForDisplay = selectedScore || anyScore;
      const total = selectedScore ? safeNumber(selectedScore.total ?? selectedScore.apacheII?.totalScore) : null;
      let matched = false;
      if (indicatorKey === 'apacheGte15Rate') {
        matched = itemOrder === 0 ? total !== null && total >= 15 : true;
      } else if (indicatorKey === 'apacheLt15Rate') {
        matched = itemOrder === 0 ? total !== null && total < 15 : true;
      } else if (indicatorKey === 'apacheLt15DeathRate') {
        const dischargeType = String(firstValue(patient, ['dischargedType', 'dischargeType']));
        matched = itemOrder === 0
          ? total !== null && total < 15 && dischargeType.includes('死亡')
          : total !== null && total < 15;
      } else if (indicatorKey === 'apacheScoreRate') {
        if (itemOrder === 0) matched = Boolean(anyScore);
        else if (itemOrder === APACHE_UNSCORED_ORDER) matched = !anyScore;
        else matched = true;
      }
      if (!matched) return;
      rows.push({
        patient,
        statMonth: monthKey,
        apacheScore: scoreForDisplay ? safeNumber(scoreForDisplay.total ?? scoreForDisplay.apacheII?.totalScore) : '',
      });
    });
  }

  return rows.map((item, index) => toPatientDetailRow(item.patient, index + 1, item.statMonth, {
    apacheScore: item.apacheScore,
  }));
}

async function getPredictedMortalityRows(months, department) {
  const rows = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1, _id: 1 })
      .lean();
    const { selectedScoreByPid } = await getApacheScoreMaps(patients, startDate, endDate);
    patients.forEach(patient => {
      const score = selectedScoreByPid.get(String(patient._id));
      if (!score) return;
      rows.push({
        patient,
        statMonth: monthKey,
        apacheScore: safeNumber(score.total ?? score.apacheII?.totalScore),
        predictedMortality: trimTrailingZeros((safeNumber(score.apacheII?.calDead?.score) * 1000).toFixed(2)),
      });
    });
  }
  return rows.map((item, index) => toPatientDetailRow(item.patient, index + 1, item.statMonth, {
    apacheScore: item.apacheScore,
    predictedMortality: item.predictedMortality,
  }));
}

async function getDeathPatientRows(months, department) {
  const rows = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1, _id: 1 })
      .lean();
    rows.push(...patients
      .filter(patient => String(firstValue(patient, ['dischargedType', 'dischargeType'])).includes('死亡'))
      .map(patient => ({ patient, statMonth: monthKey })));
  }
  return patientEntriesToRows(rows);
}

async function getPatientsByMrns(mrns, months, department, extraByMrn = new Map()) {
  const rows = [];
  const mrnList = [...new Set(mrns.filter(Boolean))];
  if (!mrnList.length) return rows;
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .where('mrn').in(mrnList)
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1, _id: 1 })
      .lean();
    rows.push(...patients.map(patient => ({
      patient,
      statMonth: monthKey,
      extra: extraByMrn.get(patient.mrn) || {},
    })));
  }
  return rows.map((item, index) => toPatientDetailRow(item.patient, index + 1, item.statMonth, item.extra));
}

async function getQualityDataPatientRows(months, department, indicatorCode, indicatorName) {
  const keysByMonth = new Map();
  for (const monthKey of months) {
    const doc = await getQualityDataDoc(monthKey, indicatorCode, indicatorName);
    const mrns = [];
    const hisPids = [];
    (doc?.qualityItemList || []).forEach(item => {
      if (item.mrn) mrns.push(String(item.mrn));
      if (item.hisPid) hisPids.push(String(item.hisPid));
    });
    keysByMonth.set(monthKey, { mrns: [...new Set(mrns)], hisPids: [...new Set(hisPids)] });
  }
  const rows = [];
  for (const monthKey of months) {
    const keys = keysByMonth.get(monthKey) || { mrns: [], hisPids: [] };
    if (!keys.mrns.length && !keys.hisPids.length) continue;
    const { startDate, endDate } = getMonthRange(monthKey);
    const identityFilter = keys.hisPids.length
      ? { hisPid: { $in: keys.hisPids } }
      : { mrn: { $in: keys.mrns } };
    const patients = await Patient.find({
      $and: [
        buildMonthlyOverlapFilter(startDate, endDate, department),
        identityFilter,
      ],
    })
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1, _id: 1 })
      .lean();
    const seen = new Set();
    patients.forEach(patient => {
      const identity = keys.hisPids.length ? patient.hisPid : patient.mrn;
      if (seen.has(identity)) return;
      seen.add(identity);
      rows.push({ patient, statMonth: monthKey });
    });
  }
  return patientEntriesToRows(rows);
}

async function getDvtPatientSets(months, department) {
  const DVT_DEVICE = ['肢体气压治疗', '梯度压力弹力袜', '腔静脉滤器'];
  const DVT_HEPARIN = ['低分子肝素钠', '低分子肝素钙', '那屈肝素', '依诺肝素', '达肝素钠注射液'];
  const DVT_RIVA = ['利伐沙班'];
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean();
    const mrns = patients.map(patient => patient.mrn).filter(Boolean);
    const orders = await Order.find({
      mrn: { $in: mrns },
      orderTime: { $lte: endDate },
      orderName: { $not: /撤销/ },
      $or: [
        ...DVT_DEVICE.map(keyword => ({ orderName: new RegExp(escapeOrderRegExp(keyword)) })),
        ...DVT_HEPARIN.map(keyword => ({ orderName: new RegExp(escapeOrderRegExp(keyword)), exeMethod: '皮下注射' })),
        ...DVT_RIVA.map(keyword => ({ orderName: new RegExp(escapeOrderRegExp(keyword)), exeMethod: { $in: ['口服', '胃管置管术注药'] } })),
      ],
    }).select('mrn').lean();
    const hit = new Set(orders.map(order => order.mrn));
    patients.forEach(patient => {
      denominator.push({ patient, statMonth: monthKey });
      (hit.has(patient.mrn) ? done : notDone).push({ patient, statMonth: monthKey });
    });
  }
  return { denominator, done, notDone };
}

function patientEntriesToRows(entries, extraFactory = () => ({})) {
  return entries.map((item, index) => toPatientDetailRow(item.patient, index + 1, item.statMonth, extraFactory(item)));
}

async function getOrderPairPatientSets(months, department, denominatorKeyword, numeratorKeyword) {
  const denominatorMrns = [];
  const doneMrns = [];
  const notDoneMrns = [];
  for (const monthKey of months) {
    const denominatorOrders = await Order.find(buildOrderQuery(monthKey, denominatorKeyword)).select('mrn').lean();
    const denom = [...new Set(denominatorOrders.map(order => order.mrn).filter(Boolean))];
    const numeratorOrders = await Order.find({
      ...buildOrderQuery(monthKey, numeratorKeyword),
      mrn: { $in: denom },
    }).select('mrn').lean();
    const done = new Set(numeratorOrders.map(order => order.mrn).filter(Boolean));
    denominatorMrns.push(...denom);
    doneMrns.push(...[...done]);
    notDoneMrns.push(...denom.filter(mrn => !done.has(mrn)));
  }
  return {
    denominator: await getPatientsByMrns(denominatorMrns, months, department),
    done: await getPatientsByMrns(doneMrns, months, department),
    notDone: await getPatientsByMrns(notDoneMrns, months, department),
  };
}

async function getIcuOrderHitSets(months, department, keyword) {
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean();
    const mrns = patients.map(patient => patient.mrn).filter(Boolean);
    const orders = await Order.find({
      ...buildOrderQuery(monthKey, keyword),
      mrn: { $in: mrns },
    }).select('mrn orderTime').lean();
    const matched = buildPatientEntriesByOrderMatch(patients, orders, endDate);
    denominator.push(...matched.denominator.map(patient => ({ patient, statMonth: monthKey })));
    done.push(...matched.done.map(patient => ({ patient, statMonth: monthKey })));
    notDone.push(...matched.notDone.map(patient => ({ patient, statMonth: monthKey })));
  }
  return {
    denominator: patientEntriesToRows(denominator),
    done: patientEntriesToRows(done),
    notDone: patientEntriesToRows(notDone),
  };
}

async function getEN48hSets(months, department) {
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = (await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean()).filter(patient => {
      const admission = asDate(patient.icuAdmissionTime);
      const discharge = asDate(patient.icuDischargeTime) || endDate;
      return admission && discharge - admission >= 48 * 3600 * 1000;
    });
    const mrns = patients.map(patient => patient.mrn).filter(Boolean);
    const orders = await Order.find({
      mrn: { $in: mrns },
      orderName: { $regex: /流质饮食/, $not: /撤销/ },
    }).select('mrn orderTime').lean();
    const earliest = new Map();
    orders.forEach(order => {
      const current = earliest.get(order.mrn);
      if (!current || asDate(order.orderTime) < asDate(current.orderTime)) earliest.set(order.mrn, order);
    });
    patients.forEach(patient => {
      const order = earliest.get(patient.mrn);
      const admission = asDate(patient.icuAdmissionTime);
      const hit = order && admission && asDate(order.orderTime) - admission <= 48 * 3600 * 1000;
      denominator.push({ patient, statMonth: monthKey });
      (hit ? done : notDone).push({ patient, statMonth: monthKey });
    });
  }
  return {
    denominator: patientEntriesToRows(denominator),
    done: patientEntriesToRows(done),
    notDone: patientEntriesToRows(notDone),
  };
}

async function getExtubationSets(months, department) {
  const denominatorPids = [];
  const unplannedPids = [];
  const reintubatedPids = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const tubes = await TubeExe.find({
      type: '气插管',
      valid: { $ne: false },
      replace: { $ne: true },
      endTime: { $gte: startDate, $lte: endDate },
    }).lean();
    denominatorPids.push(...tubes.map(tube => String(tube.pid)).filter(Boolean));
    unplannedPids.push(...tubes.filter(tube => tube.unPlannedEndTube === true).map(tube => String(tube.pid)).filter(Boolean));
    const pids = [...new Set(tubes.map(tube => String(tube.pid)).filter(Boolean))];
    const histories = await TubeExe.find({
      pid: { $in: pids },
      type: '气插管',
      valid: { $ne: false },
      replace: { $ne: true },
    }).sort({ startTime: 1 }).lean();
    const historyByPid = new Map();
    histories.forEach(tube => {
      const pid = String(tube.pid);
      if (!historyByPid.has(pid)) historyByPid.set(pid, []);
      historyByPid.get(pid).push(tube);
    });
    tubes.forEach(tube => {
      const end = asDate(tube.endTime);
      const next = (historyByPid.get(String(tube.pid)) || []).find(item => asDate(item.startTime) && asDate(item.startTime) > end);
      if (
        next
        && asDate(next.startTime) - end <= 48 * 3600 * 1000
        && asDate(next.startTime) >= startDate
        && asDate(next.startTime) <= endDate
      ) {
        reintubatedPids.push(String(tube.pid));
      }
    });
  }
  return {
    denominator: await getPatientsByDetailPids(denominatorPids.map(pid => ({ pid })), months, department),
    unplanned: await getPatientsByDetailPids(unplannedPids.map(pid => ({ pid })), months, department),
    reintubated: await getPatientsByDetailPids(reintubatedPids.map(pid => ({ pid })), months, department),
  };
}

async function getIcuReturnSets(months, department) {
  const denominatorEntries = [];
  const returnedEntries = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const outPatients = await Patient.find(buildPatientFilter({
      icuDischargeTime: { $gte: startDate, $lte: endDate },
      dischargedType: /转出/,
    }, department)).select(PATIENT_SELECT).lean();
    const mrns = [...new Set(outPatients.map(patient => patient.mrn).filter(Boolean))];
    const histories = await Patient.find(buildPatientFilter({ mrn: { $in: mrns } }, department))
      .select(PATIENT_SELECT)
      .sort({ icuAdmissionTime: 1 })
      .lean();
    const byMrn = new Map();
    histories.forEach(patient => {
      if (!byMrn.has(patient.mrn)) byMrn.set(patient.mrn, []);
      byMrn.get(patient.mrn).push(patient);
    });
    outPatients.forEach(patient => {
      denominatorEntries.push({ patient, statMonth: monthKey });
      const discharge = asDate(patient.icuDischargeTime);
      const next = (byMrn.get(patient.mrn) || []).find(item => asDate(item.icuAdmissionTime) && asDate(item.icuAdmissionTime) > discharge);
      if (next && asDate(next.icuAdmissionTime) - discharge <= 48 * 3600 * 1000) {
        returnedEntries.push({ patient, statMonth: monthKey });
      }
    });
  }
  return {
    denominator: patientEntriesToRows(denominatorEntries),
    returned: patientEntriesToRows(returnedEntries),
  };
}

async function getRescuePatientSets(months, department) {
  const successMrns = [];
  const deathMrns = [];
  const terminalMrns = [];
  const denominatorMrns = [];
  const countByMrn = new Map();
  for (const monthKey of months) {
    const rescue = await calcRescueStats(monthKey);
    rescue.rescueCountByMrn.forEach((count, mrn) => countByMrn.set(mrn, safeNumber(countByMrn.get(mrn)) + count));
    const rescueMrns = [...rescue.rescueCountByMrn.keys()];
    const patients = await Patient.find(buildPatientFilter({ mrn: { $in: rescueMrns } }, department))
      .select('mrn dischargedType dischargeType')
      .lean();
    patients.forEach(patient => {
      const dischargeType = String(firstValue(patient, ['dischargedType', 'dischargeType']));
      if (dischargeType.includes('死亡（终末）')) {
        terminalMrns.push(patient.mrn);
      } else {
        denominatorMrns.push(patient.mrn);
        if (dischargeType === '死亡' || (dischargeType.includes('死亡') && !dischargeType.includes('终末'))) deathMrns.push(patient.mrn);
        else successMrns.push(patient.mrn);
      }
    });
  }
  const extraByMrn = new Map([...countByMrn.entries()].map(([mrn, count]) => [mrn, { rescueCount: count }]));
  return {
    success: await getPatientsByMrns(successMrns, months, department, extraByMrn),
    denominator: await getPatientsByMrns(denominatorMrns, months, department, extraByMrn),
    death: await getPatientsByMrns(deathMrns, months, department, extraByMrn),
    terminal: await getPatientsByMrns(terminalMrns, months, department, extraByMrn),
  };
}

async function calcOccupiedBedDayTotal(months, department) {
  const entries = await getMonthlyOccupiedPatientEntries(months, department);
  return entries.reduce((sum, item) => sum + item.occupiedDays, 0);
}

async function calcOccupiedPatientTotal(months, department) {
  const entries = await getMonthlyOccupiedPatientEntries(months, department);
  return entries.length;
}

async function getBedNumForMonth(monthKey, department) {
  const deptCode = resolveDepartmentCode(department);
  const { endDate } = getMonthRange(monthKey);
  const filter = {
    ...(deptCode ? { deptCode } : {}),
    time: { $lte: endDate },
  };
  const record = await BedRecord.findOne(filter).sort({ time: -1 }).lean();
  if (record) return safeNumber(record.bedNum);

  const fallback = await BedRecord.findOne(deptCode ? { deptCode } : {}).sort({ time: 1 }).lean();
  return safeNumber(fallback?.bedNum);
}

async function calcConfiguredBedDayTotal(months, department) {
  const values = await Promise.all(months.map(async monthKey => {
    const bedNum = await getBedNumForMonth(monthKey, department);
    const daysInMonth = moment.parseZone(`${monthKey}-01T00:00:00+08:00`).daysInMonth();
    return bedNum * daysInMonth;
  }));
  return values.reduce((sum, value) => sum + value, 0);
}

async function buildComputedSummaryRows(spec, months, startMonth, endMonth, department) {
  const basicStats = await getBasicMonthlyStats(months, department);
  const computedStats = await getComputedMonthlyStats(months, department, basicStats);
  const total = field => months.reduce((sum, monthKey) => sum + safeNumber(computedStats.get(monthKey)?.[field]), 0);
  const icuCensusTotal = months.reduce((sum, monthKey) => sum + safeNumber(basicStats.get(monthKey)?.icuCensus), 0);
  const action = order => ({
    label: '查看详情',
    target: spec.key,
    startMonth,
    endMonth,
    itemOrder: String(order),
  });
  const makeRow = (index, item, value, rowAction = null) => ({
    index,
    item,
    value: trimTrailingZeros(safeNumber(value).toFixed(2)),
    action: rowAction,
  });
  const withRatioRow = rows => prependIndicatorRatioRow(rows, spec);

  if (spec.key === 'bedUsage') {
    return withRatioRow([
      makeRow(1, '本科收治患者总床日数', total('occupiedBedDays'), action(0)),
      makeRow(2, '本科总床日数', total('configuredBedDays')),
    ]);
  }
  if (spec.key === 'avgLengthOfStay') {
    return withRatioRow([
      makeRow(1, '本科收治患者总床日数', total('occupiedBedDays'), action(0)),
      makeRow(2, '同期ICU收治患者总数', icuCensusTotal, action(1)),
    ]);
  }
  if (spec.key === 'icuAdmissionRate') {
    return withRatioRow([
      makeRow(1, '同期ICU收治患者总数', icuCensusTotal, action(0)),
      makeRow(2, '同期医院收治患者总数', total('hospitalAdmissions')),
    ]);
  }
  if (spec.key === 'icuBedDayRate') {
    return withRatioRow([
      makeRow(1, '本科收治患者总床日数', total('occupiedBedDays'), action(0)),
      makeRow(2, '同期医院患者收治总床日数', total('hospitalBedDays')),
    ]);
  }
  if (spec.key === 'antibioticCultureRate') {
    return withRatioRow([
      makeRow(1, '使用抗菌药物前病原学检验标本送检病例数', total('antibioticCultureCases')),
      makeRow(2, '同期使用抗菌药物治疗病例总数', total('antibioticTreatmentCases')),
    ]);
  }
  return [];
}

async function buildCalculatedSummaryRows(spec, months, department) {
  const basicStats = await getBasicMonthlyStats(months, department);
  const calculatedStats = await getCalculatedMonthlyStats(months, department, basicStats);
  const totalValue = getter => months.reduce((sum, monthKey) => sum + safeNumber(getter(calculatedStats.get(monthKey) || {}, basicStats.get(monthKey) || {})), 0);
  const action = order => ({
    label: '查看详情',
    target: spec.key,
    startMonth: months[0],
    endMonth: months[months.length - 1],
    itemOrder: String(order),
  });
  const makeRow = (index, item, value, rowAction = null) => ({
    index,
    item,
    value: trimTrailingZeros(safeNumber(value).toFixed(2)),
    action: rowAction,
  });
  let rows = [];

  if (spec.key === 'shockBundleRate') {
    rows = [
      makeRow(1, '入ICU诊断为感染性休克并完成感染性休克护理常规的患者数', totalValue(stats => stats.shockBundleDone), action(0)),
      makeRow(2, '同期入ICU诊断为感染性休克患者总数', totalValue(stats => stats.shockBundleDenominator), action(1)),
      makeRow(3, '未下感染性休克护理常规医嘱', totalValue(stats => stats.shockBundleNotDone), action(2)),
    ];
  } else if (spec.key === 'apacheGte15Rate') {
    rows = [
      makeRow(1, 'APACHEⅡ≥15患者数', totalValue(stats => stats.apacheGte15), action(0)),
      makeRow(2, '同期ICU收治患者总数', totalValue((_, basic) => basic.icuCensus), action(1)),
    ];
  } else if (spec.key === 'apacheLt15Rate') {
    rows = [
      makeRow(1, 'APACHEⅡ<15患者数', totalValue(stats => stats.apacheLt15), action(0)),
      makeRow(2, '同期ICU收治患者总数', totalValue((_, basic) => basic.icuCensus), action(1)),
    ];
  } else if (spec.key === 'apacheScoreRate') {
    const scored = totalValue(stats => stats.apacheScored);
    const denominator = totalValue((_, basic) => basic.icuCensus);
    rows = [
      makeRow(1, 'APACHEⅡ评分患者数', scored, action(0)),
      makeRow(2, '同期ICU收治患者总数', denominator, action(1)),
      {
        ...makeRow(3, '未评APACHEⅡ人数', Math.max(0, denominator - scored)),
        action: {
          label: '查看详情',
          target: spec.key,
          startMonth: months[0],
          endMonth: months[months.length - 1],
          itemOrder: String(APACHE_UNSCORED_ORDER),
        },
      },
    ];
  } else if (spec.key === 'predictedMortalityRate') {
    rows = [
      makeRow(1, 'ICU收治患者预计病死率总和', totalValue(stats => stats.predictedMortalitySum) * 1000, action(0)),
      makeRow(2, '同期ICU收治患者总数', totalValue((_, basic) => basic.icuCensus), action(1)),
    ];
  } else if (spec.key === 'apacheLt15DeathRate') {
    rows = [
      makeRow(1, 'APACHEⅡ<15死亡患者数', totalValue(stats => stats.apacheLt15Death), action(0)),
      makeRow(2, 'APACHEⅡ<15患者数', totalValue(stats => stats.apacheLt15), action(1)),
    ];
  } else if (spec.key === 'standardizedMortalityIndex') {
    const actualRate = totalValue(stats => stats.actualMortalityRate);
    const predictedRate = totalValue(stats => stats.predictedMortalityRate);
    rows = [
      { index: 1, item: 'ICU患者实际病死率', value: trimTrailingZeros(actualRate.toFixed(6)), action: action(0) },
      { index: 2, item: '同期ICU患者预计病死率', value: trimTrailingZeros(predictedRate.toFixed(6)), action: action(1) },
    ];
  } else if (spec.key === 'unplannedExtubationRate') {
    rows = [
      makeRow(1, '非计划气管插管拔管例数', totalValue(stats => stats.unplannedExtubationNum), action(0)),
      makeRow(2, '同期ICU患者气管插管拔管总数', totalValue(stats => stats.extubationDenominator), action(1)),
    ];
  } else if (spec.key === 'reintubation48hRate') {
    rows = [
      makeRow(1, '气管插管计划拔管后48h内再插管例数', totalValue(stats => stats.reintubationNum), action(0)),
      makeRow(2, '同期ICU患者气管插管拔管总例数', totalValue(stats => stats.extubationDenominator), action(1)),
    ];
  } else if (spec.key === 'unplannedIcuTransferRate') {
    rows = [
      makeRow(1, '非计划转入ICU患者数', totalValue(stats => stats.unplannedIcuTransferNum), action(0)),
      makeRow(2, '同期ICU收治患者总数', totalValue((_, basic) => basic.icuCensus), action(1)),
    ];
  } else if (spec.key === 'icuReturn48hRate') {
    rows = [
      makeRow(1, '转出ICU后48h内重返患者数', totalValue(stats => stats.icuReturn48hNum), action(0)),
      makeRow(2, '转出ICU患者总数', totalValue(stats => stats.icuReturnDenominator), action(1)),
    ];
  } else if (spec.key === 'shockUltrasoundRate') {
    rows = [
      makeRow(1, '完成超声筛查评估休克患者数', totalValue(stats => stats.shockUltrasoundNum), action(0)),
      makeRow(2, '休克患者总数', totalValue(stats => stats.shockDenominator), action(1)),
      makeRow(3, '休克病人未做（床旁B超检查及监测）项目的人数', Math.max(0, totalValue(stats => stats.shockDenominator) - totalValue(stats => stats.shockUltrasoundNum)), action(2)),
    ];
  } else if (spec.key === 'shockHemodynamicRate') {
    rows = [
      makeRow(1, '完成血流动力学指标监测休克患者数', totalValue(stats => stats.shockHemodynamicNum), action(0)),
      makeRow(2, '休克患者总数', totalValue(stats => stats.shockDenominator), action(1)),
      makeRow(3, '休克病人未做（测中心静脉压、PICCO监测）项目的人数', Math.max(0, totalValue(stats => stats.shockDenominator) - totalValue(stats => stats.shockHemodynamicNum)), action(2)),
    ];
  } else if (spec.key === 'ardsRate') {
    rows = [
      makeRow(1, '俯卧位通气患者数', totalValue(stats => stats.ardsNum), action(0)),
      makeRow(2, '急性呼吸窘迫综合征（ARDS）患者总数', totalValue(stats => stats.ardsDenominator), action(1)),
      makeRow(3, '未做俯卧位通气患者数', Math.max(0, totalValue(stats => stats.ardsDenominator) - totalValue(stats => stats.ardsNum)), action(2)),
    ];
  } else if (spec.key === 'rescueSuccessRate') {
    rows = [
      makeRow(1, '抢救成功例数', totalValue(stats => stats.rescueSuccess), action(0)),
      makeRow(2, '抢救总例数', totalValue(stats => stats.rescueDenominator), action(1)),
      makeRow(3, '抢救死亡人数', totalValue(stats => stats.rescueDeath), action(2)),
      makeRow(4, '死亡（终末）人数', totalValue(stats => stats.rescueTerminal), action(3)),
    ];
  }

  return prependIndicatorRatioRow(rows, spec);
}

async function buildSetBasedSummaryRows(spec, months, startMonth, endMonth, department) {
  const action = order => ({
    label: '查看详情',
    target: spec.key,
    startMonth,
    endMonth,
    itemOrder: String(order),
  });
  const makeRow = (index, item, value, rowAction = null) => ({
    index,
    item,
    value: trimTrailingZeros(safeNumber(value).toFixed(2)),
    action: rowAction,
  });
  let rows = [];

  if (spec.key === 'dvtRate') {
    const sets = await getDvtPatientSets(months, department);
    rows = [
      makeRow(1, '进行深静脉血栓(DVT)预防的ICU患者数', sets.done.length, action(0)),
      makeRow(2, '同期ICU收治患者总数', sets.denominator.length, action(1)),
      makeRow(3, '未进行深静脉血栓(DVT)预防的ICU患者数', sets.notDone.length, action(2)),
    ];
  } else if (spec.key === 'en48hRate') {
    const sets = await getEN48hSets(months, department);
    rows = [
      makeRow(1, '入科后48h内启动EN的患者人数', sets.done.length, action(0)),
      makeRow(2, '入科超过48h的ICU患者人数', sets.denominator.length, action(1)),
      makeRow(3, '入科后48h内未启动EN的患者人数', sets.notDone.length, action(2)),
    ];
  } else if (spec.key === 'painRate') {
    const sets = await getIcuOrderHitSets(months, department, '镇痛评估');
    rows = [
      makeRow(1, '进行镇痛评估患者人数', sets.done.length, action(0)),
      makeRow(2, '同期ICU收治患者总数', sets.denominator.length, action(1)),
      makeRow(3, '未进行镇痛评估患者人数', sets.notDone.length, action(2)),
    ];
  } else if (spec.key === 'sedationRate') {
    const sets = await getIcuOrderHitSets(months, department, '镇静评估');
    rows = [
      makeRow(1, '进行镇静评估患者人数', sets.done.length, action(0)),
      makeRow(2, '同期ICU收治患者总数', sets.denominator.length, action(1)),
      makeRow(3, '未进行镇静评估患者人数', sets.notDone.length, action(2)),
    ];
  } else if (spec.key === 'acuteBrainInjuryRate') {
    const sets = await getOrderPairPatientSets(months, department, '急性脑损伤护理常规', '格拉斯哥昏迷评分');
    rows = [
      makeRow(1, 'ICU内完成意识评估的急性脑损伤患者人数', sets.done.length, action(0)),
      makeRow(2, '急性脑损伤患者总数', sets.denominator.length, action(1)),
      makeRow(3, 'ICU内未完成意识评估的急性脑损伤患者人数', sets.notDone.length, action(2)),
    ];
  }
  return prependIndicatorRatioRow(rows, spec);
}

function prependIndicatorRatioRow(rows, spec) {
  if (!rows.length) return rows;
  const numerator = spec.key === 'predictedMortalityRate'
    ? safeNumber(rows[0]?.value) / 1000
    : safeNumber(rows[0]?.value);
  const denominator = safeNumber(rows[1]?.value);
  const ratioRow = {
    index: 1,
    item: spec.name,
    value: formatComputedRatio(spec, numerator, denominator),
    action: null,
  };
  return [ratioRow, ...rows.map((row, index) => ({ ...row, index: index + 2 }))];
}

function appendComplementRows(summaryRows, spec, startMonth, endMonth) {
  const action = order => ({
    label: '查看详情',
    target: spec.key,
    startMonth,
    endMonth,
    itemOrder: String(order),
  });
  const numeric = index => safeNumber(summaryRows[index]?.value);
  const append = (item, value, order) => {
    summaryRows.push({
      index: summaryRows.length + 1,
      item,
      value: trimTrailingZeros(Math.max(0, value).toFixed(2)),
      action: action(order),
    });
  };

  if (spec.key === 'dvtRate' && summaryRows.length >= 2) {
    append('未进行深静脉血栓(DVT)预防的ICU患者数', numeric(1) - numeric(0), 2);
  } else if (spec.key === 'en48hRate' && summaryRows.length >= 2) {
    append('入科后48h内未启动EN的患者人数', numeric(1) - numeric(0), 2);
  } else if (spec.key === 'painRate' && summaryRows.length >= 2) {
    append('未进行镇痛评估患者人数', numeric(1) - numeric(0), 2);
  } else if (spec.key === 'sedationRate' && summaryRows.length >= 2) {
    append('未进行镇静评估患者人数', numeric(1) - numeric(0), 2);
  } else if (spec.key === 'acuteBrainInjuryRate' && summaryRows.length >= 2) {
    append('ICU内未完成意识评估的急性脑损伤患者人数', numeric(1) - numeric(0), 2);
  }
  return summaryRows;
}

async function getPatientsByDetailPids(detailRows, months, department, options = {}) {
  const pidSet = [...new Set(detailRows.map(item => String(item.pid)).filter(Boolean))];
  if (!pidSet.length) return [];
  const patients = await Patient.find(buildPatientFilter({ _id: { $in: pidSet } }, department))
    .select(PATIENT_SELECT)
    .lean();
  const patientById = new Map(patients.map(patient => [String(patient._id), patient]));
  const statMonth = months.length === 1 ? months[0] : `${months[0]}至${months[months.length - 1]}`;
  let apacheScoreByPid = new Map();
  if (options.includeApacheScore && patients.length) {
    const { startDate } = getMonthRange(months[0]);
    const { endDate } = getMonthRange(months[months.length - 1]);
    const { anyScoreByPid, selectedScoreByPid } = await getApacheScoreMaps(patients, startDate, endDate);
    apacheScoreByPid = new Map();
    patients.forEach(patient => {
      const pid = String(patient._id);
      apacheScoreByPid.set(pid, selectedScoreByPid.get(pid) || anyScoreByPid.get(pid) || null);
    });
  }
  return detailRows
    .map((detail, index) => {
      const patient = patientById.get(String(detail.pid));
      if (!patient) return null;
      const apacheScore = apacheScoreByPid.get(String(patient._id));
      const extra = options.includeApacheScore
        ? { apacheScore: apacheScore ? safeNumber(apacheScore.total ?? apacheScore.apacheII?.totalScore) : '' }
        : {};
      return toPatientDetailRow(patient, index + 1, statMonth, extra);
    })
    .filter(Boolean);
}

async function getQualityDetail(indicatorKey, params) {
  const spec = SPEC_BY_KEY.get(indicatorKey);
  if (!spec) throw new Error('质控指标不支持');
  const { months, startMonth, endMonth } = getRangeFromQuery(params);
  const department = params.department || '';
  const itemOrder = params.itemOrder === undefined ? null : Number(params.itemOrder);
  const [docs, qcDocs] = await Promise.all([
    fetchQualityDocs(months, department),
    fetchQualityQcDocs(months, department),
  ]);
  let indicatorDocs = docs.filter(doc => doc.indicatorCode === spec.code);
  let items = await fetchItemsByQualityIds(indicatorDocs.map(doc => String(doc._id)));
  let detailRows = await fetchDetailRowsByItemIds(items.map(item => String(item._id)));

  if ((!indicatorDocs.length || !items.length) && spec.newCode) {
    const qcIndicatorDocs = qcDocs.filter(doc => doc.indicatorCode === spec.newCode);
    const qcItems = await fetchQcItemsByQualityIds(qcIndicatorDocs.map(doc => String(doc._id)));
    if (qcIndicatorDocs.length && qcItems.length) {
      indicatorDocs = qcIndicatorDocs;
      items = qcItems;
      detailRows = await fetchQcDetailRowsByItemIds(items.map(item => String(item._id)));
    }
  }

  const itemsByQualityId = buildItemsByQualityId(items);
  const detailsByItemId = buildDetailsByItemId(detailRows);

  if (COMPUTED_RATIO_KEYS.has(spec.key)) {
    if (itemOrder !== null && !Number.isNaN(itemOrder)) {
      if (
        (['bedUsage', 'avgLengthOfStay', 'icuBedDayRate'].includes(spec.key) && itemOrder === 0)
      ) {
        const rows = await getOccupiedBedDayRows(months, department);
        return { indicator: { key: spec.key, name: spec.name }, columns: OCCUPIED_BED_DAY_COLUMNS, rows };
      }
      if (
        (spec.key === 'avgLengthOfStay' && itemOrder === 1)
        || (spec.key === 'icuAdmissionRate' && itemOrder === 0)
      ) {
        const rows = await getBasicPatientRows('icuCensus', months, department);
        return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows };
      }
      return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows: [] };
    }

    const rows = await buildComputedSummaryRows(spec, months, startMonth, endMonth, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: SUMMARY_COLUMNS,
      rows,
    };
  }

  if (APACHE_DETAIL_KEYS.has(spec.key) && itemOrder === null) {
    const rows = await buildCalculatedSummaryRows(spec, months, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: SUMMARY_COLUMNS,
      rows,
    };
  }

  if ([
    'predictedMortalityRate',
    'standardizedMortalityIndex',
    'unplannedExtubationRate',
    'reintubation48hRate',
    'unplannedIcuTransferRate',
    'icuReturn48hRate',
    'shockBundleRate',
    'shockUltrasoundRate',
    'shockHemodynamicRate',
    'ardsRate',
    'rescueSuccessRate',
    'apacheLt15DeathRate',
  ].includes(spec.key) && itemOrder === null) {
    const rows = await buildCalculatedSummaryRows(spec, months, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: SUMMARY_COLUMNS,
      rows,
    };
  }

  if (['dvtRate', 'en48hRate', 'painRate', 'sedationRate', 'acuteBrainInjuryRate'].includes(spec.key) && itemOrder === null) {
    const rows = await buildSetBasedSummaryRows(spec, months, startMonth, endMonth, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: SUMMARY_COLUMNS,
      rows,
    };
  }

  if (CALCULATED_FALLBACK_KEYS.has(spec.key) && !items.length && itemOrder === null) {
    const rows = await buildCalculatedSummaryRows(spec, months, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: SUMMARY_COLUMNS,
      rows,
    };
  }

  if (APACHE_DETAIL_KEYS.has(spec.key) && itemOrder !== null && !Number.isNaN(itemOrder)) {
    const rows = await getApachePatientRows(spec.key, itemOrder, months, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: itemOrder === APACHE_UNSCORED_ORDER ? PATIENT_DETAIL_COLUMNS : APACHE_DETAIL_COLUMNS,
      rows,
    };
  }

  if (spec.key === 'apacheLt15DeathRate' && itemOrder !== null && !Number.isNaN(itemOrder)) {
    const rows = await getApachePatientRows(spec.key, itemOrder, months, department);
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: APACHE_DETAIL_COLUMNS,
      rows,
    };
  }

  if (CALCULATED_FALLBACK_KEYS.has(spec.key) && itemOrder !== null && !Number.isNaN(itemOrder)) {
    let rows = [];
    let columns = PATIENT_DETAIL_COLUMNS;
    if (spec.key === 'predictedMortalityRate') {
      rows = itemOrder === 0 ? await getPredictedMortalityRows(months, department) : await getBasicPatientRows('icuCensus', months, department);
      columns = itemOrder === 0 ? MORTALITY_DETAIL_COLUMNS : PATIENT_DETAIL_COLUMNS;
    } else if (spec.key === 'standardizedMortalityIndex') {
      rows = itemOrder === 0 ? await getDeathPatientRows(months, department) : await getPredictedMortalityRows(months, department);
      columns = itemOrder === 1 ? MORTALITY_DETAIL_COLUMNS : PATIENT_DETAIL_COLUMNS;
    } else if (spec.key === 'unplannedIcuTransferRate') {
      rows = itemOrder === 0
        ? await getQualityDataPatientRows(months, department, 'NoPlanInICUNum', '非计划转入ICU患者数')
        : await getBasicPatientRows('icuCensus', months, department);
    } else if (spec.key === 'shockBundleRate') {
      const sets = await getOrderPairPatientSets(months, department, '感染性休克护理常规', '感染性休克患者集束化治疗');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'shockUltrasoundRate') {
      const sets = await getOrderPairPatientSets(months, department, '休克护理常规', '重症超声筛查评估');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'shockHemodynamicRate') {
      const sets = await getOrderPairPatientSets(months, department, '休克护理常规', 'CVP');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'ardsRate') {
      const sets = await getOrderPairPatientSets(months, department, '中重度ARDS护理常规', '俯卧位通气');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'rescueSuccessRate') {
      const sets = await getRescuePatientSets(months, department);
      rows = itemOrder === 0 ? sets.success : itemOrder === 1 ? sets.denominator : itemOrder === 2 ? sets.death : sets.terminal;
      columns = RESCUE_DETAIL_COLUMNS;
    }
    if (rows.length || ['predictedMortalityRate', 'standardizedMortalityIndex', 'unplannedIcuTransferRate', 'shockBundleRate', 'shockUltrasoundRate', 'shockHemodynamicRate', 'ardsRate', 'rescueSuccessRate'].includes(spec.key)) {
      return { indicator: { key: spec.key, name: spec.name }, columns, rows };
    }
  }

  if (isOccupiedBedDayIndicator(spec) && (itemOrder === 0 || (spec.key === 'avgLengthOfStay' && itemOrder === 1))) {
    const rows = await getOccupiedBedDayRows(months, department);
    return { indicator: { key: spec.key, name: spec.name }, columns: OCCUPIED_BED_DAY_COLUMNS, rows };
  }

  if (itemOrder !== null && !Number.isNaN(itemOrder) && ['dvtRate', 'en48hRate', 'painRate', 'sedationRate', 'acuteBrainInjuryRate'].includes(spec.key)) {
    let rows = [];
    if (spec.key === 'dvtRate') {
      const sets = await getDvtPatientSets(months, department);
      rows = patientEntriesToRows(itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone);
    } else if (spec.key === 'en48hRate') {
      const sets = await getEN48hSets(months, department);
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'painRate') {
      const sets = await getIcuOrderHitSets(months, department, '镇痛评估');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'sedationRate') {
      const sets = await getIcuOrderHitSets(months, department, '镇静评估');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    } else if (spec.key === 'acuteBrainInjuryRate') {
      const sets = await getOrderPairPatientSets(months, department, '急性脑损伤护理常规', '格拉斯哥昏迷评分');
      rows = itemOrder === 0 ? sets.done : itemOrder === 1 ? sets.denominator : sets.notDone;
    }
    return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows };
  }

  if (itemOrder !== null && !Number.isNaN(itemOrder) && ['unplannedExtubationRate', 'reintubation48hRate', 'icuReturn48hRate'].includes(spec.key)) {
    let rows = [];
    if (spec.key === 'unplannedExtubationRate') {
      const sets = await getExtubationSets(months, department);
      rows = itemOrder === 0 ? sets.unplanned : sets.denominator;
    } else if (spec.key === 'reintubation48hRate') {
      const sets = await getExtubationSets(months, department);
      rows = itemOrder === 0 ? sets.reintubated : sets.denominator;
    } else if (spec.key === 'icuReturn48hRate') {
      const sets = await getIcuReturnSets(months, department);
      rows = itemOrder === 0 ? sets.returned : sets.denominator;
    }
    return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows };
  }

  if (spec.type === 'count') {
    const firstItems = indicatorDocs.flatMap(doc => (itemsByQualityId.get(String(doc._id)) || []).filter(item => safeNumber(item.order) === 0));
    const firstDetailRows = firstItems.flatMap(item => detailsByItemId.get(String(item._id)) || []);
    if (!firstDetailRows.length && (spec.key === 'newAdmissions' || spec.key === 'icuCensus')) {
      const rows = await getBasicPatientRows(spec.key, months, department);
      return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows };
    }
    const rows = await getPatientsByDetailPids(firstDetailRows, months, department);
    return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows };
  }

  if (itemOrder !== null && !Number.isNaN(itemOrder)) {
    const matchedItems = indicatorDocs.flatMap(doc => (itemsByQualityId.get(String(doc._id)) || []).filter(item => safeNumber(item.order) === itemOrder));
    const matchedDetailRows = matchedItems.flatMap(item => detailsByItemId.get(String(item._id)) || []);
    const includeApacheScore = APACHE_DETAIL_KEYS.has(spec.key);
    const rows = await getPatientsByDetailPids(matchedDetailRows, months, department, { includeApacheScore });
    return {
      indicator: { key: spec.key, name: spec.name },
      columns: includeApacheScore ? APACHE_DETAIL_COLUMNS : PATIENT_DETAIL_COLUMNS,
      rows,
    };
  }

  const summaryRows = [];
  const itemOrderSet = [...new Set(items.map(item => safeNumber(item.order)))].sort((a, b) => a - b);
  const summaryOrders = itemOrderSet.length ? itemOrderSet : (isOccupiedBedDayIndicator(spec) ? [0, 1] : []);
  let occupiedBedDayTotal = null;
  let occupiedPatientTotal = null;
  let configuredBedDayTotal = null;
  let icuCensusTotal = null;
  for (const [index, order] of summaryOrders.entries()) {
    const matchedItems = indicatorDocs.flatMap(doc => (itemsByQualityId.get(String(doc._id)) || []).filter(item => safeNumber(item.order) === order));
    const isOccupiedNumerator = isOccupiedBedDayIndicator(spec) && order === 0;
    const isBedUsageDenominator = spec.key === 'bedUsage' && order === 1;
    const isAvgLengthDenominator = spec.key === 'avgLengthOfStay' && order === 1;
    const rawItemName = matchedItems[0]?.itemName || matchedItems[0]?.itemCode || `明细项${order + 1}`;
    const isIcuCensusDenominator = isIcuCensusDenominatorItem(spec, order, rawItemName);
    const itemName = isOccupiedNumerator
      ? '本科收治患者总床日数'
      : isBedUsageDenominator
        ? '本科总床日数'
        : isIcuCensusDenominator
          ? '同期ICU收治患者总数'
          : rawItemName;
    let itemValue = matchedItems.reduce((sum, item) => sum + safeNumber(item.itemData), 0);
    if (isOccupiedNumerator) {
      occupiedBedDayTotal ??= await calcOccupiedBedDayTotal(months, department);
      itemValue = occupiedBedDayTotal;
    } else if (isBedUsageDenominator && !itemValue) {
      configuredBedDayTotal ??= await calcConfiguredBedDayTotal(months, department);
      itemValue = configuredBedDayTotal;
    } else if (isAvgLengthDenominator) {
      occupiedPatientTotal ??= await calcOccupiedPatientTotal(months, department);
      itemValue = occupiedPatientTotal;
    } else if (isIcuCensusDenominator) {
      const basicStats = await getBasicMonthlyStats(months, department);
      icuCensusTotal ??= months.reduce((sum, monthKey) => sum + safeNumber(basicStats.get(monthKey)?.icuCensus), 0);
      itemValue = icuCensusTotal;
    }
    const details = matchedItems.flatMap(item => detailsByItemId.get(String(item._id)) || []);
    const hasCalculatedDetail = ['unplannedExtubationRate', 'reintubation48hRate', 'icuReturn48hRate'].includes(spec.key);
    const hasDetailAction = isOccupiedNumerator || isAvgLengthDenominator || hasCalculatedDetail || (!isBedUsageDenominator && details.length);
    summaryRows.push({
      index: index + 1,
      item: itemName,
      value: trimTrailingZeros(itemValue.toFixed(2)),
      action: hasDetailAction ? {
        label: '查看详情',
        target: isIcuCensusDenominator ? 'icuCensus' : spec.key,
        startMonth,
        endMonth,
        itemOrder: isIcuCensusDenominator ? undefined : String(order),
      } : null,
    });
  }

  if (spec.key === 'apacheScoreRate' && summaryRows.length >= 2) {
    const scored = safeNumber(summaryRows[0].value);
    const denominator = safeNumber(summaryRows[1].value);
    summaryRows.push({
      index: summaryRows.length + 1,
      item: '未评APACHEⅡ人数',
      value: trimTrailingZeros(Math.max(0, denominator - scored).toFixed(2)),
      action: {
        label: '查看详情',
        target: spec.key,
        startMonth,
        endMonth,
        itemOrder: String(APACHE_UNSCORED_ORDER),
      },
    });
  }

  appendComplementRows(summaryRows, spec, startMonth, endMonth);

  if (!summaryRows.length) {
    return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows: [] };
  }

  return {
    indicator: { key: spec.key, name: spec.name },
    columns: SUMMARY_COLUMNS,
    rows: prependIndicatorRatioRow(summaryRows, spec),
  };
}

async function calcOrderPair(monthKey, department, denominatorKeyword, numeratorKeyword) {
  const { startDate, endDate } = getMonthRange(monthKey);
  const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
    .select(PATIENT_SELECT)
    .lean();
  const sets = await getOrderPairEntriesForMonth(patients, endDate, denominatorKeyword, numeratorKeyword);
  return {
    numerator: sets.done.length,
    denominator: sets.denominator.length,
  };
}

async function calcShockBundleStats(monthKey, department) {
  const { startDate, endDate } = getMonthRange(monthKey);
  const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
    .select(PATIENT_SELECT)
    .lean();
  const sets = await getOrderPairEntriesForMonth(patients, endDate, '感染性休克护理常规', '感染性休克患者集束化治疗');
  return {
    done: sets.done.length,
    denominator: sets.denominator.length,
    notDone: sets.notDone.length,
  };
}

async function getDvtPatientSets(months, department) {
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean();
    const matched = await getMatchedPatientEntriesByOrderFilter(patients, buildDvtOrderFilter(endDate), endDate);
    denominator.push(...matched.denominator.map(patient => ({ patient, statMonth: monthKey })));
    done.push(...matched.done.map(patient => ({ patient, statMonth: monthKey })));
    notDone.push(...matched.notDone.map(patient => ({ patient, statMonth: monthKey })));
  }
  return { denominator, done, notDone };
}

async function getOrderPairPatientSets(months, department, denominatorKeyword, numeratorKeyword) {
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean();
    const matched = await getOrderPairEntriesForMonth(patients, endDate, denominatorKeyword, numeratorKeyword);
    denominator.push(...matched.denominator.map(patient => ({ patient, statMonth: monthKey })));
    done.push(...matched.done.map(patient => ({ patient, statMonth: monthKey })));
    notDone.push(...matched.notDone.map(patient => ({ patient, statMonth: monthKey })));
  }
  return {
    denominator: patientEntriesToRows(denominator),
    done: patientEntriesToRows(done),
    notDone: patientEntriesToRows(notDone),
  };
}

async function getIcuOrderHitSets(months, department, keyword) {
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean();
    const matched = await getMatchedPatientEntriesByOrderFilter(
      patients,
      buildOrderUpToEndQuery(endDate, keyword),
      endDate,
    );
    denominator.push(...matched.denominator.map(patient => ({ patient, statMonth: monthKey })));
    done.push(...matched.done.map(patient => ({ patient, statMonth: monthKey })));
    notDone.push(...matched.notDone.map(patient => ({ patient, statMonth: monthKey })));
  }
  return {
    denominator: patientEntriesToRows(denominator),
    done: patientEntriesToRows(done),
    notDone: patientEntriesToRows(notDone),
  };
}

async function getEN48hSets(months, department) {
  const denominator = [];
  const done = [];
  const notDone = [];
  for (const monthKey of months) {
    const { startDate, endDate } = getMonthRange(monthKey);
    const patients = (await Patient.find(buildMonthlyOverlapFilter(startDate, endDate, department))
      .select(PATIENT_SELECT)
      .lean()).filter(patient => {
      const admission = asDate(patient.icuAdmissionTime);
      const discharge = asDate(patient.icuDischargeTime) || endDate;
      return admission && discharge - admission >= 48 * 3600 * 1000;
    });
    const matched = await getEn48hEntriesForMonth(patients, endDate);
    denominator.push(...matched.denominator.map(patient => ({ patient, statMonth: monthKey })));
    done.push(...matched.done.map(patient => ({ patient, statMonth: monthKey })));
    notDone.push(...matched.notDone.map(patient => ({ patient, statMonth: monthKey })));
  }
  return {
    denominator: patientEntriesToRows(denominator),
    done: patientEntriesToRows(done),
    notDone: patientEntriesToRows(notDone),
  };
}

module.exports = {
  QUALITY_SPECS,
  getQualityStats,
  getQualityDetail,
};
