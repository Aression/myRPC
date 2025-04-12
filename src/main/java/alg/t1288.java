package alg;

import java.util.Arrays;

public class t1288 {
    public static void main(String[] args) {
        // 更复杂的测试用例
        int[][] intervals = {{1, 4}, {3, 6}, {2, 8}, {10, 15}, {5, 7}, {12, 20}, {9, 11}, {16, 18}};
        System.out.println("原始区间数组：");
        for (int[] interval : intervals) {
            System.out.println(Arrays.toString(interval));
        }

        // 对区间进行排序
        Arrays.sort(intervals, (a, b) -> {
            if (a[0] != b[0]) {
                return a[0] - b[0];
            } else {
                return a[1] - b[1];
            }
        });

        System.out.println("\n排序后的区间数组：");
        for (int[] interval : intervals) {
            System.out.println(Arrays.toString(interval));
        }

        int len = intervals.length;
        int count = 1;
        int end = intervals[0][1];
        System.out.println("\n初始结束位置：" + end);

        for (int i = 1; i < len; i++) {
            System.out.println("检查区间：" + Arrays.toString(intervals[i]));
            if (intervals[i][1] > end) {
                count++;
                end = Math.max(end, intervals[i][1]);
                System.out.println("更新结束位置：" + end);
            } else {
                System.out.println("区间被覆盖：" + Arrays.toString(intervals[i]));
            }
        }

        System.out.println("\n不被覆盖的区间数量：" + count);
    }
}