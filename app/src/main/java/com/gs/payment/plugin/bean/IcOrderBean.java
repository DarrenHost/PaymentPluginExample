package com.gs.payment.plugin.bean;

import java.util.List;

/**
 * @author Leyi
 * @date 2024/9/21 16:34
 */
public class IcOrderBean {

    /**
     * deviceId : lt0000001
     * orderNo :
     * amountMode : 1
     * detailsItem : [{"orderNo":"24040715123144065485","orderDate":"2024-04-07","mealName":"下午茶","stallsList":[{"stallsName":"东北菜档口","stallsId":"402881198d4364b4018d4368f00f0018","foodList":[]}],"orderPrice":2.2,"discountPrice":0,"payPrice":2.2,"payMode":0,"mealCardPay":2.2,"cashPay":null,"wechatPay":null,"alipayPay":null,"otherPayMethodName":"","otherPay":null,"amountPay":2.2,"walletsPay":[{"name":"生活补助","amount":5,"type":0}]}]
     * createTime : 2024-04-07 15:12:31
     * name : 张三
     * memberCategory : 东北国际医院
     * cardId : KCS02000000000005
     * cardNo : null
     * amount : 151.8
     * wallets : [{"name":"生活补助","amount":0,"type":0}]
     */

    private List<DetailsItemBean> detailsItem;


    public List<DetailsItemBean> getDetailsItem() {
        return detailsItem;
    }

    public void setDetailsItem(List<DetailsItemBean> detailsItem) {
        this.detailsItem = detailsItem;
    }

    public static class DetailsItemBean {
        /**
         * orderNo : 24040715123144065485
         * orderDate : 2024-04-07
         * mealName : 下午茶
         * stallsList : [{"stallsName":"东北菜档口","stallsId":"402881198d4364b4018d4368f00f0018","foodList":[]}]
         * orderPrice : 2.2
         * discountPrice : 0
         * payPrice : 2.2
         * payMode : 0
         * mealCardPay : 2.2
         * cashPay : null
         * wechatPay : null
         * alipayPay : null
         * otherPayMethodName :
         * otherPay : null
         * amountPay : 2.2
         * walletsPay : [{"name":"生活补助","amount":5,"type":0}]
         */

        private String orderNo;

        public String getOrderNo() {
            return orderNo;
        }

        public void setOrderNo(String orderNo) {
            this.orderNo = orderNo;
        }


        public static class StallsListBean {
            /**
             * stallsName : 东北菜档口
             * stallsId : 402881198d4364b4018d4368f00f0018
             * foodList : []
             */

            private String stallsName;
            private String stallsId;
            private List<Object> foodList;

            public String getStallsName() {
                return stallsName;
            }

            public void setStallsName(String stallsName) {
                this.stallsName = stallsName;
            }

            public String getStallsId() {
                return stallsId;
            }

            public void setStallsId(String stallsId) {
                this.stallsId = stallsId;
            }

            public List<Object> getFoodList() {
                return foodList;
            }

            public void setFoodList(List<Object> foodList) {
                this.foodList = foodList;
            }
        }

        public static class WalletsPayBean {
            /**
             * name : 生活补助
             * amount : 5
             * type : 0
             */

            private String name;
            private int amount;
            private int type;

            public String getName() {
                return name;
            }

            public void setName(String name) {
                this.name = name;
            }

            public int getAmount() {
                return amount;
            }

            public void setAmount(int amount) {
                this.amount = amount;
            }

            public int getType() {
                return type;
            }

            public void setType(int type) {
                this.type = type;
            }
        }
    }

    public static class WalletsBean {
        /**
         * name : 生活补助
         * amount : 0
         * type : 0
         */

        private String name;
        private int amount;
        private int type;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }
    }
}
