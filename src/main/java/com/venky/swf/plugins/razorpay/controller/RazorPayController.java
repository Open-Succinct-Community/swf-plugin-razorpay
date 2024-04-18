package com.venky.swf.plugins.razorpay.controller;

import com.venky.core.string.StringUtil;
import com.venky.swf.controller.Controller;
import com.venky.swf.controller.annotations.RequireLogin;
import com.venky.swf.db.annotations.column.ui.mimes.MimeType;
import com.venky.swf.db.model.SWFHttpResponse;
import com.venky.swf.integration.FormatHelper;
import com.venky.swf.integration.IntegrationAdaptor;
import com.venky.swf.path.Path;
import com.venky.swf.plugins.background.core.TaskManager;
import com.venky.swf.plugins.razorpay.agent.RazorPayMessageHandler;
import com.venky.swf.views.View;

import java.io.IOException;

public class RazorPayController extends Controller {
    public RazorPayController(Path path) {
        super(path);
    }

    @RequireLogin(false)
    public View push() throws IOException {
        String signature = getPath().getRequest().getHeader("X-Razorpay-Signature");
        String payLoad = StringUtil.read(getPath().getInputStream());

        TaskManager.instance().executeAsync(new RazorPayMessageHandler(signature, payLoad), true);


        return IntegrationAdaptor.instance(SWFHttpResponse.class, FormatHelper.getFormatClass(MimeType.APPLICATION_JSON)).createStatusResponse(getPath(), null);
    }


}
