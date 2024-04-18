package com.venky.swf.plugins.razorpay.db.model;

import com.venky.core.util.Bucket;
import com.venky.swf.db.Database;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.plugins.collab.db.model.user.User;


public class PlanImpl extends ModelImpl<Plan> {
    public PlanImpl(Plan proxy){
        super(proxy);
    }
    public User getUser(){
        com.venky.swf.db.model.User user = Database.getInstance().getCurrentUser();
        if (user != null){
            return user.getRawRecord().getAsProxy(User.class);
        }
        return null;
    }

    public Purchase purchase(Application forApplication){
        User user = getUser();
        if (user == null){
            throw  new RuntimeException("Please login to purchase");
        }
        Plan plan = getProxy();
        Purchase purchase = Database.getTable(Purchase.class).newRecord();
        purchase.setPlanId(plan.getId());
        purchase.setApplicationId(forApplication.getId());
        purchase.setCaptured(false);
        //purchase.setRazorpayPaymentId();
        purchase.setRemainingCredits(new Bucket());
        purchase.save();
        return purchase;
    }
}
