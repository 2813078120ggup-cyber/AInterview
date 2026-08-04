-- 算法练习中心批量题目录入 + 标准答案字段

ALTER TABLE algorithm_problem
    ADD COLUMN solution_code MEDIUMTEXT COMMENT '管理员标准答案（仅管理端可见）' AFTER starter_code;

-- 补充标签：基础 / 数学 / 遍历
INSERT INTO algorithm_tag (id, name, code, sort_no) VALUES
  (17, '基础', 'BASIC', 17),
  (18, '数学', 'MATH', 18),
  (19, '遍历', 'TRAVERSAL', 19);

-- ============ 已有题目补充标准答案与标签 ============

-- 1. A+B 问题（基础 / 数学）
UPDATE algorithm_problem SET solution_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int a = scanner.nextInt();
        int b = scanner.nextInt();

        System.out.println(a + b);

        scanner.close();
    }
}'
WHERE id = 1;
INSERT INTO algorithm_problem_tag (problem_id, tag_id) VALUES (1, 17), (1, 18);

-- 2. 两数之和（数组 / 哈希表）
UPDATE algorithm_problem SET solution_code =
'import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();
        Map<Integer, Integer> indexMap = new HashMap<>();

        int[] nums = new int[n];

        for (int i = 0; i < n; i++) {
            nums[i] = scanner.nextInt();
        }

        int target = scanner.nextInt();

        for (int i = 0; i < n; i++) {
            int required = target - nums[i];

            if (indexMap.containsKey(required)) {
                System.out.println(
                        indexMap.get(required) + " " + i
                );
                scanner.close();
                return;
            }

            indexMap.put(nums[i], i);
        }

        scanner.close();
    }
}'
WHERE id = 2;

-- 3. 反转字符串（字符串 / 双指针）
UPDATE algorithm_problem SET solution_code =
'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();

        String result = new StringBuilder(text)
                .reverse()
                .toString();

        System.out.println(result);

        scanner.close();
    }
}'
WHERE id = 3;

-- ============ 新增题目 ============

-- 4. 数组中的最大值（数组 / 遍历）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (4, '数组中的最大值', 'array-max', 'EASY',
   '## 题目描述\n\n给定一个包含 `n` 个整数的数组，请找出数组中的最大值。\n',
   '第一行输入整数 `n`，表示数组长度。\n\n第二行输入 `n` 个整数，使用空格分隔。',
   '输出数组中的最大值。',
   '1 ≤ n ≤ 100000\n-10^9 ≤ nums[i] ≤ 10^9',
   NULL,
   1000, 128, 'JAVA17',
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
}',
   'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();
        int maxValue = scanner.nextInt();

        for (int i = 1; i < n; i++) {
            int value = scanner.nextInt();
            maxValue = Math.max(maxValue, value);
        }

        System.out.println(maxValue);

        scanner.close();
    }
}',
   1, 4);

-- 5. 判断回文字符串（字符串 / 双指针）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (5, '判断回文字符串', 'palindrome-check', 'EASY',
   '## 题目描述\n\n如果一个字符串从左向右读取和从右向左读取完全相同，则称它为回文字符串。\n\n给定一个只包含英文字母和数字的字符串，请判断它是否为回文字符串。\n',
   '输入一行字符串 `s`。',
   '如果是回文字符串输出 `YES`，否则输出 `NO`。',
   '1 ≤ |s| ≤ 100000，字符为英文字母或数字。',
   '双指针从两端向中间比较。',
   1000, 128, 'JAVA17',
   'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();

        // 请判断 text 是否为回文字符串

        scanner.close();
    }
}',
   'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();

        int left = 0;
        int right = text.length() - 1;

        boolean palindrome = true;

        while (left < right) {
            if (text.charAt(left) != text.charAt(right)) {
                palindrome = false;
                break;
            }

            left++;
            right--;
        }

        System.out.println(palindrome ? "YES" : "NO");

        scanner.close();
    }
}',
   1, 5);

-- 6. 二分查找（数组 / 二分查找）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (6, '二分查找', 'binary-search', 'EASY',
   '## 题目描述\n\n给定一个升序排列的整数数组和一个目标值 `target`，请使用二分查找找到目标值的位置。\n\n数组下标从 `0` 开始。如果目标值不存在，输出 `-1`。\n',
   '第一行输入整数 `n`。\n\n第二行输入 `n` 个升序排列的整数。\n\n第三行输入目标值 `target`。',
   '输出目标值第一次出现的下标；如果不存在，输出 `-1`。',
   '1 ≤ n ≤ 100000\n-10^9 ≤ nums[i], target ≤ 10^9',
   '注意返回的是第一次出现的位置。',
   1000, 128, 'JAVA17',
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
}',
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

        int left = 0;
        int right = n - 1;
        int answer = -1;

        while (left <= right) {
            int middle = left + (right - left) / 2;

            if (nums[middle] >= target) {
                if (nums[middle] == target) {
                    answer = middle;
                }

                right = middle - 1;
            } else {
                left = middle + 1;
            }
        }

        System.out.println(answer);

        scanner.close();
    }
}',
   1, 6);

