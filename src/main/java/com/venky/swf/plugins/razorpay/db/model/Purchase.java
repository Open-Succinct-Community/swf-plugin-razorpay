package com.venky.swf.plugins.razorpay.db.model;

import com.razorpay.RazorpayClient;
import com.venky.core.util.Bucket;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_NAME;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.UNIQUE_KEY;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.db.annotations.column.validations.Enumeration;
import com.venky.swf.db.model.Model;

import java.io.Reader;
import java.sql.Timestamp;

public interface  Purchase extends com.venky.swf.plugins.payments.db.model.payment.Purchase {
    @PARTICIPANT
    public Long getApplicationId();
    public void setApplicationId(Long id);
    public Application getApplication();


    public Reader getPaymentJson();
    public void setPaymentJson(Reader reader);


    public RazorpayClient createRazorpayClient() ;


    public boolean canAcceptPayment();

    @COLUMN_SIZE(1024)
    public String getOrderJson();
    public void setOrderJson(String orderJson);

    public String getRazorpayOrderId();
    public void setRazorpayOrderId(String razorpayOrderId);


    @COLUMN_DEF(StandardDefault.BOOLEAN_FALSE)
    @IS_NULLABLE(false)
    public boolean isProduction();
    public void setProduction(boolean production);

}
