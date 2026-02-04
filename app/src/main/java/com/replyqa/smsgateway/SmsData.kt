package com.replyqa.smsgateway

class SmsData(
    var msg: String,
    var from: String?
) {
    init {
        msg = msg
        from = from
    }
}