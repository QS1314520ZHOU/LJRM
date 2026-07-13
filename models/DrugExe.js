const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DrugExeSchema = new mongoose.Schema({
  pid: String,
  mrn: String,
  status: String,
  startTime: Date,
  endTime: Date,
  liquidAmount: Number,
  liquidAmountUnit: String,
  drugList: Array,
}, { collection: 'drugExe', strict: false });

module.exports = smartCareConn.model('DrugExe', DrugExeSchema);
