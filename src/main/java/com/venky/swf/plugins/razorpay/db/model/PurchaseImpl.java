package com.venky.swf.plugins.razorpay.db.model;

import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.plugins.payments.db.model.payment.Purchase.PaymentStatus;
import com.venky.swf.routing.Config;


public class PurchaseImpl extends ModelImpl<Purchase> {
    public PurchaseImpl(Purchase p){
        super(p);
    }
    public RazorpayClient createRazorpayClient() {
        Purchase invoice = getProxy();

        String key = Config.instance().getProperty(String.format("%s.%s","razor.pay.key",invoice.isProduction()? "prod" :"test"));
        String secret = Config.instance().getProperty(String.format("%s.%s","razor.pay.secret",invoice.isProduction()? "prod" :"test"));
        try {
            return new RazorpayClient(key, secret, true);
        }catch (Exception eX){
            return null;
        }
    }

    public void refreshPaymentStatus(){
        Purchase purchase = getProxy();
        if (!ObjectUtil.isVoid(purchase.getPaymentReference())){
            Payment payment ;
            try {
                payment = createRazorpayClient().payments.fetch(purchase.getPaymentReference());
            } catch (RazorpayException e) {
                throw new RuntimeException(e);
            }
            purchase.setStatus(payment.get("status"));
            if (PaymentStatus.valueOf(purchase.getStatus()).ordinal()
                    >= PaymentStatus.captured.ordinal()){
                purchase.setCaptured(true);
            }
            purchase.save();
        }
    }

    public boolean canAcceptPayment(){
        Purchase purchase = getProxy();
        refreshPaymentStatus();
        return PaymentStatus.valueOf(purchase.getStatus()).compareTo(PaymentStatus.authorized) < 0;
    }
}

