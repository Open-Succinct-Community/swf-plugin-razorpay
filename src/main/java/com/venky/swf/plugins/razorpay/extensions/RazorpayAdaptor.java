package com.venky.swf.plugins.razorpay.extensions;

import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import com.venky.core.util.ObjectUtil;
import com.venky.swf.db.Database;
import com.venky.swf.db.model.application.Application;
import com.venky.swf.db.model.application.ApplicationUtil;
import com.venky.swf.plugins.payments.db.model.payment.gateway.PaymentGateway;

import com.venky.swf.plugins.payments.db.model.payment.gateway.PaymentLink;
import com.venky.swf.plugins.payments.gateway.PaymentGatewayAdaptor;
import com.venky.swf.plugins.payments.gateway.PaymentGatewayAdaptorFactory;
import com.venky.swf.routing.Config;
import in.succinct.beckn.BecknObject;
import in.succinct.beckn.BecknObjectWithId;
import in.succinct.beckn.Invoice.Invoices;
import in.succinct.beckn.Order;
import in.succinct.beckn.Payment.PaymentStatus;
import in.succinct.beckn.Request;
import in.succinct.json.JSONAwareWrapper;
import org.json.JSONTokener;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.Map;
import java.util.logging.Logger;

public class RazorpayAdaptor extends PaymentGatewayAdaptor{
    static{
        PaymentGatewayAdaptorFactory.getInstance().register("Razorpay",RazorpayAdaptor.class);
    }
    
    
    RazorpayClient client ;
    public RazorpayAdaptor(PaymentGateway paymentGateway){
        super(paymentGateway);
        try {
            this.client =  new RazorpayClient(getCred("key"),getCred("secret"));
        } catch (RazorpayException e) {
            throw new RuntimeException(e);
        }
    }
    /*
    curl -u [YOUR_KEY_ID]:[YOUR_KEY_SECRET] \
        -X POST https://api.razorpay.com/v1/payment_links/ \
        -H 'Content-type: application/json' \
        -d '{
          "amount": 1000,
          "currency": "INR",
          "accept_partial": true,
          "first_min_partial_amount": 100,
          "expire_by": 1691097057,
          "reference_id": "TS1989",
          "description": "Payment for policy no #23456",
          "customer": {
            "name": "Gaurav Kumar",
            "contact": "+919000090000",
            "email": "gaurav.kumar@example.com"
          },
          "notify": {
            "sms": true,
            "email": true
          },
          "reminder_enable": true,
          "notes": {
            "policy_name": "Jeevan Bima"
          },
          "callback_url": "https://example-callback-url.com/",
          "callback_method": "get"
        }'
     */
    public void createPaymentLink(Request request){
        Order order = request.getMessage().getOrder();
        
        Invoices invoices = order.getInvoices();
        invoices.forEach(invoice -> {
            if (invoice.getPaymentTransactions().isEmpty()){
                String paymentUri = invoice.getTag("payment_link","uri");
                
                if (!ObjectUtil.isVoid(paymentUri)) {
                    String paymentId = invoice.getTag("payment_link","id");
                    cancelPaymentLink(paymentId);
                }
                RazorpayPaymentLink razorpayPaymentLink = new RazorpayPaymentLink();
                razorpayPaymentLink.setReferenceId("%s".formatted(invoice.getId()));
                razorpayPaymentLink.setAmount((int)(invoice.getUnpaidAmount().doubleValue() * 100.0));
                razorpayPaymentLink.setCustomer(new Customer(){{
                    setName(order.getBilling().getName());
                    setContact(order.getBilling().getPhone());
                    setEmail(order.getBilling().getEmail());
                }});
                try {
                    RazorpayPaymentLink output = new RazorpayPaymentLink(client.paymentLink.create(new org.json.JSONObject(new JSONTokener(razorpayPaymentLink.toString()))).toString());
                    invoice.setTag("payment_link","uri",output.getShortUrl());
                    invoice.setTag("payment_link","id",output.getId());
                    
                    PaymentLink paymentLink = Database.getTable(PaymentLink.class).newRecord();
                    paymentLink.setApplicationId(ApplicationUtil.find(request.getContext().getBppId()).getId());
                    paymentLink.setLinkUri(output.getShortUrl());
                    paymentLink.setStatus(PaymentStatus.NOT_PAID.name());
                    paymentLink.setStatusCommunicated(false);
                    paymentLink.setTxnReference("%s/%s/%s".formatted(request.getContext().getTransactionId(),invoice.getId(),output.getId()));
                    paymentLink.save();
                    
                    
                } catch (RazorpayException e) {
                    throw new RuntimeException(e);
                }
            }
        });
    }
    
