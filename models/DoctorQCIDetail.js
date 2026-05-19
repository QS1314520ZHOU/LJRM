const mongoose = require('mongoose');
const { smartCareConn } = require('../config/db');

const DoctorQCIDetailSchema = new mongoose.Schema({}, {
  collection: 'doctorQCIDetail',
  strict: false,
});

module.exports = smartCareConn.model('DoctorQCIDetail', DoctorQCIDetailSchema);
