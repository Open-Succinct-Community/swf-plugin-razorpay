package com.venky.swf.plugins.razorpay.extensions;

import com.razorpay.Payment;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.venky.core.io.StringReader;
import com.venky.core.math.DoubleHolder;
import com.venky.core.util.MultiException;
import com.venky.swf.db.Database;
import com.venky.swf.db.extensions.BeforeModelValidateExtension;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.payments.db.model.payment.Purchase.PaymentStatus;
import com.venky.swf.plugins.payments.tasks.PaymentCapture;
import com.venky.swf.plugins.razorpay.db.model.Purchase;
import org.json.JSONObject;

public class BeforeValidatePurchase extends BeforeModelValidateExtension<Purchase> {
    static {
        registerExtension(new BeforeValidatePurchase());
    }
    @Override
    public void beforeValidate(Purchase model) {
        if (model.getId() > 0 && !model.getReflector().isVoid(model.getPaymentReference()) && !model.isCaptured()){
            TaskManager.instance().executeAsync(new PaymentCapture(model.getId()),false);
            return;
        }

        Boolean authorizedPaymentUpdate = Database.getInstance().getCurrentTransaction().getAttribute("X-AuthorizedPaymentUpdate");
        if (authorizedPaymentUpdate == null){
            authorizedPaymentUpdate = false;
        }
        if (model.getRawRecord().isFieldDirty("CAPTURED") && model.isCaptured() && authorizedPaymentUpdate){ // Being captured
            String paymentId = model.getPaymentReference();
            RazorpayClient razorpay ;
            MultiException ex = new MultiException("Payment Transaction Failed for " + paymentId);
            try {
                razorpay = model.createRazorpayClient();
                Payment payment = razorpay.payments.fetch(paymentId);
                model.setCaptured(payment.get("captured"));
                model.setStatus(payment.get("status"));
                model.setPaymentJson(new StringReader(payment.toString()));
                int authAmount = model.getReflector().getJdbcTypeHelper().getTypeRef(Integer.class).getTypeConverter().valueOf(payment.get("amount"));
                int invoicedAmount = new DoubleHolder(model.getPlan().getSellingPrice()*100,0).getHeldDouble().intValue();
                if (!model.isCaptured()){
                    if (PaymentStatus.authorized.toString().equals(model.getStatus())){
                        if (authAmount == invoicedAmount){
                            JSONObject captureRequest = new JSONObject();
                            captureRequest.put("amount", invoicedAmount); // Amount should be in paise
                            payment = razorpay.payments.capture(paymentId, captureRequest);
                            model.setCaptured(payment.get("captured"));
                            model.setPaymentJson(new StringReader(payment.toString()));
                            model.setStatus(payment.get("status"));
                        }else {
                            throw new RazorpayException("Invoiced Amount != authorized Amount. Razor Pay will fail" );
                        }
                    }
                }
            } catch (RazorpayException e) {
                if (e.getMessage().contains("This payment has already been captured")){
                    model.setCaptured(true);
                }else {
                    ex.add(e);
                    throw ex;
                }
            }
        }
    }
}
