package com.venky.swf.plugins.razorpay.db.model;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;

import java.util.List;

public interface Application extends com.venky.swf.plugins.collab.db.model.participants.Application {



    @IS_VIRTUAL
    public int getTestBalance();

    @IS_VIRTUAL
    public int getProductionBalance();

    public List<Purchase> getPurchases();

    @IS_VIRTUAL
    public Purchase getActiveSubscription();

    @IS_VIRTUAL
    public Purchase getIncompletePurchase();

}
