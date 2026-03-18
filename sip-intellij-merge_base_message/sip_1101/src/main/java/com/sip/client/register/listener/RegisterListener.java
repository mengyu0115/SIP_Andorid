package com.sip.client.register.listener;

import com.sip.client.register.SipRegisterManager;
import lombok.extern.slf4j.Slf4j;

import javax.sip.*;
import javax.sip.message.Request;
import javax.sip.message.Response;

/**
 * 注册事件监听器
 *
 * 负责监听SIP注册相关事件并回调到UI层
 *
 * @author SIP Team - Member 1
 * @version 1.0
 */
@Slf4j
public class RegisterListener implements SipListener {

    private RegisterCallback callback;

    public RegisterListener(RegisterCallback callback) {
        this.callback = callback;
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        // 客户端一般不需要处理请求
        Request request = requestEvent.getRequest();
        log.debug("收到请求: {}", request.getMethod());
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        Response response = responseEvent.getResponse();
        int statusCode = response.getStatusCode();

        log.info("收到响应: {} {}", statusCode, response.getReasonPhrase());

        // 注册成功
        if (statusCode == Response.OK) {
            String method = ((gov.nist.javax.sip.message.SIPResponse) response)
                .getCSeq().getMethod();

            if (Request.REGISTER.equals(method)) {
                log.info("✅ SIP注册成功");
                if (callback != null) {
                    callback.onRegisterSuccess("注册成功");
                }
            }
        }
        // 需要认证
        else if (statusCode == Response.UNAUTHORIZED ||
                 statusCode == Response.PROXY_AUTHENTICATION_REQUIRED) {
            log.info("🔐 需要认证，正在处理...");
            if (callback != null) {
                callback.onAuthenticationRequired();
            }
        }
        // 注册失败
        else if (statusCode >= 400) {
            log.error("❌ 注册失败: {} {}", statusCode, response.getReasonPhrase());
            if (callback != null) {
                callback.onRegisterFailed(statusCode, response.getReasonPhrase());
            }
        }
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        log.error("⏰ 请求超时");
        if (callback != null) {
            callback.onRegisterFailed(408, "请求超时");
        }
    }

    @Override
    public void processIOException(IOExceptionEvent exceptionEvent) {
        log.error("💥 IO异常: {}", exceptionEvent.toString());
        if (callback != null) {
            callback.onRegisterFailed(500, "网络异常");
        }
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        log.debug("事务终止");
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        log.debug("对话终止");
    }

    /**
     * 注册回调接口
     */
    public interface RegisterCallback {
        /**
         * 注册成功
         */
        void onRegisterSuccess(String message);

        /**
         * 需要认证
         */
        void onAuthenticationRequired();

        /**
         * 注册失败
         */
        void onRegisterFailed(int statusCode, String reason);
    }
}
