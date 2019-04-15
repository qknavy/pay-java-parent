package com.egzosn.pay.yiji.api;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.egzosn.pay.common.api.BasePayService;
import com.egzosn.pay.common.bean.*;
import com.egzosn.pay.common.bean.result.PayException;
import com.egzosn.pay.common.exception.PayErrorException;
import com.egzosn.pay.common.http.HttpConfigStorage;
import com.egzosn.pay.common.http.UriVariables;
import com.egzosn.pay.common.util.MatrixToImageWriter;
import com.egzosn.pay.common.util.Util;
import com.egzosn.pay.common.util.sign.SignUtils;
import com.egzosn.pay.common.util.str.StringUtils;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.util.*;

/**
 * 易极付支付服务
 *
 * @author egan
 *         <p>
 *         email egzosn@gmail.com
 *          * date 2019/04/15 22:51
 */
public class YiJiPayService extends BasePayService<YiJiPayConfigStorage> {

    /**
     * 正式测试环境
     */
    private static final String HTTPS_REQ_URL = "https://api.yiji.com";
    /**
     * 沙箱测试环境账号
     */
    private static final String DEV_REQ_URL = "https://openapi.yijifu.net/gateway.html";

    public static final String SIGN = "sign";

    public static final String SUCCESS_CODE = "10000";

    public static final String CODE = "code";
    /**
     * 附加参数
     */
    public static final String PASSBACK_PARAMS = "passback_params";
    /**
     * 产品代码
     */
    public static final String PRODUCT_CODE = "product_code";
    /**
     * 返回地址
     */
    public static final String RETURN_URL = "return_url";

    /**
     * 请求内容
     */
    public static final String BIZ_CONTENT = "biz_content";
    /**
     * 获取对应的请求地址
     *
     * @return 请求地址
     */
    public String getReqUrl(TransactionType transactionType) {
        return payConfigStorage.isTest() ? DEV_REQ_URL : HTTPS_REQ_URL;
    }
    /**
     * 获取对应的请求地址
     *
     * @return 请求地址
     */
    public String getReqUrl() {
        return getReqUrl(null);
    }


    public YiJiPayService(YiJiPayConfigStorage payConfigStorage, HttpConfigStorage configStorage) {
        super(payConfigStorage, configStorage);
    }

    public YiJiPayService(YiJiPayConfigStorage payConfigStorage) {
        super(payConfigStorage);
    }


    public String getHttpsVerifyUrl() {
        return getReqUrl() + "?service=notify_verify";
    }

    /**
     * 回调校验
     *
     * @param params 回调回来的参数集
     * @return 签名校验 true通过
     */
    @Override
    public boolean verify(Map<String, Object> params) {

        if (params.get(SIGN) == null) {
            LOG.debug("易极付支付异常：params：" + params);
            return false;
        }

        return signVerify(params, (String) params.get(SIGN)) && verifySource((String) params.get("notify_id"));

    }

