package common.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Objects;
import java.util.Properties;

/**
 * 轻量应用配置加载器：
 * - 优先读取系统属性（-Dkey=value）
 * - 其次读取 classpath: application.properties
 */
public final class AppConfig {
    private static final Logger logger = LoggerFactory.getLogger(AppConfig.class);
    private static final Properties PROPS = new Properties();

    static {
        try (InputStream in = AppConfig.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (in != null) {
                PROPS.load(in);
                logger.info("已加载 application.properties，共 {} 项", PROPS.size());
            } else {
                logger.info("未找到 application.properties，使用内置默认与系统属性");
            }
        } catch (IOException e) {
            logger.warn("加载 application.properties 失败: {}", e.getMessage());
        }
    }

    private AppConfig() {}

    public static String getString(String key, String defaultValue) {
        String sysVal = System.getProperty(key);
        if (sysVal != null && !sysVal.isEmpty()) return sysVal;
        String val = PROPS.getProperty(key);
        return val != null ? val : defaultValue;
    }

    public static int getInt(String key, int defaultValue) {
        String str = getString(key, null);
        if (str == null) return defaultValue;
        try {
            return Integer.parseInt(str.trim());
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    public static <E extends Enum<E>> E getEnumIgnoreCase(String key, Class<E> enumType, E defaultValue) {
        String str = getString(key, null);
        if (str == null) return defaultValue;
        try {
            return Enum.valueOf(enumType, str.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return defaultValue;
        }
    }

    public static boolean getBoolean(String key, boolean defaultValue) {
        String str = getString(key, null);
        if (str == null) return defaultValue;
        return Objects.equals("true", str.trim().toLowerCase(Locale.ROOT))
                || Objects.equals("1", str.trim());
    }
}


