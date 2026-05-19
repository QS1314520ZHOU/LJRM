const Patient = require('../models/Patient');
const Bedside = require('../models/Bedside');
const Order = require('../models/Order');
const moment = require('moment');

const EAST_8_OFFSET_MINUTES = 8 * 60;

const INDICATORS = [
  { id: 1, name: '体外人工膜肺（ECMO）', key: 'ecmo', unit: '例' },
  { id: 2, name: '有创呼吸机支持≥96小时', key: 'ventilatorGte96', unit: '例' },
  { id: 3, name: '有创呼吸机支持＜96小时', key: 'ventilatorLt96', unit: '例' },
  { id: 4, name: '有创呼吸机支持≥96小时伴CRRT', key: 'ventilatorCrrt', unit: '例' },
];

const DETAIL_COLUMNS = [
  { key: 'index', title: '序号' },
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

const DURATION_COLUMN = { key: 'durationCount', title: '呼吸通气时长' };

const SUPPORTED_KEYS = new Set(INDICATORS.map(item => item.key));
const DEPARTMENT_FIELDS = ['department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName'];
const PATIENT_SELECT = [
  '_id', 'hisPid', 'mrn', 'name', 'birthday', 'age', 'gender', 'hisBed', 'bedNo', 'bedCode', 'bedName', 'bedNumber',
  'hospitalNo', 'hospitalNumber', 'zyh', 'zyhm', 'hospitalTime', 'icuAdmissionTime', 'icuDischargeTime',
  'department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName', 'admissionDoctor',
  'admissionDoctorName', 'attendingDoctor', 'attendingDoctorName', 'chargeDoctorName', 'tubeDoctorName',
  'bedDoctor', 'admissionSource', 'inSource', 'source', 'dischargedType', 'dischargeType', 'outType',
  'dischargedDepartment', 'transferDept', 'outDeptName',
  'admissionDiagnosis', 'diagnosis', 'clinicalDiagnosis', 'primaryDiagnosis',
  'status',
].join(' ');

function escapeRegExp(text) {
  return String(text).replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function buildDepartmentOr(department) {
  if (process.env.ENABLE_DEPT_FILTER !== 'true' || !department) return [];
  const regex = new RegExp(escapeRegExp(department), 'i');
  return DEPARTMENT_FIELDS.map(field => ({ [field]: regex }));
}

function buildAdmissionRangeFilter(startDate, endDate, department) {
  const and = [{ icuAdmissionTime: { $gte: startDate, $lte: endDate } }, { status: { $ne: 'invalid' } }];
  const deptOr = buildDepartmentOr(department);
  if (deptOr.length) and.push({ $or: deptOr });
  return { $and: and };
}

function buildPatientFilter(extra = {}, department) {
  const and = [{ status: { $ne: 'invalid' } }, extra];
  const deptOr = buildDepartmentOr(department);
  if (deptOr.length) and.push({ $or: deptOr });
  return { $and: and };
}

function buildOrderFilter(extra = {}) {
  return { ...extra, status: { $ne: '作废' } };
}

function buildBedsideFilter(extra = {}) {
  return { ...extra, valid: { $ne: false } };
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

function getMonthRange(monthKey) {
  return {
    startDate: moment.parseZone(`${monthKey}-01T00:00:00+08:00`).startOf('month').toDate(),
    endDate: moment.parseZone(`${monthKey}-01T00:00:00+08:00`).endOf('month').toDate(),
  };
}

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

function toDetailRow(patient, index, extra = {}) {
  return {
    index,
    department: firstValue(patient, ['department', 'deptName', 'wardName', 'inDeptName', 'currentDeptName', 'unitName']) || '重症医学科',
    bedNo: firstValue(patient, ['hisBed']),
    name: firstValue(patient, ['name']),
    age: calcAge(patient),
    hospitalNo: firstValue(patient, ['mrn']),
    icuAdmissionTime: formatDateTime(patient.icuAdmissionTime),
    icuDischargeTime: formatDateTime(patient.icuDischargeTime),
    ecmoOrderTime: formatDateTime(patient.ecmoOrderTime),
    icuDays: calcIcuDays(patient),
    admissionDoctor: firstValue(patient, ['bedDoctor']),
    attendingDoctor: firstValue(patient, ['bedDoctor']),
    admissionSource: firstValue(patient, ['admissionSource', 'inSource', 'source']),
    dischargeType: firstValue(patient, ['dischargedType']),
    transferDept: firstValue(patient, ['dischargedDepartment']),
    diagnosis: firstValue(patient, ['clinicalDiagnosis', 'diagnosis', 'admissionDiagnosis', 'primaryDiagnosis']),
    ...extra,
  };
}

async function getAdmissionPatients(startDate, endDate, department) {
  return Patient.find(buildAdmissionRangeFilter(startDate, endDate, department))
    .select(PATIENT_SELECT)
    .lean();
}

async function getEcmoPatients(startDate, endDate, department) {
  const orders = await Order.find({
    ...buildOrderFilter({
      orderTime: { $gte: startDate, $lte: endDate },
    }),
    orderName: '体外人工膜肺（ECMO）安装术',
  }).select('mrn orderTime').lean();

  const mrnToOrderTimes = new Map();
  for (const order of orders) {
    const mrn = normalizeText(order.mrn);
    const time = asDate(order.orderTime);
    if (!mrn || !time) continue;
    if (!mrnToOrderTimes.has(mrn)) mrnToOrderTimes.set(mrn, []);
    mrnToOrderTimes.get(mrn).push(time);
  }
  for (const times of mrnToOrderTimes.values()) {
    times.sort((a, b) => a - b);
  }

  const mrns = [...mrnToOrderTimes.keys()];
  if (!mrns.length) return [];

  const patients = await Patient.find(buildPatientFilter({ mrn: { $in: mrns } }, department))
    .select(PATIENT_SELECT)
    .lean();

  const patientsByMrn = new Map();
  for (const patient of patients) {
    const mrn = normalizeText(patient.mrn);
    if (!mrn) continue;
    if (!patientsByMrn.has(mrn)) patientsByMrn.set(mrn, []);
    patientsByMrn.get(mrn).push(patient);
  }

  const matchedAdmissions = [];
  const added = new Set();
  for (const [mrn, mrnPatients] of patientsByMrn.entries()) {
    const orderTimes = mrnToOrderTimes.get(mrn) || [];
    if (!orderTimes.length) continue;

    if (mrnPatients.length === 1) {
      const patient = mrnPatients[0];
      const key = String(patient._id);
      if (!added.has(key)) {
        added.add(key);
        matchedAdmissions.push({
          ...patient,
          ecmoOrderTime: orderTimes[0] || null,
        });
      }
      continue;
    }

    for (const patient of mrnPatients) {
      const start = asDate(patient.icuAdmissionTime);
      const end = asDate(patient.icuDischargeTime) || new Date();
      if (!start) continue;
      const startWithTolerance = new Date(start.getTime() - 12 * 60 * 60 * 1000);
      const matchedTime = orderTimes.find(time => time >= startWithTolerance && time <= end);
      if (!matchedTime) continue;
      const key = String(patient._id);
      if (added.has(key)) continue;
      added.add(key);
      matchedAdmissions.push({
        ...patient,
        ecmoOrderTime: matchedTime,
      });
    }
  }
  return matchedAdmissions;
}

function isValidVentilatorValue(value) {
  const text = normalizeText(value).toUpperCase();
  return Boolean(text) && text !== 'S/T' && text !== 'BIPAP';
}

function isNotBlank(value) {
  return normalizeText(value) !== '';
}

function groupByPid(events) {
  const grouped = new Map();
  for (const event of events) {
    const pid = normalizeText(event.pid);
    if (!pid) continue;
    if (!grouped.has(pid)) grouped.set(pid, []);
    grouped.get(pid).push(event);
  }
  return grouped;
}

function eventTime(event) {
  return asDate(event.time) || asDate(event.editTime);
}

function isWithinIcu(event, patient) {
  const time = eventTime(event);
  const start = asDate(patient.icuAdmissionTime);
  const end = asDate(patient.icuDischargeTime) || new Date();
  return Boolean(time && start && time >= start && time <= end);
}

function calcCappedRecordCount(events, patient) {
  const dailyCount = new Map();
  for (const event of events) {
    if (!isValidVentilatorValue(event.strVal) || !isWithinIcu(event, patient)) continue;
    const day = moment(eventTime(event)).utcOffset(EAST_8_OFFSET_MINUTES).format('YYYY-MM-DD');
    dailyCount.set(day, (dailyCount.get(day) || 0) + 1);
  }
  let total = 0;
  for (const count of dailyCount.values()) {
    total += Math.min(count, 24);
  }
  return total;
}

async function getVentilatorStatsPatients(startDate, endDate, department) {
  const patients = await getAdmissionPatients(startDate, endDate, department);
  const pids = patients.map(patient => String(patient._id));
  if (!pids.length) return [];

  const events = await Bedside.find(buildBedsideFilter({
    pid: { $in: pids },
    code: 'param_HuXiMoShi',
    strVal: { $exists: true, $nin: ['', null, 'S/T', 'BIPAP', 's/t', 'bipap'] },
  })).select('pid code strVal time editTime').lean();

  const grouped = groupByPid(events);
  return patients.map(patient => {
    const pid = String(patient._id);
    const count = calcCappedRecordCount(grouped.get(pid) || [], patient);
    return { patient, count };
  }).filter(item => item.count > 0);
}

async function getVentilatorPatientsByThreshold(startDate, endDate, department, predicate) {
  const stats = await getVentilatorStatsPatients(startDate, endDate, department);
  return stats.filter(item => predicate(item.count)).map(item => item.patient);
}

async function getVentilatorStatsByThreshold(startDate, endDate, department, predicate) {
  const stats = await getVentilatorStatsPatients(startDate, endDate, department);
  return stats.filter(item => predicate(item.count));
}

async function getVentilatorCrrtPatients(startDate, endDate, department) {
  const stats = await getVentilatorStatsByThreshold(startDate, endDate, department, count => count >= 96);
  if (!stats.length) return [];

  const patientsByPid = new Map(stats.map(item => [String(item.patient._id), item.patient]));
  const pids = [...patientsByPid.keys()];
  const crrtEvents = await Bedside.find(buildBedsideFilter({
    pid: { $in: pids },
    code: 'param_CBP_set_Blood_Flow',
    strVal: { $exists: true, $nin: ['', null] },
  })).select('pid code strVal time editTime').lean();

  const matched = new Set();
  for (const event of crrtEvents) {
    if (!isNotBlank(event.strVal)) continue;
    const patient = patientsByPid.get(normalizeText(event.pid));
    if (patient && isWithinIcu(event, patient)) matched.add(String(patient._id));
  }
  return stats.filter(item => matched.has(String(item.patient._id))).map(item => item.patient);
}

async function getVentilatorCrrtStatsPatients(startDate, endDate, department) {
  const stats = await getVentilatorStatsByThreshold(startDate, endDate, department, count => count >= 96);
  if (!stats.length) return [];

  const patientsByPid = new Map(stats.map(item => [String(item.patient._id), item.patient]));
  const pids = [...patientsByPid.keys()];
  const crrtEvents = await Bedside.find(buildBedsideFilter({
    pid: { $in: pids },
    code: 'param_CBP_set_Blood_Flow',
    strVal: { $exists: true, $nin: ['', null] },
  })).select('pid code strVal time editTime').lean();

  const matched = new Set();
  for (const event of crrtEvents) {
    if (!isNotBlank(event.strVal)) continue;
    const patient = patientsByPid.get(normalizeText(event.pid));
    if (patient && isWithinIcu(event, patient)) matched.add(String(patient._id));
  }
  return stats.filter(item => matched.has(String(item.patient._id)));
}

async function getIndicatorPatients(indicatorKey, startDate, endDate, department) {
  switch (indicatorKey) {
    case 'ecmo':
      return getEcmoPatients(startDate, endDate, department);
    case 'ventilatorGte96':
      return getVentilatorPatientsByThreshold(startDate, endDate, department, count => count >= 96);
    case 'ventilatorLt96':
      return getVentilatorPatientsByThreshold(startDate, endDate, department, count => count < 96);
    case 'ventilatorCrrt':
      return getVentilatorCrrtPatients(startDate, endDate, department);
    default:
      throw new Error('指标不支持');
  }
}

async function calculateIndicator(indicatorKey, startDate, endDate, department) {
  const patients = await getIndicatorPatients(indicatorKey, startDate, endDate, department);
  return patients.length;
}

async function buildRows(months, department) {
  return Promise.all(INDICATORS.map(async indicator => {
    const values = await Promise.all(months.map(async monthKey => {
      const { startDate, endDate } = getMonthRange(monthKey);
      const value = await calculateIndicator(indicator.key, startDate, endDate, department);
      return [monthKey, value];
    }));
    const monthMap = Object.fromEntries(values);
    const total = values.reduce((sum, [, value]) => sum + value, 0);
    return { id: indicator.id, name: indicator.name, key: indicator.key, unit: indicator.unit, total, months: monthMap };
  }));
}

async function getYearStats(year, department = '') {
  const y = validateYear(year);
  const months = Array.from({ length: 12 }, (_, i) => `${y}-${String(i + 1).padStart(2, '0')}`);
  const data = await buildRows(months, department);
  return { months, data, startMonth: `${y}-01`, endMonth: `${y}-12` };
}

async function getRangeStats(startMonth, endMonth, department = '') {
  const months = buildMonths(startMonth, endMonth);
  const data = await buildRows(months, department);
  return { months, data, startMonth, endMonth };
}

function getDetailColumns(indicatorKey) {
  if (indicatorKey === 'ventilatorGte96' || indicatorKey === 'ventilatorLt96' || indicatorKey === 'ventilatorCrrt') {
    const insertIndex = DETAIL_COLUMNS.findIndex(col => col.key === 'icuDays') + 1;
    return [
      ...DETAIL_COLUMNS.slice(0, insertIndex),
      DURATION_COLUMN,
      ...DETAIL_COLUMNS.slice(insertIndex),
    ];
  }
  return DETAIL_COLUMNS;
}

async function getDetail(indicatorKey, startMonth, endMonth, department = '') {
  if (!SUPPORTED_KEYS.has(indicatorKey)) throw new Error('指标不支持');
  const months = buildMonths(startMonth, endMonth);
  const indicator = INDICATORS.find(item => item.key === indicatorKey);
  const startDate = moment.parseZone(`${months[0]}-01T00:00:00+08:00`).startOf('month').toDate();
  const endDate = moment.parseZone(`${months[months.length - 1]}-01T00:00:00+08:00`).endOf('month').toDate();
  const columns = getDetailColumns(indicatorKey);

  if (indicatorKey === 'ventilatorGte96' || indicatorKey === 'ventilatorLt96') {
    const stats = await getVentilatorStatsByThreshold(
      startDate,
      endDate,
      department,
      indicatorKey === 'ventilatorGte96' ? count => count >= 96 : count => count < 96
    );
    const rows = stats.map((item, idx) => toDetailRow(item.patient, idx + 1, { durationCount: item.count }));
    return { indicator, columns, rows };
  }

  if (indicatorKey === 'ventilatorCrrt') {
    const stats = await getVentilatorCrrtStatsPatients(startDate, endDate, department);
    const rows = stats.map((item, idx) => toDetailRow(item.patient, idx + 1, { durationCount: item.count }));
    return { indicator, columns, rows };
  }

  const patients = await getIndicatorPatients(indicatorKey, startDate, endDate, department);
  const rows = patients.map((patient, idx) => toDetailRow(patient, idx + 1));
  return { indicator, columns, rows };
}

module.exports = { INDICATORS, DETAIL_COLUMNS, getYearStats, getRangeStats, getDetail };