    /**
     * 根据反馈回来的信息，生成签名结果
     *
     * @param params 通知返回来的参数数组
     * @param sign   比对的签名结果
     * @return 生成的签名结果
     */
    @Override
    public boolean signVerify(Map<String, Object> params, String sign) {

        if (params instanceof JSONObject) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                if (SIGN.equals(entry.getKey())) {
                    continue;
                }
                TreeMap<String, Object> response = new TreeMap((Map<String, Object> )entry.getValue());
                LinkedHashMap<Object, Object> linkedHashMap = new LinkedHashMap<>();
                linkedHashMap.put(CODE, response.remove(CODE));
                linkedHashMap.put("msg", response.remove("msg"));
                linkedHashMap.putAll(response);
                return SignUtils.valueOf(payConfigStorage.getSignType()).verify(JSON.toJSONString(linkedHashMap), sign, payConfigStorage.getKeyPublic(), payConfigStorage.getInputCharset());
            }
        }

        return SignUtils.valueOf(payConfigStorage.getSignType()).verify(params, sign, payConfigStorage.getKeyPublic(), payConfigStorage.getInputCharset());
    }


    /**
     * 校验数据来源
     *
     * @param id 业务id, 数据的真实性.
     * @return true通过
     */
    @Override
    public boolean verifySource(String id) {
        return true;
    }


    /**
     * 生成并设置签名
     *
     * @param parameters 请求参数
     * @return 请求参数
     */
    private Map<String, Object> setSign(Map<String, Object> parameters) {
        parameters.put("sign_type", payConfigStorage.getSignType());
        String sign = createSign(SignUtils.parameterText(parameters, "&", SIGN), payConfigStorage.getInputCharset());

        parameters.put(SIGN, sign);
        return parameters;
    }


    /**
     * 返回创建的订单信息
     *
     * @param order 支付订单
     * @return 订单信息
     * @see PayOrder 支付订单信息
     */
    @Override
    public Map<String, Object> orderInfo(PayOrder order) {

        return setSign(getOrder(order));
    }


    /**
     * 易极付创建订单信息
     * create the order info
     *
     * @param order 支付订单
     * @return 返回易极付预下单信息
     * @see PayOrder 支付订单信息
     */
    private Map<String, Object> getOrder(PayOrder order) {


        Map<String, Object> orderInfo = getPublicParameters(order.getTransactionType());
        orderInfo.put("orderNo", order.getTradeNo());
        orderInfo.put("outOrderNo", order.getOutTradeNo());

        if (StringUtils.isNotEmpty(payConfigStorage.getSeller())){
            orderInfo.put("sellerUserId", payConfigStorage.getSeller());
        }
        orderInfo.put("tradeAmount", Util.conversionAmount(order.getPrice()));
        orderInfo.put("goodsClauses", String.format("[{'name':'%s'}]", order.getBody()));


        return orderInfo;
    }

    /**
     * 获取公共请求参数
     *
     * @return 放回公共请求参数
     */
    private Map<String, Object> getPublicParameters(TransactionType transactionType) {
        Map<String, Object> orderInfo = new TreeMap<>();
        orderInfo.put("partnerId", payConfigStorage.getPid());
        orderInfo.put("signType", payConfigStorage.getSignType());
        orderInfo.put("returnUrl", payConfigStorage.getReturnUrl());
        orderInfo.put("notifyUrl", payConfigStorage.getNotifyUrl());
        orderInfo.put("service", transactionType.getMethod());
        return orderInfo;
    }


    /**
     * 获取输出消息，用户返回给支付端
     *
     * @param code    状态
     * @param message 消息
     * @return 返回输出消息
     */
    @Override
    public PayOutMessage getPayOutMessage(String code, String message) {
        return PayOutMessage.TEXT().content(code.toLowerCase()).build();
    }

    /**
     * 获取成功输出消息，用户返回给支付端
     * 主要用于拦截器中返回
     *
     * @param payMessage 支付回调消息
     * @return 返回输出消息
     */
    @Override
    public PayOutMessage successPayOutMessage(PayMessage payMessage) {
        return PayOutMessage.TEXT().content("success").build();
    }

    /**
     * @param orderInfo 发起支付的订单信息
     * @param method    请求方式  "post" "get",
     * @return 获取输出消息，用户返回给支付端, 针对于web端
     */
    @Override
    public String buildRequest(Map<String, Object> orderInfo, MethodType method) {
        StringBuilder formHtml = new StringBuilder();
        formHtml.append("<form id=\"_alipaysubmit_\" name=\"alipaysubmit\" action=\"");
        String bizContent = (String) orderInfo.remove(BIZ_CONTENT);
        formHtml.append(getReqUrl()).append("?").append(UriVariables.getMapToParameters(orderInfo))
                .append("\" method=\"").append(method.name().toLowerCase()).append("\">");
        formHtml.append("<input type=\"hidden\" name=\"biz_content\" value=\'" + bizContent + "\'/>");
        formHtml.append("</form>");
        formHtml.append("<script>document.forms['_alipaysubmit_'].submit();</script>");

        return formHtml.toString();
    }

    /**
     * 生成二维码支付
     *
     * @param order 发起支付的订单信息
     * @return 返回图片信息，支付时需要的
     */
    @Override
    public BufferedImage genQrPay(PayOrder order) {

        Map<String, Object> orderInfo = orderInfo(order);


        //预订单
        JSONObject result = getHttpRequestTemplate().postForObject(getReqUrl() + "?" + UriVariables.getMapToParameters(orderInfo), null, JSONObject.class);
        JSONObject response = result.getJSONObject("alipay_trade_precreate_response");
        if (SUCCESS_CODE.equals(response.getString(CODE))) {
            return MatrixToImageWriter.writeInfoToJpgBuff(response.getString("qr_code"));
        }
        throw new PayErrorException(new PayException(response.getString(CODE), response.getString("msg"), result.toJSONString()));

    }

    /**
     * pos主动扫码付款(条码付)
     *
     * @param order 发起支付的订单信息
     * @return 支付结果
     */
    @Override
    public Map<String, Object> microPay(PayOrder order) {
        Map<String, Object> orderInfo = orderInfo(order);
        //预订单
        JSONObject result = getHttpRequestTemplate().postForObject(getReqUrl() + "?" + UriVariables.getMapToParameters(orderInfo), null, JSONObject.class);
        JSONObject response = result.getJSONObject("alipay_trade_pay_response");
        if (!SUCCESS_CODE.equals(response.getString(CODE))) {
            LOG.info("收款失败");
        }
        return result;
    }

    /**
     * 交易查询接口
     *
     * @param tradeNo    支付平台订单号
     * @param outTradeNo 商户单号
     * @return 返回查询回来的结果集，支付方原值返回
     */
    @Override
    public Map<String, Object> query(String tradeNo, String outTradeNo) {
        return null;

    }


    /**
     * 交易关闭接口
     *
     * @param tradeNo    支付平台订单号
     * @param outTradeNo 商户单号
     * @return 返回支付方交易关闭后的结果
     */
    @Override
    public Map<String, Object> close(String tradeNo, String outTradeNo) {
        return null;
    }

    /**
     * 支付交易返回失败或支付系统超时，调用该接口撤销交易。
     * 如果此订单用户支付失败，易极付系统会将此订单关闭；如果用户支付成功，易极付系统会将此订单资金退还给用户。
     * 注意：只有发生支付系统超时或者支付结果未知时可调用撤销，其他正常支付的单如需实现相同功能请调用申请退款API。
     * 提交支付交易后调用【查询订单API】，没有明确的支付结果再调用【撤销订单API】。
     *
     * @param tradeNo    支付平台订单号
     * @param outTradeNo 商户单号
     * @return 返回支付方交易撤销后的结果
     */
    @Override
    public Map<String, Object> cancel(String tradeNo, String outTradeNo) {
        return null;
    }

    /**
     * 申请退款接口
     * 废弃
     *
     * @param tradeNo      支付平台订单号
     * @param outTradeNo   商户单号
     * @param refundAmount 退款金额
     * @param totalAmount  总金额
     * @return 返回支付方申请退款后的结果
     * @see #refund(RefundOrder, com.egzosn.pay.common.api.Callback)
     *  @deprecated 版本替代 {@link #refund(RefundOrder, com.egzosn.pay.common.api.Callback)}
     */
    @Deprecated
    @Override
    public Map<String, Object> refund(String tradeNo, String outTradeNo, BigDecimal refundAmount, BigDecimal totalAmount) {
        return refund(new RefundOrder(tradeNo, outTradeNo, refundAmount, totalAmount));
    }


    /**
     * 申请退款接口
     *
     * @param refundOrder 退款订单信息
     * @return 返回支付方申请退款后的结果
     */
    @Override
    public Map<String, Object> refund(RefundOrder refundOrder) {

        return null;
    }

    /**
     * 查询退款
     *
     * @param tradeNo    支付平台订单号
     * @param outTradeNo 商户单号
     * @return 返回支付方查询退款后的结果
     */
    @Override
    public Map<String, Object> refundquery(String tradeNo, String outTradeNo) {
        return null;
    }

    /**
     * 查询退款
     *
     * @param refundOrder 退款订单单号信息
     * @return 返回支付方查询退款后的结果
     */
    @Override
    public Map<String, Object> refundquery(RefundOrder refundOrder) {

        return null;

    }

    /**
     * 目前只支持日账单
     *
     * @param billDate 账单类型，商户通过接口或商户经开放平台授权后其所属服务商通过接口可以获取以下账单类型：trade、signcustomer；trade指商户基于易极付交易收单的业务账单；signcustomer是指基于商户易极付余额收入及支出等资金变动的帐务账单；
     * @param billType 账单时间：日账单格式为yyyy-MM-dd，月账单格式为yyyy-MM。
     * @return 返回支付方下载对账单的结果
     */
    @Override
    public Map<String, Object> downloadbill(Date billDate, String billType) {

        return null;
    }


    /**
     * @param tradeNoOrBillDate  支付平台订单号或者账单类型， 具体请
     *                           类型为{@link String }或者 {@link Date }，类型须强制限制，类型不对应则抛出异常{@link PayErrorException}
     * @param outTradeNoBillType 商户单号或者 账单类型
     * @param transactionType    交易类型
     * @return 返回支付方对应接口的结果
     */
    @Override
    public Map<String, Object> secondaryInterface(Object tradeNoOrBillDate, String outTradeNoBillType, TransactionType transactionType) {


        return null;
    }

    /**
     * 转账
     *
     * @param order 转账订单
     * @return 对应的转账结果
     */
    @Override
    public Map<String, Object> transfer(TransferOrder order) {

        return null;
    }

    /**
     * 转账查询
     *
     * @param outNo   商户转账订单号
     * @param tradeNo 支付平台转账订单号
     * @return 对应的转账订单
     */
    @Override
    public Map<String, Object> transferQuery(String outNo, String tradeNo) {

        return null;
    }

}