    @Override
    public void recordPayment(String eventJson, Map<String,String> headers){
        
        String signature = headers.get("X-Razorpay-Signature");
        try {
            Logger cat = Config.instance().getLogger(getClass().getName());
            
            cat.info("Sign:" + signature);
            cat.info("Payload:" + eventJson);
        
            if (!Utils.verifyWebhookSignature(eventJson,signature,getCred("secret"))){
                return;
            }
            JSONObject jsonObject = JSONAwareWrapper.parse(eventJson);
            JSONArray contains = (JSONArray) jsonObject.get("contains");
            if (!contains.contains("payment_link")){
                return;
            }
            if (!ObjectUtil.equals(jsonObject.get("event"),"payment_link.paid")){
                return;
            }
            RazorpayPaymentLink razorpayPaymentLink = new RazorpayPaymentLink((JSONObject)
                    ((JSONObject)
                            ((JSONObject)
                                    jsonObject.get("payload")
                            ).get("payment_link")
                    ).get("entity"));
            PaymentLink paymentLink = Database.getTable(PaymentLink.class).newRecord();
            paymentLink.setLinkUri(razorpayPaymentLink.getShortUrl());
            paymentLink = Database.getTable(PaymentLink.class).getRefreshed(paymentLink);
            paymentLink.setAmountPaid(razorpayPaymentLink.getAmountPaid()/100.0);
            switch (razorpayPaymentLink.getRazorpayLinkStatus()){
                case paid -> {
                    paymentLink.setStatus(PaymentStatus.COMPLETE.name());
                    paymentLink.setActive(false);
                }
                case cancelled,expired -> {
                    paymentLink.setStatus(PaymentStatus.NOT_PAID.name());
                    paymentLink.setActive(false);
                }
                default -> {
                    paymentLink.setStatus(PaymentStatus.NOT_PAID.name());
                    paymentLink.setActive(true);
                }
            }
            paymentLink.save();
            
        } catch (RazorpayException e) {
            throw new RuntimeException(e);
        }
        
    }
    
    private void cancelPaymentLink(String paymentId) {
        try {
            client.paymentLink.cancel(paymentId);
        } catch (RazorpayException e) {
            throw new RuntimeException(e);
        }
    }
    private static class Notify extends BecknObject {
        public Notify() {
            super();
            setSms(true);
        }
        public boolean isSms(){
            return getBoolean("sms");
        }
        public void setSms(boolean sms){
            set("sms",sms);
        }
        
        public boolean isEmail(){
            return getBoolean("email");
        }
        public void setEmail(boolean email){
            set("email",email);
        }
        
    }
    private static class RazorpayPaymentLink extends BecknObjectWithId {
        public RazorpayPaymentLink() {
            super();
            setAcceptPartial(false);
            setCurrency("INR");
            setNotify(new Notify());
            setReminderEnable(true);
        }
        
        public RazorpayPaymentLink(String payload) {
            super(payload);
        }
        
        public RazorpayPaymentLink(JSONObject object) {
            super(object);
        }
        public Notify getNotify(){
            return get(Notify.class, "notify");
        }
        public void setNotify(Notify notify){
            set("notify",notify);
        }
        
        public String getDescription(){
            return get("description");
        }
        public void setDescription(String description){
            set("description",description);
        }
        
        public boolean isAcceptPartial(){
            return getBoolean("accept_partial");
        }
        public void setAcceptPartial(boolean accept_partial){
            set("accept_partial",accept_partial);
        }
        
        public boolean isReminderEnable(){
            return getBoolean("reminder_enable");
        }
        public void setReminderEnable(boolean reminder_enable){
            set("reminder_enable",reminder_enable);
        }
        
        
        
        @Override
        public void setInner(JSONObject value) {
            super.setInner(value);
            
        }
        
        public String getReferenceId(){
            return get("reference_id");
        }
        public void setReferenceId(String reference_id){
            set("reference_id",reference_id);
            setDescription("Payment for invoice #%s".formatted(reference_id));
        }
        
        
        public RazorpayLinkStatus getRazorpayLinkStatus(){
            return getEnum(RazorpayLinkStatus.class, "status");
        }
        public void setRazorpayLinkStatus(RazorpayLinkStatus status){
            setEnum("status",status);
        }
        
        public String getShortUrl(){
            return get("short_url");
        }
        public void setShortUrl(String short_url){
            set("short_url",short_url);
        }
        
        public Customer getCustomer(){
            return get(Customer.class, "customer");
        }
        public void setCustomer(Customer customer){
            set("customer",customer);
        }
        
        public int getAmount(){
            return getInteger("amount");
        }
        public void setAmount(int amount){
            set("amount",amount);
        }
        public int getAmountPaid(){
            return getInteger("amount_paid");
        }
        public void setAmountPaid(int amount_paid){
            set("amount_paid",amount_paid);
        }
        public String getCurrency(){
            return get("currency");
        }
        public void setCurrency(String currency){
            set("currency",currency);
        }
    }
    
    private static class Customer extends BecknObject {
        public String getEmail(){
            return get("email");
        }
        public void setEmail(String email){
            set("email",email);
        }
        public String getContact(){
            return get("contact");
        }
        public void setContact(String contact){
            set("contact",contact);
        }
        public String getName(){
            return get("name");
        }
        public void setName(String name){
            set("name",name);
        }
        
        
    
    }
    
    public enum RazorpayLinkStatus {
        created,
        partially_paid,
        paid,
        expired,
        cancelled,
    }
    
   
}
