package com.gs.payment.plugin.manager;

/**
 * ━━━━━━神兽出没━━━━━━
 * 　　　┏┓　　　┏┓
 * 　　┏┛┻━━━┛┻┓
 * 　　┃　　　　　　　┃
 * 　　┃　　　━　　　┃
 * 　　┃　┳┛　┗┳　┃
 * 　　┃　　　　　　　┃
 * 　　┃　　　┻　　　┃
 * 　　┃　　　　　　　┃
 * 　　┗━┓　　　┏━┛Code is far away from bug with the animal protecting
 * 　　　　┃　　　┃    神兽保佑,代码无bug
 * 　　　　┃　　　┃
 * 　　　　┃　　　┗━━━┓
 * 　　　　┃　　　　　　　┣┓
 * 　　　　┃　　　　　　　┏┛
 * 　　　　┗┓┓┏━┳┓┏┛
 * 　　　　　┃┫┫　┃┫┫
 * 　　　　　┗┻┛　┗┻┛
 * <p>
 * Created by wangzhenxiong on 2019/3/6.
 */
public class ByteUtils {
    /*/
   截取数组
    */
    public static byte[] subBytes(byte[] src, int begin, int count) {
        byte[] bs = new byte[count];
        for (int i = begin; i < begin + count; i++) {
            bs[i - begin] = src[i];
        }
        return bs;
    }

    /*/
    byte数组拼接
     */
    public static byte[] byteMerger(byte[] byte_1, byte[] byte_2) {
        byte[] byte_3 = new byte[byte_1.length + byte_2.length];
        System.arraycopy(byte_1, 0, byte_3, 0, byte_1.length);
        System.arraycopy(byte_2, 0, byte_3, byte_1.length, byte_2.length);
        return byte_3;
    }

    /**
     * int转换成bytes数组
     *
     * @return
     */
    public static byte[] intToBytes2_HL(int value, int i) {
        // value=-2;
        byte[] src = new byte[4];
        src[0] = (byte) ((value >> 24) & 0xFF);
        src[1] = (byte) ((value >> 16) & 0xFF);
        src[2] = (byte) ((value >> 8) & 0xFF);
        src[3] = (byte) (value & 0xFF);
        byte[] bt1 = new byte[2];
        bt1[0] = src[2];
        bt1[1] = src[3];
        if (i == 4) {
            return src;
        } else if (i == 2) {
            return bt1;
        }
        return null;
    }

    /**
     * bytes转换成十六进制字符串
     *
     * @param b byte数组
     * @return String 每个Byte值之间空格分隔
     */
    public static String byte2HexStr2(byte[] b) {
        String stmp = "";
        StringBuilder sb = new StringBuilder("");
        for (int n = 0; n < b.length; n++) {
            stmp = Integer.toHexString(b[n] & 0xFF);
            sb.append((stmp.length() == 1) ? "0" + stmp : stmp);
            sb.append(" ");
        }
        return sb.toString().toUpperCase().trim();
    }

    /**
     * 字节数组转16进制
     *
     * @param bytes 需要转换的byte数组
     * @return 转换后的Hex字符串
     */
    public static String bytesToHex(byte[] bytes) {
        StringBuffer sb = new StringBuffer();
        for (int i = 0; i < bytes.length; i++) {
            String hex = Integer.toHexString(bytes[i] & 0xFF);
            if (hex.length() < 2) {
                sb.append(0);
            }
            sb.append(hex);
        }
        return sb.toString();
    }

    /**
     * 将byte转换为一个长度为8的byte数组，数组每个值代表bit
     */
    public static byte[] getBooleanArray(byte b) {
        byte[] array = new byte[8];
        for (int i = 7; i >= 0; i--) {
            array[i] = (byte) (b & 1);
            b = (byte) (b >> 1);
        }
        byte[] bytes = new byte[array.length];
        for (int i = 0; i < array.length; i++) {
            bytes[7 - i] = array[i];
        }
        return bytes;
    }

    /**
     * Hex转byte[]，两种情况，Hex长度为奇数最后一个字符会被舍去
     */
    public static byte[] hexTobytes(String hex) {
        if (hex.length() < 1) {

            return null;
        } else {
            byte[] result = new byte[hex.length() / 2];
            int j = 0;
            for (int i = 0; i < hex.length(); i += 2) {
                result[j++] = (byte) Integer.parseInt(hex.substring(i, i + 2), 16);
            }
            return result;
        }
    }

    /**
     * byte数组中取int数值，本方法适用于(低位在后，高位在前)的顺序。
     */
    public static int bytesToInt4(byte[] src, int offset) {
        int value;
        value = (int) (((src[offset] & 0xFF) << 8)
                | (src[offset + 1] & 0xFF));
        return value;
    }

    public static boolean checkByte(byte[] bytes) {
        //数据长度至少为5
        if (bytes.length < 5) {
            return false;
        }
        int checksum = 0;
        for (int i = 0; i < bytes.length - 1; i++) {
            checksum += bytes[i];
        }
        int i = checksum & 0xff;
        if (i == (bytes[bytes.length - 1] & 0xff)) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * 从byte数组中提取16位无符号整数
     *
     * @param bytes 包含16位无符号整数的byte数组
     * @return 提取出的16位无符号整数
     */
    public static int extract16BitValue(byte[] bytes) {
        return ((bytes[1] & 0xFF) << 8) | (bytes[0] & 0xFF);
    }

    /**
     * 计算完整的32位前门状态信息
     *
     * @param FMST_L 低16位前门状态信息（byte数组）
     * @param FMST_H 高16位前门状态信息（byte数组）
     * @return 完整的32位前门状态信息
     */
    public static int calculateFMST(byte[] FMST_L, byte[] FMST_H) {
        // 提取低16位和高16位的值
        int lowPart = extract16BitValue(FMST_L);
        int highPart = extract16BitValue(FMST_H);

        // 将高16位左移16位
        int highPartShifted = highPart << 16;

        // 将低16位和高16位合并
        return highPartShifted | lowPart;
    }

    /**
     * 将整数转换为32位的二进制字符串
     *
     * @param value 要转换的整数
     * @return 32位的二进制字符串
     */
    public static String to32BitBinaryString(long value) {
        return String.format("%32s", Long.toBinaryString(value)).replace(' ', '0');
    }

    // 将 byte 数组转为无符号 int，并组合高16位和低16位
    public static long convertsByteToInt(byte[] fmstH, byte[] fmstL) {
        // 将高16位和低16位的 byte 数组转换为无符号 int
        int high = ((fmstH[0] & 0xFF) << 8) | (fmstH[1] & 0xFF);
        int low = ((fmstL[0] & 0xFF) << 8) | (fmstL[1] & 0xFF);

        // 使用位移操作和按位或操作将两部分组合成32位无符号整数
        return ((long) high << 16) | (low & 0xFFFFL);
    }


    /**
     * 反转数组
     * @param array
     */
    public static void reverse(byte[] array) {
        int left = 0;
        int right = array.length - 1;
        while (left < right) {
            // 交换左右元素
            byte temp = array[left];
            array[left] = array[right];
            array[right] = temp;

            left++;
            right--;
        }
    }
}
