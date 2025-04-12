package common.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import common.pojo.User;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class JsonFileUtil {
    private static final String USER_DATA_FILE = "user_data.json";

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
            System.out.println("读取用户数据文件失败: " + e.getMessage());
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
            System.out.println("保存用户数据文件失败: " + e.getMessage());
        }
    }
} 