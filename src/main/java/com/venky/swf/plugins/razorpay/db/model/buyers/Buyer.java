package com.venky.swf.plugins.razorpay.db.model.buyers;

import com.venky.swf.db.annotations.column.IS_VIRTUAL;
import com.venky.swf.plugins.razorpay.db.model.Purchase;

import java.util.List;
import java.util.Map;

public interface Buyer {

    @IS_VIRTUAL
    public int getTestBalance();

    @IS_VIRTUAL
    public int getNumDaysLeftInTestSubscription();

    @IS_VIRTUAL
    public int getProductionBalance();

    @IS_VIRTUAL
    public int getNumDaysLeftInProductionSubscription();


    public List<Purchase> getPurchases();


    @IS_VIRTUAL
    public Purchase getIncompletePurchase(boolean production);


    Map<String,Integer> getBalance(boolean production);
}
