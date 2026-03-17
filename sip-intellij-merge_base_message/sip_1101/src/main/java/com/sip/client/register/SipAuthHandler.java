package com.sip.client.register;

import lombok.extern.slf4j.Slf4j;

import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Request;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SIP Digest 认证处理器
 * 实现 RFC 2617 Digest 认证算法
 *
 * 认证流程:
 * 1. 服务器返回 401/407 带 nonce, realm 等参数
 * 2. 客户端计算 response = MD5(MD5(A1):nonce:MD5(A2))
 * 3. 客户端发送带 Authorization 头的请求
 *
 */
@Slf4j
public class SipAuthHandler {

    /**
     * 生成 Authorization/Proxy-Authorization 头
     *
     * @param request 原始请求
     * @param authHeader 服务器返回的认证质询头
     * @param username 用户名
     * @param password 密码
     * @param headerName 头名称 (Authorization 或 Proxy-Authorization)
     * @return Authorization 头
     */
    public AuthorizationHeader generateAuthHeader(
            Request request,
            WWWAuthenticateHeader authHeader,
            String username,
            String password,
            String headerName) throws Exception {

        // 获取认证参数
        String realm = authHeader.getRealm();
        String nonce = authHeader.getNonce();
        String algorithm = authHeader.getAlgorithm();
        String qop = authHeader.getQop();
        String opaque = authHeader.getOpaque();

        if (algorithm == null) {
            algorithm = "MD5";
        }

        log.debug("认证参数: realm={}, nonce={}, algorithm={}, qop={}", realm, nonce, algorithm, qop);

        // 计算 response
        String method = request.getMethod();
        SipURI requestURI = (SipURI) request.getRequestURI();
        // 修复: URI需要包含端口号
        String uri = "sip:" + requestURI.getHost();
        if (requestURI.getPort() != -1) {
            uri += ":" + requestURI.getPort();
        }

        String response = calculateResponse(username, realm, password, method, uri, nonce, qop, algorithm);

        log.debug("计算的 response: {}", response);

        // 创建 Authorization 头
        AuthorizationHeader authorization = null;

        if (headerName.equals(AuthorizationHeader.NAME)) {
            authorization = (AuthorizationHeader) request.getHeader(AuthorizationHeader.NAME);
            if (authorization == null) {
                authorization = new gov.nist.javax.sip.header.Authorization();
            }
        } else {
            authorization = (AuthorizationHeader) request.getHeader(ProxyAuthorizationHeader.NAME);
            if (authorization == null) {
                authorization = new gov.nist.javax.sip.header.ProxyAuthorization();
            }
        }

        authorization.setScheme("Digest");
        authorization.setUsername(username);
        authorization.setRealm(realm);
        authorization.setNonce(nonce);
        authorization.setURI(requestURI);
        authorization.setResponse(response);
        authorization.setAlgorithm(algorithm);

        if (opaque != null) {
            authorization.setOpaque(opaque);
        }

        if (qop != null) {
            authorization.setQop(qop);
            authorization.setNonceCount(1);
            authorization.setCNonce(generateCNonce());
        }

        return authorization;
    }

    /**
     * 计算 Digest response
     * response = MD5(HA1:nonce:HA2)
     * HA1 = MD5(username:realm:password)
     * HA2 = MD5(method:uri)
     */
    public String calculateResponse(String username, String realm, String password,
                                    String method, String uri, String nonce,
                                    String qop, String algorithm) {
        try {
            // 计算 HA1
            String a1 = username + ":" + realm + ":" + password;
            String ha1 = md5(a1);

            // 计算 HA2
            String a2 = method + ":" + uri;
            String ha2 = md5(a2);

            // 计算 response
            String responseStr;
            if (qop != null && qop.equals("auth")) {
                String nc = "00000001"; // nonce count
                String cnonce = generateCNonce();
                responseStr = ha1 + ":" + nonce + ":" + nc + ":" + cnonce + ":" + qop + ":" + ha2;
            } else {
                responseStr = ha1 + ":" + nonce + ":" + ha2;
            }

            return md5(responseStr);

        } catch (Exception e) {
            log.error("计算 response 失败", e);
            return null;
        }
    }

    /**
     * MD5 哈希
     */
    private String md5(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(input.getBytes());
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            log.error("MD5 算法不存在", e);
            return null;
        }
    }

    /**
     * 生成客户端 nonce (CNonce)
     */
    private String generateCNonce() {
        long time = System.currentTimeMillis();
        return md5(String.valueOf(time));
    }
}