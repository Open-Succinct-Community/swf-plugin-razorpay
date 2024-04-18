package com.venky.swf.plugins.razorpay.db.model;

import com.venky.core.util.Bucket;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.util.List;

public class ApplicationImpl extends ModelImpl<Application> {
    public ApplicationImpl(Application proxy){
        super(proxy);
    }
    public int getTestBalance() {
        Application application = getProxy();
        Bucket balance  = new Bucket();
        getActiveSubscriptions().forEach(as->{
            if (!as.isProduction()) {
                balance.increment(as.getRemainingCredits().intValue());
            }
        });
        return balance.intValue();
    }
    public int getProductionBalance() {
        Application application = getProxy();
        Bucket balance  = new Bucket();
        getActiveSubscriptions().forEach(as->{
            if (as.isProduction()) {
                balance.increment(as.getRemainingCredits().intValue());
            }
        });
        return balance.intValue();
    }

    public Purchase getActiveSubscription(){
        List<Purchase> purchases = getActiveSubscriptions();
        if (!purchases.isEmpty()){
            return purchases.get(0);
        }
        return null;
    }
    public List<Purchase> getActiveSubscriptions(){
        Application application = getProxy();
        Select select = new Select().from(Purchase.class);
        Expression expression = new Expression(select.getPool(), Conjunction.AND);
        expression.add(new Expression(select.getPool(),"APPLICATION_ID", Operator.EQ,application.getId()));
        expression.add(new Expression(select.getPool(),"CAPTURED", Operator.EQ,true));
        expression.add(new Expression(select.getPool(),"PAYMENT_REFERENCE", Operator.NE));
        expression.add(new Expression(select.getPool(),"PURCHASED_ON", Operator.NE));
        expression.add(new Expression(select.getPool(),"REMAINING_CREDITS", Operator.GT,0));

        return select.where(expression).orderBy("PURCHASED_ON").execute();
    }
    public Purchase getIncompletePurchase() {
        Application application = getProxy();
        Select select = new Select().from(Purchase.class);
        Expression expression = new Expression(select.getPool(), Conjunction.AND);
        expression.add(new Expression(select.getPool(),"APPLICATION_ID", Operator.EQ,application.getId()));
        expression.add(new Expression(select.getPool(),"CAPTURED", Operator.EQ,false));
        List<Purchase> purchases = select.where(expression).orderBy("ID DESC").execute(1);
        if (purchases.isEmpty()){
            return null;
        }else {
            return purchases.get(0);
        }
    }
}
