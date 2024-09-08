package com.venky.swf.plugins.razorpay.db.model;


import com.venky.swf.plugins.razorpay.db.model.buyers.Buyer;

public interface Plan extends com.venky.swf.plugins.payments.db.model.payment.Plan {
    public Purchase purchase(Buyer forBuyer);

}
