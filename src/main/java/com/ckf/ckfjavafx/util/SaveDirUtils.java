package com.ckf.ckfjavafx.util;

import java.io.File;

/**
 * 文件保存目录
 * @author: Chenkf
 * @create: 2022/9/8
 **/
public class SaveDirUtils {

    /**
     * 获取保存根目录，如：windows为 C:\Users\当前用户
     * @return
     */
    public static String getRootSaveDir() {
        return System.getProperty("user.home");
    }

    /**
     * 获取文件具体保存目录
     * @return
     */
    public static String getFileSaveDir(){
        return getRootSaveDir().concat(File.separator).concat("ckf-javafx-demo");
    }

    /**
     * 获取配置文件(.ini格式)具体保存路径
     * @return
     */
    public static String getConfigFileSavePath(){
        return getFileSaveDir().concat(File.separator).concat("demo-config.ini");
    }

}
