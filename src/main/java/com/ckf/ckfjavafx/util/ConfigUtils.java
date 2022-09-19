package com.ckf.ckfjavafx.util;

import com.ckf.ckfjavafx.exceptions.BizException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.ini4j.Config;
import org.ini4j.Ini;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Ini配置读写工具类
 * @author: Chenkf
 * @create: 2022/8/22
 **/
@Slf4j
public class ConfigUtils {
    private static final ConcurrentMap<String, Ini> CONFIG_POOL = new ConcurrentHashMap<>(8);

    private ConfigUtils() {}


    public static boolean exists(String configPath) {
        File configFile = new File(configPath);
        return configFile.isFile() && configFile.exists();
    }

    private static Ini loadConfig(String configPath) {
        try {
            FileUtils.forceMkdirParent(new File(configPath));
            try (InputStreamReader reader = new InputStreamReader(new FileInputStream(configPath), StandardCharsets.UTF_8)) {
                Ini ini = new Ini(reader);
                ini.setConfig(defaultConfig());
                return ini;
            }
        } catch (IOException e) {
            log.error("加载配置文件失败：", e);
            throw new BizException(e);
        }
    }

    private static Ini createConfig(String configPath) {
        try {
            FileUtils.forceMkdirParent(new File(configPath));
            Ini ini = new Ini();
            ini.setConfig(defaultConfig());
            ini.store(new File(configPath));
            return ini;
        } catch (IOException e) {
            log.error("创建配置文件失败：", e);
            throw new BizException(e);
        }
    }

    private static Config defaultConfig() {
        Config config = new Config();
        config.setMultiSection(true);
        config.setFileEncoding(StandardCharsets.UTF_8);
        return config;
    }

    public static String readValue(String configPath, String section, String key) {
        Ini ini = CONFIG_POOL.computeIfAbsent(configPath, ConfigUtils::loadConfig);
        return ini.get(section, key);
    }

    public static Ini readConfig(String configPath) {
        return CONFIG_POOL.computeIfAbsent(configPath, ConfigUtils::loadConfig);
    }

    public static void writeValue(String configPath, String section, String key, Object value) {
        try {
            Ini ini = CONFIG_POOL.computeIfAbsent(configPath, ConfigUtils::createConfig);
            ini.put(section, key, value);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(configPath, false), StandardCharsets.UTF_8)) {
                ini.store(writer);
            }
        } catch (IOException e) {
            log.error("保存配置文件失败：", e);
        }
    }

    public static void removeValue(String configPath, String section) {
        try {
            Ini ini = CONFIG_POOL.computeIfAbsent(configPath, ConfigUtils::createConfig);
            ini.remove(section);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(configPath, false), StandardCharsets.UTF_8)) {
                ini.store(writer);
            }
        } catch (IOException e) {
            log.error("保存配置文件失败：", e);
        }
    }

    public static <T> T read(String configPath, String section, T bean) {
        Ini ini = CONFIG_POOL.computeIfAbsent(configPath, ConfigUtils::loadConfig);
        ini.get(section).to(bean);
        return bean;
    }

    public static <T> void write(String configPath, String section, T bean) {
        try {
            Ini ini = CONFIG_POOL.computeIfAbsent(configPath, ConfigUtils::createConfig);
            Optional.ofNullable(ini.get(section)).orElseGet(() -> ini.add(section)).from(bean);
            try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(configPath, false), StandardCharsets.UTF_8)) {
                ini.store(writer);
            }
        } catch (IOException e) {
            log.error("保存配置文件失败：", e);
        }
    }
}