-- 7. 有效括号（字符串 / 栈）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (7, '有效括号', 'valid-parentheses', 'MEDIUM',
   '## 题目描述\n\n给定一个只包含 `( ) [ ] { }` 的字符串，判断括号是否有效。\n\n有效括号需要满足：\n\n1. 左括号必须使用相同类型的右括号闭合。\n2. 左括号必须按照正确的顺序闭合。\n',
   '输入一行括号字符串。',
   '有效输出 `YES`，无效输出 `NO`。',
   '1 ≤ |s| ≤ 100000，字符仅包含 `( ) [ ] { }`。',
   '使用栈匹配括号。',
   1000, 128, 'JAVA17',
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
}',
   'import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        String text = scanner.next();
        Deque<Character> stack = new ArrayDeque<>();

        boolean valid = true;

        for (char current : text.toCharArray()) {
            if (current == ''(''
                    || current == ''[''
                    || current == ''{'') {

                stack.push(current);
                continue;
            }

            if (stack.isEmpty()) {
                valid = false;
                break;
            }

            char left = stack.pop();

            if ((current == '')'' && left != ''('')
                    || (current == '']'' && left != ''['')
                    || (current == ''}'' && left != ''{'')) {

                valid = false;
                break;
            }
        }

        if (!stack.isEmpty()) {
            valid = false;
        }

        System.out.println(valid ? "YES" : "NO");

        scanner.close();
    }
}',
   1, 7);

-- 8. 最大子数组和（数组 / 动态规划）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (8, '最大子数组和', 'max-subarray-sum', 'MEDIUM',
   '## 题目描述\n\n给定一个整数数组，请找出一个连续子数组，使该子数组的元素和最大，并输出这个最大值。\n\n连续子数组至少包含一个元素。\n',
   '第一行输入整数 `n`。\n\n第二行输入 `n` 个整数。',
   '输出最大连续子数组和。',
   '1 ≤ n ≤ 100000\n-10000 ≤ nums[i] ≤ 10000',
   'Kadane 算法：维护以当前位置结尾的最大和。',
   2000, 256, 'JAVA17',
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
}',
   'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();

        long currentMax = scanner.nextLong();
        long globalMax = currentMax;

        for (int i = 1; i < n; i++) {
            long value = scanner.nextLong();

            currentMax = Math.max(
                    value,
                    currentMax + value
            );

            globalMax = Math.max(
                    globalMax,
                    currentMax
            );
        }

        System.out.println(globalMax);

        scanner.close();
    }
}',
   1, 8);

-- 9. 爬楼梯（动态规划）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (9, '爬楼梯', 'climbing-stairs', 'EASY',
   '## 题目描述\n\n一个楼梯共有 `n` 级台阶。\n\n每次可以向上走一级或者两级，请计算走到第 `n` 级台阶共有多少种不同方法。\n',
   '输入整数 `n`。',
   '输出不同的方法数量。',
   '1 ≤ n ≤ 45',
   '斐波那契数列：f(n) = f(n-1) + f(n-2)。',
   1000, 128, 'JAVA17',
   'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();

        // 请计算爬到第 n 级台阶的方法数量

        scanner.close();
    }
}',
   'import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();

        if (n <= 2) {
            System.out.println(n);
            scanner.close();
            return;
        }

        int previousTwo = 1;
        int previousOne = 2;

        for (int current = 3; current <= n; current++) {
            int ways = previousOne + previousTwo;
            previousTwo = previousOne;
            previousOne = ways;
        }

        System.out.println(previousOne);

        scanner.close();
    }
}',
   1, 9);

-- 10. 统计单词出现次数（字符串 / 哈希表）
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, solution_code, status, sort_no)
VALUES
  (10, '统计单词出现次数', 'word-count', 'EASY',
   '## 题目描述\n\n输入 `n` 个单词，统计每个单词出现的次数。\n\n按照单词第一次出现的顺序输出统计结果。\n',
   '第一行输入整数 `n`。\n\n之后输入 `n` 个不包含空格的单词。',
   '每行输出一个单词及其出现次数，中间使用一个空格分隔。',
   '1 ≤ n ≤ 100000，单词长度 1-100，由英文字母或数字组成。',
   '使用 LinkedHashMap 保持首次出现顺序。',
   1000, 128, 'JAVA17',
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
}',
   'import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Scanner;

