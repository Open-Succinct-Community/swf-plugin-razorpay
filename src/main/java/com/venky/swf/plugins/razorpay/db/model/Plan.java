package com.venky.swf.plugins.razorpay.db.model;



public interface Plan extends com.venky.swf.plugins.payments.db.model.payment.Plan {
    public Purchase purchase(Application forApplication);

}
