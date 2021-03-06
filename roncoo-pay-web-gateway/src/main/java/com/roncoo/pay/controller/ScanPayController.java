/*
 * Copyright 2015-2102 RonCoo(http://www.roncoo.com) Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.roncoo.pay.controller;

import com.roncoo.pay.common.core.enums.PayWayEnum;
import com.roncoo.pay.common.core.exception.BizException;
import com.roncoo.pay.common.core.utils.DateUtils;
import com.roncoo.pay.common.core.utils.StringUtil;
import com.roncoo.pay.controller.common.BaseController;
import com.roncoo.pay.service.CnpPayService;
import com.roncoo.pay.trade.bo.ScanPayRequestBo;
import com.roncoo.pay.trade.exception.TradeBizException;
import com.roncoo.pay.trade.service.RpTradePaymentManagerService;
import com.roncoo.pay.trade.service.RpTradePaymentQueryService;
import com.roncoo.pay.trade.utils.MerchantApiUtil;
import com.roncoo.pay.trade.utils.WeixinConfigUtil;
import com.roncoo.pay.trade.vo.OrderPayResultVo;
import com.roncoo.pay.trade.vo.RpPayGateWayPageShowVo;
import com.roncoo.pay.trade.vo.ScanPayResultVo;
import com.roncoo.pay.user.entity.RpUserPayConfig;
import com.roncoo.pay.user.exception.UserBizException;
import com.roncoo.pay.user.service.RpUserPayConfigService;
import com.roncoo.pay.utils.JsonUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * <b>????????????:?????????????????????
 * </b>
 *
 * @author Peter
 * <a href="http://www.roncoo.com">????????????(www.roncoo.com)</a>
 */
@Controller
@RequestMapping(value = "/scanPay")
public class ScanPayController extends BaseController {

    private static final Logger logger = LoggerFactory.getLogger(ScanPayController.class);

    @Autowired
    private RpTradePaymentManagerService rpTradePaymentManagerService;

    @Autowired
    private RpTradePaymentQueryService rpTradePaymentQueryService;

    @Autowired
    private CnpPayService cnpPayService;

    /**
     * ????????????,???????????????
     * ???????????????????????????,???????????????????????????
     * ???????????????????????????????????????????????????????????????,???????????????????????????
     * 1:????????????????????????,??????????????????????????????????????????
     * 2:???????????????????????????,?????????
     *
     * @return
     */
    @RequestMapping("/initPay")
    public String initPay(@ModelAttribute ScanPayRequestBo scanPayRequestBo, BindingResult bindingResult, Model model, HttpServletRequest httpServletRequest) {
        logger.info("======>??????????????????{}" , scanPayRequestBo);

    try{
        RpUserPayConfig rpUserPayConfig = cnpPayService.checkParamAndGetUserPayConfig(scanPayRequestBo,  bindingResult, httpServletRequest);

        if (StringUtil.isEmpty(scanPayRequestBo.getPayType())) {//???????????????
            logger.info("======>??????????????????????????????");
            RpPayGateWayPageShowVo payGateWayPageShowVo = rpTradePaymentManagerService.initNonDirectScanPay( rpUserPayConfig , scanPayRequestBo);
            model.addAttribute("payGateWayPageShowVo", payGateWayPageShowVo);//????????????????????????
            return "gateway";

        } else {//????????????
            logger.info("======>???????????????????????????");
            BigDecimal orderPrice = scanPayRequestBo.getOrderPrice();
            ScanPayResultVo scanPayResultVo = rpTradePaymentManagerService.initDirectScanPay(rpUserPayConfig , scanPayRequestBo);

            model.addAttribute("codeUrl", scanPayResultVo.getCodeUrl());//???????????????

            if (PayWayEnum.WEIXIN.name().equals(scanPayResultVo.getPayWayCode())) {
                model.addAttribute("queryUrl", WeixinConfigUtil.readConfig("order_query_url") + "?orderNO=" + scanPayRequestBo.getOrderNo() + "&payKey=" + scanPayRequestBo.getPayKey());
                model.addAttribute("productName", scanPayRequestBo.getProductName());//????????????
                model.addAttribute("orderPrice", orderPrice);//????????????
                model.addAttribute("orderNo", scanPayRequestBo.getOrderNo());//?????????
                model.addAttribute("payKey", scanPayRequestBo.getPayKey());//??????Key

                return "weixinPayScanPay";
            } else if (PayWayEnum.ALIPAY.name().equals(scanPayResultVo.getPayWayCode())) {
                return "alipayDirectPay";
            }
        }
        return "gateway";

        } catch (BizException e) {
            logger.error("????????????:", e);
            model.addAttribute("errorMsg", e.getMsg());//????????????
            return "exception/exception";

        } catch (Exception e) {
            logger.error("????????????:", e);
            model.addAttribute("errorMsg", "????????????");//????????????
            return "exception/exception";
        }

    }

    @RequestMapping("/toPay/{orderNo}/{payType}/{payKey}")
    public String toPay(@PathVariable("payKey") String payKey, @PathVariable("orderNo") String orderNo, @PathVariable("payType") String payType, Model model) {

        ScanPayResultVo scanPayResultVo = rpTradePaymentManagerService.toNonDirectScanPay(payKey, orderNo, payType , 3);

        model.addAttribute("codeUrl", scanPayResultVo.getCodeUrl());//???????????????

        if (PayWayEnum.WEIXIN.name().equals(scanPayResultVo.getPayWayCode())) {
            model.addAttribute("queryUrl", WeixinConfigUtil.readConfig("order_query_url") + "?orderNO=" + orderNo + "&payKey=" + payKey);
            model.addAttribute("productName", scanPayResultVo.getProductName());//????????????
            model.addAttribute("orderPrice", scanPayResultVo.getOrderAmount());//????????????
            return "weixinPayScanPay";
        } else if (PayWayEnum.ALIPAY.name().equals(scanPayResultVo.getPayWayCode())) {
            return "alipayDirectPay";
        }

        return null;
    }

    /**
     * ????????????????????????
     *
     * @param httpServletResponse
     */
    @RequestMapping("orderQuery")
    public void orderQuery(HttpServletResponse httpServletResponse) throws IOException {

        String payKey = getString_UrlDecode_UTF8("payKey"); // ????????????KEY
        String orderNO = getString_UrlDecode_UTF8("orderNO"); // ?????????

        OrderPayResultVo payResult = rpTradePaymentQueryService.getPayResult(payKey, orderNO);
        httpServletResponse.setContentType("text/text;charset=UTF-8");
        JsonUtils.responseJson(httpServletResponse, payResult);

    }

}
