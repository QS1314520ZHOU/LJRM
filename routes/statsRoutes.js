const express = require('express');
const statsService = require('../services/statsService');
const qualityService = require('../services/qualityService');

const router = express.Router();

function ok(res, data) {
  res.json({ code: 200, msg: 'success', data });
}

function fail(res, err) {
  const message = err && err.message ? err.message : '服务器异常';
  const status = /格式|缺失|不能|超过|不支持/.test(message) ? 400 : 500;
  if (status === 500) console.error(err);
  res.status(status).json({ code: status, msg: message });
}

router.get('/indicators', (req, res) => {
  ok(res, statsService.INDICATORS);
});

router.get('/year', async (req, res) => {
  try {
    const { year, department = '' } = req.query;
    if (!year) throw new Error('年份参数缺失');
    const data = await statsService.getYearStats(year, department);
    ok(res, data);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/range', async (req, res) => {
  try {
    const { startMonth, endMonth, department = '' } = req.query;
    if (!startMonth || !endMonth) throw new Error('月份参数缺失');
    const data = await statsService.getRangeStats(startMonth, endMonth, department);
    ok(res, data);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/detail', async (req, res) => {
  try {
    const { indicatorKey, startMonth, endMonth, department = '' } = req.query;
    if (!indicatorKey) throw new Error('指标参数缺失');
    if (!startMonth || !endMonth) throw new Error('月份参数缺失');
    const data = await statsService.getDetail(indicatorKey, startMonth, endMonth, department);
    ok(res, data);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/quality', async (req, res) => {
  try {
    const { year, startMonth, endMonth, department = '' } = req.query;
    const data = await qualityService.getQualityStats({ year, startMonth, endMonth, department });
    ok(res, data);
  } catch (err) {
    fail(res, err);
  }
});

router.get('/quality/detail', async (req, res) => {
  try {
    const { indicatorKey, year, startMonth, endMonth, department = '', itemOrder } = req.query;
    if (!indicatorKey) throw new Error('指标参数缺失');
    const data = await qualityService.getQualityDetail(indicatorKey, { year, startMonth, endMonth, department, itemOrder });
    ok(res, data);
  } catch (err) {
    fail(res, err);
  }
});

module.exports = router;
