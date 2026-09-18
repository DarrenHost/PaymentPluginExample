package com.gs.payment.plugin;

import java.util.Objects;

/**
 * Created by LiBaoZhi
 * on 2026/2/28
 */
public enum OrderFailReasonEnum {

    RECIPE_INFO_ERROR(1, "配方信息异常","Abnormal formula information"),
    TRANSFER_CUP_TO_START_FAILED(2, "传杯到起始位失败","Failed to pass the cup to the starting position"),
    TRANSFER_CUP_TO_STATION_FAILED(3, "传杯到指定工位失败","Failed to pass the cup to the designated workstation"),
    TRANSFER_CUP_TO_END_FAILED(4, "传杯到终点位失败","Failed to pass the cup to the finish line"),
    OPEN_DOOR_FAILED(5, "开门操作失败","Failed to open the door"),
    CLOSE_DOOR_FAILED(6, "关门操作失败","The closing operation failed"),
    AUTO_DROP_CUP_FAILED(7, "自动落杯失败","Automatic cup lowering failed"),
    MANUAL_PLACE_CUP_TIMEOUT(8, "手动放杯超时","Manual cup placing timeout"),
    LOW_WATER_LEVEL_FAILED(9, "低水位导致失败（流量计）","Low water level causes failure (flowmeter)"),
    PARALLEL_GRIND_FAILED(10, "并行现磨操作失败（制作时）","Parallel fresh grinding operation failed (during production)"),
    CURRENT_GRIND_FAILED(11, "本次现磨操作失败（制作时，送杯前）","This fresh grinding operation failed (during production, before delivering the cup)"),
    WAIT_FOR_PICKUP_TIMEOUT(12, "等待取杯超时","Waiting for cup retrieval timed out"),
    GRINDER_OFFLINE_BEFORE_MAKE(13, "制作前，现磨离线或故障","Before production, grind offline or faulty items"),
    MAKE_PROCESS_FORCE_STOP(14, "制作过程被强制结束","The production process was forcibly terminated"),
    ICE_DISPENSE_FAILED(15, "出冰失败","Ice dispensing failure"),
    LID_DROP_FAILED(16, "落盖失败","Failed to close the lid"),
    LID_PRESS_FAILED(17, "压盖失败","Lid press failed"),
    FRONT_DOOR_OFFLINE_BEFORE_MAKE(18, "制作前，前门离线","Before production, the front door is offline"),
    FRONT_DOOR_BUSY_BEFORE_MAKE(19, "制作前，前门长期忙","Before production, the front door has been busy for a long time"),
    WAIT_GRIND_COMPLETE_TIMEOUT(20, "等待【现磨完成】通知超时","Waiting for the notification of 'grinding completed' timeout"),
    CUP_MISSING_DURING_MAKE(21, "制作过程中杯子丢失","Cups are lost during the production process"),
    EXECUTE_CUP_TRANSFER_BEFORE_MAKE(22, "执行送杯 (制作前杯座有杯)","Execute cup delivery (cup holder has cup before making)"),
    GRINDER_PRE_RUN_FAILED(23, "现磨预运行失败 (落杯后)","Pre-operation failure of fresh grinding (after cup placement)"),
    INSTANT_MIX_STEP_FAILED(24, "速溶步骤执行失败(执行参数异常 或 电磁阀故障)","Instant step execution failed (execution parameter exception or solenoid valve failure)"),
    WASTE_TRAY_RETRACT_FAILED(25, "废水盘收缩失败","Wastewater tray contraction failed"),
    WASTE_TRAY_EXTEND_FAILED(26, "废水盘伸展失败","Wastewater tray extension failed");

    private final Integer type;
    private final String name;
    private final String enName;

    OrderFailReasonEnum(Integer type, String name,String enName) {
        this.type = type;
        this.name = name;
        this.enName = enName;
    }

    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public String getEnName() {
        return enName;
    }

    public static String getEnNameByType(Integer type) {
        if (type != null && type == -1) {
            return "Unknown error";
        }
        for (OrderFailReasonEnum orderFailReasonEnum : OrderFailReasonEnum.values()) {
            if (Objects.equals(type, orderFailReasonEnum.getType())) {
                return orderFailReasonEnum.getEnName();
            }
        }
        return "";
    }

}
