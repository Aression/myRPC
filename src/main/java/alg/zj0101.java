package alg;

import java.util.ArrayList;
import java.util.List;

public class zj0101 {

    public static List<List<String>> generateAllSplits(String s) {
        List<List<String>> result = new ArrayList<>();
        int n = s.length();
        if (n == 0) return result;
        
        int m = n - 1; // 分割点数量
        
        // 遍历所有可能的二进制位串（0到2^m - 1）
        for (int mask = 0; mask < (1 << m); mask++) {
            List<String> currentSplit = new ArrayList<>();
            int start = 0;
            
            // 处理每个分割点
            for (int i = 0; i < m; i++) {
                // 检查当前位是否为1
                if ((mask & (1 << i)) != 0) {
                    currentSplit.add(s.substring(start, i + 1));
                    start = i + 1;
                }
            }
            
            // 添加最后一个子串
            currentSplit.add(s.substring(start));
            result.add(currentSplit);
        }
        
        return result;
    }

    public static void main(String[] args) {
        String input = "abc";
        List<List<String>> allSplits = generateAllSplits(input);
        
        System.out.println("所有划分结果：");
        for (List<String> split : allSplits) {
            System.out.println(split);
        }
    }
}