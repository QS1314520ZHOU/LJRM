const moment = require('moment');
const Patient = require('../models/Patient');
const DoctorQuality = require('../models/DoctorQuality');
const DoctorQualityItem = require('../models/DoctorQualityItem');
const DoctorQualityItemDetail = require('../models/DoctorQualityItemDetail');
const DoctorQC = require('../models/DoctorQC');
const DoctorQCIData = require('../models/DoctorQCIData');
const DoctorQCIDetail = require('../models/DoctorQCIDetail');

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
  return `${Math.max(1, moment(end).utcOffset(EAST_8_OFFSET_MINUTES).diff(moment(start).utcOffset(EAST_8_OFFSET_MINUTES), 'days') + 1)}天`;
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

function buildIndicatorRow(spec, months, monthDocMap, itemsByQualityId) {
  const monthDocs = months.map(monthKey => monthDocMap.get(monthKey)).filter(Boolean);
  const numeratorTotal = aggregateItemValue(monthDocs, itemsByQualityId, 0);
  const denominatorTotal = aggregateItemValue(monthDocs, itemsByQualityId, 1);
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
  const itemsByQualityId = buildItemsByQualityId(items);
  const qcItemsByQualityId = buildItemsByQualityId(qcItems);

  const rows = QUALITY_SPECS.map(spec => {
    if (spec.key === 'newAdmissions' || spec.key === 'icuCensus') {
      return buildBasicCountRow(spec, months, basicStats);
    }
    const monthDocMap = buildMonthDocMap(docs.filter(doc => doc.indicatorCode === spec.code));
    const qcMonthDocMap = buildMonthDocMap(qcDocs.filter(doc => doc.indicatorCode === spec.newCode));
    const mergedMonthDocMap = new Map();
    months.forEach(monthKey => {
      mergedMonthDocMap.set(monthKey, monthDocMap.get(monthKey) || qcMonthDocMap.get(monthKey));
    });
    return buildIndicatorRow(spec, months, mergedMonthDocMap, new Map([
      ...itemsByQualityId,
      ...qcItemsByQualityId,
    ]));
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

async function getPatientsByDetailPids(detailRows, months, department) {
  const pidSet = [...new Set(detailRows.map(item => String(item.pid)).filter(Boolean))];
  if (!pidSet.length) return [];
  const patients = await Patient.find(buildPatientFilter({ _id: { $in: pidSet } }, department))
    .select(PATIENT_SELECT)
    .lean();
  const patientById = new Map(patients.map(patient => [String(patient._id), patient]));
  const statMonth = months.length === 1 ? months[0] : `${months[0]}至${months[months.length - 1]}`;
  return detailRows
    .map((detail, index) => {
      const patient = patientById.get(String(detail.pid));
      if (!patient) return null;
      return toPatientDetailRow(patient, index + 1, statMonth);
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

  if (!indicatorDocs.length && spec.newCode) {
    indicatorDocs = qcDocs.filter(doc => doc.indicatorCode === spec.newCode);
    items = await fetchQcItemsByQualityIds(indicatorDocs.map(doc => String(doc._id)));
    detailRows = await fetchQcDetailRowsByItemIds(items.map(item => String(item._id)));
  }

  const itemsByQualityId = buildItemsByQualityId(items);
  const detailsByItemId = buildDetailsByItemId(detailRows);

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
    const rows = await getPatientsByDetailPids(matchedDetailRows, months, department);
    return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows };
  }

  const summaryRows = [];
  const itemOrderSet = [...new Set(items.map(item => safeNumber(item.order)))].sort((a, b) => a - b);
  itemOrderSet.forEach((order, index) => {
    const matchedItems = indicatorDocs.flatMap(doc => (itemsByQualityId.get(String(doc._id)) || []).filter(item => safeNumber(item.order) === order));
    const itemName = matchedItems[0]?.itemName || `明细项${order + 1}`;
    const itemValue = matchedItems.reduce((sum, item) => sum + safeNumber(item.itemData), 0);
    const details = matchedItems.flatMap(item => detailsByItemId.get(String(item._id)) || []);
    summaryRows.push({
      index: index + 1,
      item: itemName,
      value: trimTrailingZeros(itemValue.toFixed(2)),
      action: details.length ? {
        label: '查看详情',
        target: spec.key,
        startMonth,
        endMonth,
        itemOrder: String(order),
      } : null,
    });
  });

  if (!summaryRows.length) {
    return { indicator: { key: spec.key, name: spec.name }, columns: PATIENT_DETAIL_COLUMNS, rows: [] };
  }

  return {
    indicator: { key: spec.key, name: spec.name },
    columns: SUMMARY_COLUMNS,
    rows: summaryRows,
  };
}

module.exports = {
  QUALITY_SPECS,
  getQualityStats,
  getQualityDetail,
};
