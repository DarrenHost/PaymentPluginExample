package com.gs.payment.plugin.manager;

/**
 * Created by LiBaoZhi
 * on 2026/1/26
 */

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TransactionParser {

    public static void main(String[] args) {
        String transactionData = "0010031010030073412345004006000260013003000002012000000000001012003504014008260120260150060342490070164984513124707724017004330800800600001501099903000200031001G032001C033004Naps*03000202031001S032001G033032--------------------------------*03000203031001S032001G03301026/01/2026*03000204031001S032001G033024TIMLIF                  *03000205031001S032001G033000*03000206031001S032001G033013TEST         *03000207031001S032001G033032--------------------------------*03000209031001S032001G033011VISA CREDIT*03000210031001S032001G033016498451&zwnj;******&zwnj;7724*03000211031001S032001G03302033/08CARTE ETRANGERE*03000213031001S032001G033000*03000214031001S032001G033023N° Commer?ant : 2000009*03000215031001S032001G033026N° Terminal : 88398363    *03000217031001S032001G033023N° Transaction : 000015*03000218031001S032001G033024N° Autorisation : 135944*03000219031001S032001G033016N° STAN : 000015*03000220031001S032001G033032--------------------------------*03000221031001S032001G033018MONTANT : 0,01 MAD*03000222031001S032001G033032--------------------------------*03000223031001S032001G033005DEBIT*03000224031001S032001G033016Copie Commer?ant*03000225031001S032001G033032--------------------------------*03000226031001S032001G033034Conservez-moi, je peux être utile!*03000227031001S032001G033011www.naps.ma";

        // 解析交易数据
        Map<String, String> transactionInfo = parseTransactionData(transactionData);

        // 输出解析结果
        System.out.println("=== 交易数据解析结果 ===");
        for (Map.Entry<String, String> entry : transactionInfo.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue());
        }
    }

    public static Map<String, String> parseTransactionData(String data) {
        Map<String, String> result = new HashMap<>();

        // 1. 提取订单号 (008006000015)
        Pattern orderPattern = Pattern.compile("0080060\\d{5}");
        Matcher orderMatcher = orderPattern.matcher(data);
        if (orderMatcher.find()) {
            result.put("订单号", orderMatcher.group());
        }

        // 2. 提取银行卡号 (0070164984513124707724)
        Pattern cardPattern = Pattern.compile("007016\\d{16}");
        Matcher cardMatcher = cardPattern.matcher(data);
        if (cardMatcher.find()) {
            result.put("银行卡号", cardMatcher.group());
        }

        // 3. 提取有效时间 (0170043308)
        Pattern expiryPattern = Pattern.compile("01700\\d{5}");
        Matcher expiryMatcher = expiryPattern.matcher(data);
        if (expiryMatcher.find()) {
            result.put("有效时间", expiryMatcher.group());
        }

        // 4. 提取交易日期
        Pattern datePattern = Pattern.compile("\\d{2}/\\d{2}/\\d{4}");
        Matcher dateMatcher = datePattern.matcher(data);
        if (dateMatcher.find()) {
            result.put("交易日期", dateMatcher.group());
        }

        // 5. 提取商户名称
        if (data.contains("TIMLIF")) {
            int start = data.indexOf("TIMLIF");
            int end = data.indexOf("*", start);
            if (end != -1) {
                String merchantName = data.substring(start, end).trim();
                result.put("商户名称", merchantName);
            }
        }

        // 6. 提取商户编号
        if (data.contains("N° Commer?ant :")) {
            int start = data.indexOf("N° Commer?ant :");
            int end = data.indexOf("*", start);
            if (end != -1) {
                String merchantId = data.substring(start + 15, end).trim();
                result.put("商户编号", merchantId);
            }
        }

        // 7. 提取终端编号
        if (data.contains("N° Terminal :")) {
            int start = data.indexOf("N° Terminal :");
            int end = data.indexOf("*", start);
            if (end != -1) {
                String terminalId = data.substring(start + 13, end).trim();
                result.put("终端编号", terminalId);
            }
        }

        // 8. 提取交易金额
        if (data.contains("MONTANT :")) {
            int start = data.indexOf("MONTANT :");
            int end = data.indexOf("MAD", start);
            if (end != -1) {
                String amount = data.substring(start, end + 3).trim();
                result.put("交易金额", amount);
            }
        }

        // 9. 提取交易类型
        if (data.contains("DEBIT")) {
            result.put("交易类型", "DEBIT");
        }

        // 10. 提取卡类型
        if (data.contains("VISA CREDIT")) {
            result.put("卡类型", "VISA CREDIT");
        }

        // 11. 提取网站信息
        if (data.contains("www.naps.ma")) {
            result.put("商户网站", "www.naps.ma");
        }

        return result;
    }

    // 增强版解析：使用字段标识符解析
    public static Map<String, String> parseByFieldIdentifiers(String data) {
        Map<String, String> result = new HashMap<>();

        // 定义字段标识符和对应的字段名
        Map<String, String> fieldMappings = new HashMap<>();
        fieldMappings.put("N° Commer?ant :", "商户编号");
        fieldMappings.put("N° Terminal :", "终端编号");
        fieldMappings.put("N° Transaction :", "交易流水号");
        fieldMappings.put("N° Autorisation :", "授权号");
        fieldMappings.put("N° STAN :", "STAN号");
        fieldMappings.put("MONTANT :", "交易金额");

        // 遍历所有字段标识符
        for (Map.Entry<String, String> entry : fieldMappings.entrySet()) {
            String identifier = entry.getKey();
            String fieldName = entry.getValue();

            if (data.contains(identifier)) {
                int start = data.indexOf(identifier);
                int end = data.indexOf("*", start);
                if (end != -1) {
                    String value = data.substring(start + identifier.length(), end).trim();
                    result.put(fieldName, value);
                }
            }
        }

        return result;
    }
}
