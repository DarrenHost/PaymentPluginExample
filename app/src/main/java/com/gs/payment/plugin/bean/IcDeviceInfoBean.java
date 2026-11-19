package com.gs.payment.plugin.bean;

import java.util.List;

/**
 * @author Leyi
 * @date 2024/9/21 16:33
 */
public class IcDeviceInfoBean {

    /**
     * deviceId : lt0000001
     * mealList : [{"id":"402881198d395678018d395e7a3e001e","name":"下午茶","startTime":"00:00:00","endTime":"23:59:59","spanningDay":false,"sort":1}]
     * enableFixed : true
     * fixedList : [{"mealId":"402881198d395678018d395e7a3e001e","amount":2.8}]
     * enableFast : true
     * usablePayPlatforms : [{"type":3,"id":"3","name":"微信"},{"type":0,"id":"402881198d395678018d395e9b65001f","name":"支付宝（线下）"}]
     */

    private String deviceId;
    private boolean enableFixed;
    private boolean enableFast;
    private List<MealListBean> mealList;
    private List<FixedListBean> fixedList;
    private List<UsablePayPlatformsBean> usablePayPlatforms;

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public boolean isEnableFixed() {
        return enableFixed;
    }

    public void setEnableFixed(boolean enableFixed) {
        this.enableFixed = enableFixed;
    }

    public boolean isEnableFast() {
        return enableFast;
    }

    public void setEnableFast(boolean enableFast) {
        this.enableFast = enableFast;
    }

    public List<MealListBean> getMealList() {
        return mealList;
    }

    public void setMealList(List<MealListBean> mealList) {
        this.mealList = mealList;
    }

    public List<FixedListBean> getFixedList() {
        return fixedList;
    }

    public void setFixedList(List<FixedListBean> fixedList) {
        this.fixedList = fixedList;
    }

    public List<UsablePayPlatformsBean> getUsablePayPlatforms() {
        return usablePayPlatforms;
    }

    public void setUsablePayPlatforms(List<UsablePayPlatformsBean> usablePayPlatforms) {
        this.usablePayPlatforms = usablePayPlatforms;
    }

    public static class MealListBean {
        /**
         * id : 402881198d395678018d395e7a3e001e
         * name : 下午茶
         * startTime : 00:00:00
         * endTime : 23:59:59
         * spanningDay : false
         * sort : 1
         */

        private String id;
        private String name;
        private String startTime;
        private String endTime;
        private boolean spanningDay;
        private int sort;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getStartTime() {
            return startTime;
        }

        public void setStartTime(String startTime) {
            this.startTime = startTime;
        }

        public String getEndTime() {
            return endTime;
        }

        public void setEndTime(String endTime) {
            this.endTime = endTime;
        }

        public boolean isSpanningDay() {
            return spanningDay;
        }

        public void setSpanningDay(boolean spanningDay) {
            this.spanningDay = spanningDay;
        }

        public int getSort() {
            return sort;
        }

        public void setSort(int sort) {
            this.sort = sort;
        }
    }

    public static class FixedListBean {
        /**
         * mealId : 402881198d395678018d395e7a3e001e
         * amount : 2.8
         */

        private String mealId;
        private double amount;

        public String getMealId() {
            return mealId;
        }

        public void setMealId(String mealId) {
            this.mealId = mealId;
        }

        public double getAmount() {
            return amount;
        }

        public void setAmount(double amount) {
            this.amount = amount;
        }
    }

    public static class UsablePayPlatformsBean {
        /**
         * type : 3
         * id : 3
         * name : 微信
         */

        private int type;
        private String id;
        private String name;

        public int getType() {
            return type;
        }

        public void setType(int type) {
            this.type = type;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