public class Main {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        int n = scanner.nextInt();

        Map<String, Integer> countMap =
                new LinkedHashMap<>();

        for (int i = 0; i < n; i++) {
            String word = scanner.next();

            countMap.merge(
                    word,
                    1,
                    Integer::sum
            );
        }

        for (Map.Entry<String, Integer> entry
                : countMap.entrySet()) {

            System.out.println(
                    entry.getKey()
                            + " "
                            + entry.getValue()
            );
        }

        scanner.close();
    }
}',
   1, 10);

-- ============ 新增测试用例 ============

-- 4. 数组中的最大值
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (19, 4, '5
3 8 2 10 6', '10', 'SAMPLE', 10, 1, 1),
  (20, 4, '1
5', '5', 'HIDDEN', 10, 2, 1),
  (21, 4, '4
-8 -2 -10 -5', '-2', 'HIDDEN', 10, 3, 1),
  (22, 4, '5
9 9 9 9 9', '9', 'HIDDEN', 10, 4, 1);

-- 5. 判断回文字符串
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (23, 5, 'level', 'YES', 'SAMPLE', 10, 1, 1),
  (24, 5, 'a', 'YES', 'HIDDEN', 10, 2, 1),
  (25, 5, 'abba', 'YES', 'HIDDEN', 10, 3, 1),
  (26, 5, 'abcba', 'YES', 'HIDDEN', 10, 4, 1),
  (27, 5, 'hello', 'NO', 'HIDDEN', 10, 5, 1),
  (28, 5, '12321', 'YES', 'HIDDEN', 10, 6, 1);

-- 6. 二分查找
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (29, 6, '6
1 3 5 7 9 11
7', '3', 'SAMPLE', 10, 1, 1),
  (30, 6, '1
5
5', '0', 'HIDDEN', 10, 2, 1),
  (31, 6, '5
1 2 3 4 5
8', '-1', 'HIDDEN', 10, 3, 1),
  (32, 6, '6
1 2 2 2 3 4
2', '1', 'HIDDEN', 10, 4, 1);

-- 7. 有效括号
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (33, 7, '([]{})', 'YES', 'SAMPLE', 10, 1, 1),
  (34, 7, '()', 'YES', 'HIDDEN', 10, 2, 1),
  (35, 7, '([{}])', 'YES', 'HIDDEN', 10, 3, 1),
  (36, 7, '(]', 'NO', 'HIDDEN', 10, 4, 1),
  (37, 7, '([)]', 'NO', 'HIDDEN', 10, 5, 1),
  (38, 7, '(((', 'NO', 'HIDDEN', 10, 6, 1),
  (39, 7, '}{', 'NO', 'HIDDEN', 10, 7, 1);

-- 8. 最大子数组和
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (40, 8, '9
-2 1 -3 4 -1 2 1 -5 4', '6', 'SAMPLE', 10, 1, 1),
  (41, 8, '1
-5', '-5', 'HIDDEN', 10, 2, 1),
  (42, 8, '5
1 2 3 4 5', '15', 'HIDDEN', 10, 3, 1),
  (43, 8, '5
-1 -2 -3 -4 -5', '-1', 'HIDDEN', 10, 4, 1),
  (44, 8, '6
5 -2 3 -10 8 2', '10', 'HIDDEN', 10, 5, 1);

-- 9. 爬楼梯
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (45, 9, '4', '5', 'SAMPLE', 10, 1, 1),
  (46, 9, '1', '1', 'HIDDEN', 10, 2, 1),
  (47, 9, '2', '2', 'HIDDEN', 10, 3, 1),
  (48, 9, '10', '89', 'HIDDEN', 10, 4, 1),
  (49, 9, '45', '1836311903', 'HIDDEN', 10, 5, 1);

-- 10. 统计单词出现次数
INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (50, 10, '6
java
spring
java
mysql
spring
java', 'java 3
spring 2
mysql 1', 'SAMPLE', 10, 1, 1),
  (51, 10, '1
hello', 'hello 1', 'HIDDEN', 10, 2, 1),
  (52, 10, '4
apple
banana
apple
apple', 'apple 3
banana 1', 'HIDDEN', 10, 3, 1),
  (53, 10, '3
x
x
x', 'x 3', 'HIDDEN', 10, 4, 1);

-- ============ 新题目标签 ============
INSERT INTO algorithm_problem_tag (problem_id, tag_id) VALUES
  (4, 1), (4, 19),
  (5, 2), (5, 15),
  (6, 1), (6, 13),
  (7, 2), (7, 4),
  (8, 1), (8, 11),
  (9, 11),
  (10, 2), (10, 6);
