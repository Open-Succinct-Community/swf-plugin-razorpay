package com.venky.swf.plugins.razorpay.db.model.buyers;

import com.venky.cache.UnboundedCache;
import com.venky.core.date.DateUtils;
import com.venky.core.util.Bucket;
import com.venky.core.util.ObjectHolder;
import com.venky.swf.db.model.Model;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.db.table.ModelImpl;
import com.venky.swf.plugins.razorpay.db.model.Plan;
import com.venky.swf.plugins.razorpay.db.model.Purchase;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;

import java.lang.reflect.Method;
import java.sql.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class BuyerImpl<B extends Buyer & Model> extends ModelImpl<B> {
    public BuyerImpl(B proxy){
        super(proxy);
    }

    Map<Boolean,Map<String,Integer>> balanceByEnv = new UnboundedCache<Boolean, Map<String, Integer>>() {
        @Override
        protected Map<String, Integer> getValue(Boolean key) {
            return null;
        }
    };
    public Map<String,Integer> getBalance(boolean production){
        Map<String,Integer>balance = balanceByEnv.get(production);
        if (balance != null){
            return balance;
        }
        synchronized (this) {
            B buyer = getProxy();
            Bucket balanceCredits = new Bucket();
            ObjectHolder<Integer> numDaysToExpiry = new ObjectHolder<>(0);

            long today = DateUtils.getStartOfDay(System.currentTimeMillis());

            getActiveSubscriptions(production).forEach(as -> {
                Plan plan = as.getPlan().getRawRecord().getAsProxy(Plan.class);
                balanceCredits.increment(as.getRemainingCredits().intValue());
                if (as.getExpiresOn() != null) {
                    if (as.getExpiresOn().getTime() > today) {
                        numDaysToExpiry.set(Math.max(numDaysToExpiry.get(), DateUtils.compareToDays(as.getExpiresOn().getTime(), today)));
                    }
                }
            });

            balance = new HashMap<String, Integer>() {{
                put("CREDITS", balanceCredits.intValue());
                put("DAYS", numDaysToExpiry.get());
            }};
        }
        return balance;
    }

    public int getTestBalance() {
        return getBalance(false).get("CREDITS");
    }
    public int getProductionBalance() {
        return getBalance(true).get("CREDITS");
    }
    public int getNumDaysLeftInProductionSubscription(){
        return getBalance(true).get("DAYS");
    }
    public int getNumDaysLeftInTestSubscription() {
        return getBalance(false).get("DAYS");
    }

    public Purchase getActiveSubscription(boolean production){
        List<Purchase> purchases = getActiveSubscriptions(production);
        if (!purchases.isEmpty()){
            return purchases.get(0);
        }
        return null;
    }
    public List<Purchase> getActiveSubscriptions(boolean production){
        B buyer = getProxy();
        Select select = new Select().from(Purchase.class);
        Expression expression = new Expression(select.getPool(), Conjunction.AND);
        List<String> referenceFields = ModelReflector.instance(Purchase.class).getReferenceFields(buyer.getReflector().getModelClass());
        if (referenceFields.size() == 1){
            expression.add(new Expression(select.getPool(),referenceFields.get(0) , Operator.EQ,buyer.getId()));
        }

        expression.add(new Expression(select.getPool(),"PRODUCTION", Operator.EQ,production));
        expression.add(new Expression(select.getPool(),"CAPTURED", Operator.EQ,true));
        expression.add(new Expression(select.getPool(),"PAYMENT_REFERENCE", Operator.NE));
        expression.add(new Expression(select.getPool(),"PURCHASED_ON", Operator.NE));
        Expression expiry = new Expression(select.getPool(),Conjunction.OR);
        expiry.add(new Expression(select.getPool(),"REMAINING_CREDITS", Operator.GT,0));
        expiry.add(new Expression(select.getPool(),"EXPIRES_ON", Operator.GT,new Date(DateUtils.getStartOfDay(System.currentTimeMillis()))));
        expression.add(expiry);

        return select.where(expression).orderBy("PURCHASED_ON").execute();
    }
    public Purchase getIncompletePurchase(boolean production) {
        B buyer = getProxy();
        Select select = new Select().from(Purchase.class);
        Expression expression = new Expression(select.getPool(), Conjunction.AND);
        List<Method> referredModelGetters = getReflector().getReferredModelGetters(buyer.getReflector().getModelClass());
        if (referredModelGetters.size() == 1){
            String referenceField = getReflector().getReferenceField(referredModelGetters.get(0));
            expression.add(new Expression(select.getPool(),referenceField , Operator.EQ,buyer.getId()));
        }
        expression.add(new Expression(select.getPool(),"CAPTURED", Operator.EQ,false));
        expression.add(new Expression(select.getPool(),"PRODUCTION", Operator.EQ,production));

        List<Purchase> purchases = select.where(expression).orderBy("ID DESC").execute(1);
        if (purchases.isEmpty()){
            return null;
        }else {
            return purchases.get(0);
        }
    }
}
