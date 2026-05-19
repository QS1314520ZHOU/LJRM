const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DoctorQualityItemDetailSchema = new mongoose.Schema({}, {
  collection: 'doctorQualityItemDetail',
  strict: false,
});

module.exports = smartCareConn.model('DoctorQualityItemDetail', DoctorQualityItemDetailSchema);
