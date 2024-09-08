package com.venky.swf.plugins.razorpay.db.model;

import com.razorpay.RazorpayClient;
import com.venky.swf.db.annotations.column.COLUMN_DEF;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;
import com.venky.swf.db.annotations.column.IS_NULLABLE;
import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.db.annotations.column.defaulting.StandardDefault;
import com.venky.swf.db.annotations.column.pm.PARTICIPANT;
import com.venky.swf.plugins.razorpay.db.model.buyers.Application;
import com.venky.swf.plugins.razorpay.db.model.buyers.Buyer;
import com.venky.swf.plugins.razorpay.db.model.buyers.Company;

import java.io.Reader;

public interface  Purchase extends com.venky.swf.plugins.payments.db.model.payment.Purchase {
    @PARTICIPANT
    @IS_NULLABLE
    public Long getApplicationId();
    public void setApplicationId(Long id);
    public Application getApplication();

    @PARTICIPANT
    @IS_NULLABLE
    Long getCompanyId();
    void setCompanyId(Long id);
    Company getCompany();

    @IS_VIRTUAL
    Long getBuyerId();
    Buyer getBuyer();

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
