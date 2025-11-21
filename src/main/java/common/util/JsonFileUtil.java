package common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import common.pojo.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsonFileUtil {
    private static final Logger logger = LoggerFactory.getLogger(JsonFileUtil.class);
    private static final String DATA_DIR = "data";
    private static final String USER_DATA_FILE = DATA_DIR + "/users.json";

    static {
        // 确保数据目录存在
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }
    }

    /**
     * 从文件读取所有用户数据
     */
    public static List<User> readAllUsers() {
        File file = new File(USER_DATA_FILE);
        if (!file.exists()) {
            return new ArrayList<>();
        }

        try (FileInputStream fis = new FileInputStream(file);
                InputStreamReader isr = new InputStreamReader(fis, StandardCharsets.UTF_8);
                BufferedReader br = new BufferedReader(isr)) {

            StringBuilder content = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                content.append(line);
            }

            if (content.length() == 0) {
                return new ArrayList<>();
            }

            JSONArray jsonArray = JSON.parseArray(content.toString());
            return jsonArray.toJavaList(User.class);
        } catch (IOException e) {
            logger.error("读取用户数据文件失败: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * 保存所有用户数据到文件
     */
    public static void saveAllUsers(List<User> users) {
        try (FileOutputStream fos = new FileOutputStream(USER_DATA_FILE);
                OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8);
                BufferedWriter bw = new BufferedWriter(osw)) {

            String jsonString = JSON.toJSONString(users);
            bw.write(jsonString);
            bw.flush();
        } catch (IOException e) {
            logger.error("保存用户数据文件失败: {}", e.getMessage(), e);
        }
    }

    public static <T> T readFromFile(String filePath, Class<T> clazz) {
        try (FileReader reader = new FileReader(filePath)) {
            StringBuilder content = new StringBuilder();
            int ch;
            while ((ch = reader.read()) != -1) {
                content.append((char) ch);
            }
            return JSON.parseObject(content.toString(), clazz);
        } catch (IOException e) {
            logger.error("读取用户数据文件失败: {}", e.getMessage(), e);
            return null;
        }
    }

    public static void writeToFile(String filePath, Object data) {
        try (FileWriter writer = new FileWriter(filePath)) {
            String jsonString = JSON.toJSONString(data);
            writer.write(jsonString);
        } catch (IOException e) {
            logger.error("保存用户数据文件失败: {}", e.getMessage(), e);
        }
    }
}