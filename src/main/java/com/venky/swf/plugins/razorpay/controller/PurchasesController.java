package com.venky.swf.plugins.razorpay.controller;

import com.razorpay.Order;
import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.venky.core.io.StringReader;
import com.venky.core.math.DoubleHolder;
import com.venky.core.string.StringUtil;
import com.venky.core.util.MultiException;
import com.venky.swf.controller.ModelController;
import com.venky.swf.controller.annotations.SingleRecordAction;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.Model;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.payments.db.model.payment.Plan;
import com.venky.swf.plugins.payments.db.model.payment.Purchase.PaymentStatus;
import com.venky.swf.plugins.razorpay.db.model.Application;

import com.venky.swf.plugins.razorpay.db.model.Purchase;

import com.venky.swf.views.View;
import org.json.JSONObject;
import org.json.simple.JSONValue;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class PurchasesController extends ModelController<Purchase> {
    public PurchasesController(Path path) {
        super(path);
    }

    public View buy(){
        List<Purchase> purchases = getIntegrationAdaptor().readRequest(getPath());

        if (purchases.size() > 1){
            throw new RuntimeException ("Only one purchase can be made at a time!");
        }else if (purchases.isEmpty()){
            throw new RuntimeException ("Nothing to purchase!");
        }
        Purchase purchase = purchases.get(0);
        if (purchase.getRawRecord().isNewRecord()){
            Plan plan = purchase.getPlan();
            Application application = purchase.getApplication();
            Purchase fromDb = application.getIncompletePurchase();
            if (fromDb == null) {
                fromDb = plan.getRawRecord().getAsProxy(com.venky.swf.plugins.razorpay.db.model.Plan.class).purchase(application);
            }
            fromDb.getRawRecord().load(purchase.getRawRecord());
            purchase = fromDb;
        }
        if (purchase.isCaptured()){
            throw new RuntimeException("Purchase already captured!");
        }
        try {
            return pay(purchase);
        }catch (RazorpayException ex){
            throw new RuntimeException(ex);
        }
    }
    @SingleRecordAction(icon = "glyphicon-shopping-cart", tooltip = "Pay")
    public View pay(long id) throws RazorpayException {
        Purchase purchase = Database.getTable(Purchase.class).get(id);
        return pay(purchase);
    }
    public View pay(Purchase purchase) throws RazorpayException{
        if (purchase.getReflector().isVoid(purchase.getOrderJson()) || purchase.getRawRecord().isFieldDirty("PLAN_ID")){
            RazorpayClient client = purchase.createRazorpayClient();
            JSONObject object =new JSONObject();
            object.put("amount",purchase.getPlan().getSellingPrice() * 100);
            object.put("currency","INR");
            object.put("receipt",StringUtil.valueOf(purchase.getId()));
            JSONObject notes = new JSONObject();
            notes.put("Plan",purchase.getPlan().getName());
            notes.put("GST",StringUtil.valueOf(purchase.getPlan().getTax()));

            object.put("notes",notes);
            Order order = client.orders.create(object);
            purchase.setOrderJson(order.toString());
            purchase.setRazorpayOrderId(order.get("id"));
        }
        if (purchase.isDirty()) {
            purchase.save();
        }
        return show(purchase);
    }


    private Map<String,Object> getFields(){
        Map<String,Object> fields = new HashMap<>();
        if (getIntegrationAdaptor() == null){
            fields.putAll(getFormFields());
        }else{
            try {
                org.json.simple.JSONObject object = (org.json.simple.JSONObject) JSONValue.parse(getPath().getRequest().getReader());
                fields.put("INVOICE_ID",object.get("INVOICE_ID"));
                fields.put("razorpay_payment_id",object.get("razorpay_payment_id"));
                if (object.containsKey("razorpay_signature")) {
                    fields.put("razorpay_signature", object.get("razorpay_signature"));
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return fields;
    }

    @Override
    protected Map<Class<? extends Model>, List<String>> getIncludedModelFields() {
        Map<Class<? extends Model>, List<String>> map = super.getIncludedModelFields();
        if (!map.containsKey(Application.class)){
            map.put(Application.class, Arrays.asList("ID","APP_ID","CHANGE_SECRET", "TEST_BALANCE", "PRODUCTION_BALANCE"));
        }
        if (!map.containsKey(Plan.class)){
            map.put(Plan.class, Arrays.asList("ID","NAME","MAXIMUM_RETAIL_PRICE", "NUMBER_OF_CREDITS","TAX_PERCENTAGE"));
        }
        return map;
    }


}
