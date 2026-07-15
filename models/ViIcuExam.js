const mongoose = require('mongoose');
const { dataCenterConn } = require('../config/db');

const ViIcuExamSchema = new mongoose.Schema({
  reportID: String,
  mrn: String,
  hisPid: String,
  code: String,
  name: String,
  authTime: Date,
  status: String,
  valid: Boolean,
}, { collection: 'VI_ICU_EXAM', strict: false });

module.exports = dataCenterConn.model('ViIcuExam', ViIcuExamSchema);
