const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const BedRecordSchema = new mongoose.Schema({
  deptCode: String,
  time: Date,
  bedNum: Number,
  createTime: Date,
  createUser: String,
}, { collection: 'bedRecord', strict: false });

module.exports = smartCareConn.model('BedRecord', BedRecordSchema);
