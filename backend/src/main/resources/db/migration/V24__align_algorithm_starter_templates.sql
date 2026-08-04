-- 统一算法题目用户答题模板：与题目文件中的“用户答题模板”完全一致，
-- 移除模板中直接给出的答案逻辑（A+B、反转字符串、两数之和等）。

-- 1. A+B 问题
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int a = scanner.nextInt();
        int b = scanner.nextInt();

        // 请输出 a 和 b 的和

        scanner.close();
    }
}'
WHERE id = 1;

-- 2. 两数之和
UPDATE algorithm_problem SET starter_code =
'import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();
        int[] nums = new int[n];

        for (int i = 0; i < n; i++) {
            nums[i] = scanner.nextInt();
        }

        int target = scanner.nextInt();

        // 请找出两个元素的下标

        scanner.close();
    }
}'
WHERE id = 2;

-- 3. 反转字符串
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();

        // 请输出反转后的字符串

        scanner.close();
    }
}'
WHERE id = 3;

-- 4. 数组中的最大值
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();
        int[] nums = new int[n];

        for (int i = 0; i < n; i++) {
            nums[i] = scanner.nextInt();
        }

        // 请找出并输出最大值

        scanner.close();
    }
}'
WHERE id = 4;

-- 5. 判断回文字符串
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();

        // 请判断 text 是否为回文字符串

        scanner.close();
    }
}'
WHERE id = 5;

-- 6. 二分查找
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();
        int[] nums = new int[n];

        for (int i = 0; i < n; i++) {
            nums[i] = scanner.nextInt();
        }

        int target = scanner.nextInt();

        // 请使用二分查找并输出结果

        scanner.close();
    }
}'
WHERE id = 6;

-- 7. 有效括号
UPDATE algorithm_problem SET starter_code =
'import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();

        // 请判断括号是否有效

        scanner.close();
    }
}'
WHERE id = 7;

-- 8. 最大子数组和
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();
        int[] nums = new int[n];

        for (int i = 0; i < n; i++) {
            nums[i] = scanner.nextInt();
        }

        // 请计算最大连续子数组和

        scanner.close();
    }
}'
WHERE id = 8;

-- 9. 爬楼梯
UPDATE algorithm_problem SET starter_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();

        // 请计算爬到第 n 级台阶的方法数量

        scanner.close();
    }
}'
WHERE id = 9;

-- 10. 统计单词出现次数
UPDATE algorithm_problem SET starter_code =
'import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();

        // 请统计单词出现次数，并按照首次出现顺序输出

        scanner.close();
    }
}'
WHERE id = 10;
