package alg;

public class zj01 {

    public int numSplits(String s, int k) {
        int n = s.length();
        int[] dp = new int[n + 1];
        dp[0] = 1; // 空字符串有一种划分方式

        // 计算k的最大长度
        int maxLen = String.valueOf(k).length();

        for (int i = 1; i <= n; i++) {
            long num = 0; // 用于计算子串的数值
            for (int j = i - 1; j >= Math.max(0, i - maxLen); j--) {
                char ch = s.charAt(j);
                // 计算子串的数值
                num = num * 10 + (ch - '0');
                // 检查子串是否有前导零或数值大于等于k
                if ((i - j > 1 && s.charAt(j) == '0') || num >= k) {
                    break; // 提前终止内层循环
                }
                // 如果符合条件，累加dp[j]到dp[i]
                dp[i] += dp[j];
            }
        }

        return dp[n];
    }

    public static void main(String[] args) {
        zj01 alg = new zj01();

        // 测试用例 1
        String s1 = "123045";
        int k1 = 13;
        System.out.println(alg.numSplits(s1, k1)); // 期望输出: 3

        // 测试用例 2
        String s2 = "10203";
        int k2 = 20;
        System.out.println(alg.numSplits(s2, k2)); // 期望输出: 2
    }
}
