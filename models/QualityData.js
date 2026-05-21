const mongoose = require('mongoose');
const { dataCenterConn } = require('../config/db');

const QualityDataSchema = new mongoose.Schema({
  deptCode: String,
  deptName: String,
  year: Number,
  month: Number,
  startTime: Date,
  endTime: Date,
  indicator: String,
  indicatorCode: String,
  indicatorData: Number,
  indicatorType: String,
}, { collection: 'VI_ICU_QUALITY', strict: false });

module.exports = dataCenterConn.model('QualityData', QualityDataSchema);
