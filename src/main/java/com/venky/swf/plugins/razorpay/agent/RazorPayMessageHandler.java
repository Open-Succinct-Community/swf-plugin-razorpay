package com.venky.swf.plugins.razorpay.agent;

import com.razorpay.Payment;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.venky.core.io.StringReader;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.reflection.ModelReflector;
import com.venky.swf.plugins.background.core.Task;
import com.venky.swf.plugins.payments.db.model.payment.Purchase.PaymentStatus;
import com.venky.swf.plugins.razorpay.db.model.Purchase;
import com.venky.swf.routing.Config;
import com.venky.swf.sql.Conjunction;
import com.venky.swf.sql.Expression;
import com.venky.swf.sql.Operator;
import com.venky.swf.sql.Select;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RazorPayMessageHandler implements Task {
    String signature;
    String payLoad;

    public  RazorPayMessageHandler(String signature, String payload){
        this.signature = signature;
        this.payLoad = payload;
    }
    public RazorPayMessageHandler(){
    }


    @Override
    public void execute() {
        Logger cat = Config.instance().getLogger(getClass().getName());
        Database.getInstance().getCurrentTransaction().setAttribute("X-AuthorizedPaymentUpdate",true);

        cat.info("Sign:" + signature);
        cat.info("Payload:" + payLoad);

        JSONObject jsonObject = new JSONObject(payLoad);
        JSONArray array = jsonObject.getJSONArray("contains");
        Payment payment = null;
        for (int i = 0; i < array.length(); i++) {
            if (array.getString(i).equals("payment")) {
                payment = new Payment(jsonObject.getJSONObject("payload").getJSONObject("payment"));
                break;
            }
        }
        if (payment != null) {
            JSONObject entity = payment.get("entity");
            Object o  = entity.get("notes");
            JSONObject notes = null;
            JSONArray notes_array = null;
            if (o instanceof JSONArray){
                notes_array = (JSONArray) o;
                notes = notes_array.length() > 0 ? notes_array.getJSONObject(0) : null;
            }else if (o instanceof JSONObject){
                notes = (JSONObject) o;
            }
            
            long purchaseId = Long.parseLong(notes.getString("purchase_id"));
            Purchase purchase = Database.getTable(Purchase.class).get(purchaseId);
            StringTokenizer tokenizer  = new StringTokenizer(Config.instance().getHostName(),".");
            String part = tokenizer.nextToken();
            
            String secret = Config.instance().getProperty(String.format("razor.pay.secret.%s",purchase.isProduction()? "prod" : "test")); //Use the default secret across all instances of razorpay.
            secret = String.format("%s.%s",part,secret);
            
            cat.info("Sign:" + signature);
            cat.info("Payload:" + payLoad);
            
            try {
                if (Utils.verifyWebhookSignature(payLoad,signature,secret)){
                    cat.warning("VerifyWebHookSignature Success.!");
                    JSONObject message = new JSONObject(payLoad);
                    handleMessage(message);
                }else {
                    Config.instance().getLogger(getClass().getName()).log(Level.WARNING,"Signature does not match. Not for this host.");
                }
            } catch (RazorpayException e) {
                throw new RuntimeException(e);
            }
        }
    }



    private void handleMessage(JSONObject payload) {
        if (((String)payload.get("event")).startsWith("payment")) {
            update_payment(payload);
        }
    }

    private void update_payment(JSONObject payload) {
        Purchase purchase = getPurchase(payload);
        purchase.save();
    }

    private Purchase getPurchase(JSONObject payload) {
        JSONObject payment = ((JSONObject)((JSONObject)((JSONObject)payload.get("payload")).get("payment")).get("entity"));
        ModelReflector<Purchase> ref = ModelReflector.instance(Purchase.class);
        String paymentId =  (String)payment.get("id");
        JSONObject notes =  (JSONObject) payment.get("notes");
        long purchase_id = ref.getJdbcTypeHelper().getTypeRef(Long.class).getTypeConverter().valueOf(notes.get("purchase_id"));
        Expression where = new Expression(ref.getPool(), Conjunction.AND);

        Expression paymentIdWhere = new Expression(ref.getPool(),Conjunction.OR);
        paymentIdWhere.add(new Expression(ref.getPool(),"PAYMENT_REFERENCE", Operator.EQ, paymentId));
        paymentIdWhere.add(new Expression(ref.getPool(),"PAYMENT_REFERENCE", Operator.EQ));
        where.add(paymentIdWhere);

        if (purchase_id > 0){
            where.add(new Expression(ref.getPool(),"ID",Operator.EQ,purchase_id));
        }else {
            throw new RuntimeException("No Notes present to identify purchase.!");
        }



        Select select = new Select().from(Purchase.class);
        select.where(where);

        List<Purchase> paymentList = select.execute();
        Purchase purchase ;
        if (paymentList.isEmpty()){

            throw new RuntimeException("Could not identify Invoice!");
        }else if (paymentList.size() > 1){
            throw new RuntimeException("Multiple payments found for same id " + paymentId);
        }else {
            purchase = paymentList.get(0);
        }
        String newStatus = (String)payment.get("status");
        boolean captured = purchase.getReflector().getJdbcTypeHelper().getTypeRef(Boolean.class).getTypeConverter().valueOf(payment.get("captured"));
        if (PaymentStatus.valueOf(purchase.getStatus()) == PaymentStatus.failed ||
                PaymentStatus.valueOf(newStatus).compareTo(PaymentStatus.valueOf(purchase.getStatus())) > 0 ) {
            purchase.setStatus(newStatus);
            purchase.setPaymentJson(new StringReader(payment.toString()));
            purchase.setCaptured(purchase.isCaptured() || captured);
        }
        return purchase;
    }

}
