const mongoose = require('mongoose');
const { dataCenterConn } = require('../config/db');

const ViIcuExamItemSchema = new mongoose.Schema({
  examID: String,
  itemCode: String,
  itemName: String,
  result: String,
  unit: String,
  authTime: Date,
  resultStatus: String,
}, { collection: 'VI_ICU_EXAM_ITEM', strict: false });

module.exports = dataCenterConn.model('ViIcuExamItem', ViIcuExamItemSchema);
