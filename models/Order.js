const mongoose = require('mongoose');
const { dataCenterConn } = require('../config/db');

const OrderSchema = new mongoose.Schema({
  orderID: String,
  pid: String,
  mrn: String,
  zycs: String,
  orderType: String,
  yaoType: String,
  dose: String,
  unit: String,
  exeMethodCode: String,
  orderName: String,
  orderYaoCode: String,
  groupID: String,
  planTime: Date,
  orderDoctorID: String,
  status: String,
  orderTime: Date,
}, { collection: 'VI_ICU_ZYYZ', strict: false });

module.exports = dataCenterConn.model('Order', OrderSchema);
