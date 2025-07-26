package com.venky.swf.plugins.razorpay.db.model;

import com.razorpay.RazorpayClient;
import com.venky.swf.db.annotations.column.COLUMN_SIZE;

import java.io.Reader;

public interface  Purchase extends com.venky.swf.plugins.payments.db.model.payment.Purchase {
   

    public Reader getPaymentJson();
    public void setPaymentJson(Reader reader);


    public RazorpayClient createRazorpayClient() ;


    public boolean canAcceptPayment();

    @COLUMN_SIZE(1024)
    public String getOrderJson();
    public void setOrderJson(String orderJson);

    public String getRazorpayOrderId();
    public void setRazorpayOrderId(String razorpayOrderId);


    

}
