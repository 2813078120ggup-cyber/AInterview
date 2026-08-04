-- 算法练习中心模块（V1）：题目、标签、测试用例、提交、用例结果、进度、收藏、笔记

CREATE TABLE algorithm_tag (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(50) NOT NULL,
    code VARCHAR(50) NOT NULL,
    sort_no INT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_problem (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    title VARCHAR(200) NOT NULL,
    slug VARCHAR(100),
    difficulty VARCHAR(20) NOT NULL,
    description_md MEDIUMTEXT NOT NULL,
    input_description TEXT,
    output_description TEXT,
    constraints_description TEXT,
    hint_content TEXT,
    time_limit_ms INT NOT NULL DEFAULT 3000,
    memory_limit_mb INT NOT NULL DEFAULT 256,
    default_language VARCHAR(30) DEFAULT 'JAVA17',
    starter_code MEDIUMTEXT,
    status TINYINT NOT NULL DEFAULT 1,
    sort_no INT NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_slug (slug),
    INDEX idx_difficulty (difficulty),
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_problem_tag (
    problem_id BIGINT NOT NULL,
    tag_id BIGINT NOT NULL,
    PRIMARY KEY (problem_id, tag_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_test_case (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    problem_id BIGINT NOT NULL,
    input_data MEDIUMTEXT,
    expected_output MEDIUMTEXT NOT NULL,
    case_type VARCHAR(20) NOT NULL,
    score INT NOT NULL DEFAULT 10,
    sort_no INT NOT NULL DEFAULT 0,
    enabled TINYINT NOT NULL DEFAULT 1,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_problem_id (problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_submission (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    language VARCHAR(30) NOT NULL,
    source_code MEDIUMTEXT NOT NULL,
    submit_type VARCHAR(20) NOT NULL,
    status VARCHAR(30) NOT NULL,
    score INT NOT NULL DEFAULT 0,
    passed_count INT NOT NULL DEFAULT 0,
    total_count INT NOT NULL DEFAULT 0,
    execution_time_ms BIGINT,
    memory_usage_kb BIGINT,
    compile_message MEDIUMTEXT,
    runtime_message MEDIUMTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at DATETIME,
    finished_at DATETIME,
    INDEX idx_user_id (user_id),
    INDEX idx_problem_id (problem_id),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_case_result (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    test_case_id BIGINT,
    status VARCHAR(30) NOT NULL,
    actual_output MEDIUMTEXT,
    execution_time_ms BIGINT,
    memory_usage_kb BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_submission_id (submission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_user_progress (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    progress_status VARCHAR(20) NOT NULL,
    submit_count INT NOT NULL DEFAULT 0,
    first_accepted_at DATETIME,
    last_submitted_at DATETIME,
    best_execution_time_ms BIGINT,
    best_memory_usage_kb BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_problem (user_id, problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_favorite (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_problem (user_id, problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE algorithm_note (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    content_md MEDIUMTEXT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_user_problem (user_id, problem_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- 初始标签
INSERT INTO algorithm_tag (id, name, code, sort_no) VALUES
  (1, '数组', 'ARRAY', 1),
  (2, '字符串', 'STRING', 2),
  (3, '链表', 'LINKED_LIST', 3),
  (4, '栈', 'STACK', 4),
  (5, '队列', 'QUEUE', 5),
  (6, '哈希表', 'HASH_TABLE', 6),
  (7, '二叉树', 'BINARY_TREE', 7),
  (8, '图', 'GRAPH', 8),
  (9, '递归', 'RECURSION', 9),
  (10, '回溯', 'BACKTRACKING', 10),
  (11, '动态规划', 'DYNAMIC_PROGRAMMING', 11),
  (12, '贪心', 'GREEDY', 12),
  (13, '二分查找', 'BINARY_SEARCH', 13),
  (14, '滑动窗口', 'SLIDING_WINDOW', 14),
  (15, '双指针', 'TWO_POINTERS', 15),
  (16, '排序', 'SORTING', 16);

-- 种子题目 1：A+B 问题
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, status, sort_no)
VALUES
  (1, 'A+B 问题', 'a-plus-b', 'EASY',
   '## 题目描述\n\n给定两个整数 `a` 和 `b`，输出它们的和。\n\n这是算法练习中心的第一道题，用于熟悉在线编程环境。\n',
   '一行两个整数 a 和 b，用空格分隔。',
   '输出一个整数，表示 a + b 的和。',
   '-10^9 ≤ a, b ≤ 10^9',
   '注意结果可能超出 int 范围，建议使用 long。',
   3000, 256, 'JAVA17',
   'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        long a = sc.nextLong();\n        long b = sc.nextLong();\n        System.out.println(a + b);\n    }\n}\n',
   1, 1);

INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (1, 1, '3 5', '8', 'SAMPLE', 10, 1, 1),
  (2, 1, '0 0', '0', 'HIDDEN', 10, 2, 1),
  (3, 1, '-7 12', '5', 'HIDDEN', 10, 3, 1),
  (4, 1, '1000000000 1000000000', '2000000000', 'HIDDEN', 10, 4, 1),
  (5, 1, '123456789 987654321', '1111111110', 'HIDDEN', 10, 5, 1),
  (6, 1, '-1000000000 -1000000000', '-2000000000', 'HIDDEN', 10, 6, 1);

-- 种子题目 2：两数之和
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, status, sort_no)
VALUES
  (2, '两数之和', 'two-sum', 'EASY',
   '## 题目描述\n\n给定一个整数数组 `nums` 和一个整数目标值 `target`，请你在该数组中找出**和为目标值**的那两个整数，并返回它们的数组下标。\n\n你可以假设每种输入只会对应一个答案，且同一个元素不能使用两次。\n\n## 示例\n\n输入：`nums = [2,7,11,15], target = 9`\n\n输出：`[0,1]`\n\n解释：因为 `nums[0] + nums[1] == 9`，返回 `[0, 1]`。\n',
   '第一行一个整数 n；第二行 n 个整数（数组元素）；第三行一个整数 target。',
   '输出两个整数（0-based 下标），用空格分隔。',
   '2 ≤ n ≤ 10^4，-10^9 ≤ nums[i], target ≤ 10^9',
   '可以使用哈希表将时间复杂度降到 O(n)。',
   3000, 256, 'JAVA17',
   'import java.util.*;\n\npublic class Main {\n    // 请实现：返回两个下标（0-based）\n    public static int[] twoSum(int[] nums, int target) {\n        // TODO: 在这里编写你的代码\n        return new int[]{0, 1};\n    }\n\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        int n = sc.nextInt();\n        int[] nums = new int[n];\n        for (int i = 0; i < n; i++) {\n            nums[i] = sc.nextInt();\n        }\n        int target = sc.nextInt();\n        int[] ans = twoSum(nums, target);\n        System.out.println(ans[0] + " " + ans[1]);\n    }\n}\n',
   1, 2);

INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (7, 2, '4\n2 7 11 15\n9', '0 1', 'SAMPLE', 10, 1, 1),
  (8, 2, '2\n3 3\n6', '0 1', 'HIDDEN', 10, 2, 1),
  (9, 2, '3\n1 2 3\n5', '1 2', 'HIDDEN', 10, 3, 1),
  (10, 2, '5\n-3 4 3 90 0\n0', '0 4', 'HIDDEN', 10, 4, 1),
  (11, 2, '4\n-1 -2 -3 -4\n-8', '2 3', 'HIDDEN', 10, 5, 1),
  (12, 2, '2\n1000000000 -1000000000\n0', '0 1', 'HIDDEN', 10, 6, 1);

-- 种子题目 3：反转字符串
INSERT INTO algorithm_problem
  (id, title, slug, difficulty, description_md, input_description, output_description,
   constraints_description, hint_content, time_limit_ms, memory_limit_mb, default_language,
   starter_code, status, sort_no)
VALUES
  (3, '反转字符串', 'reverse-string', 'EASY',
   '## 题目描述\n\n给定一个字符串 `s`，将其反转后输出。\n\n## 示例\n\n输入：`hello`\n\n输出：`olleh`\n',
   '一行字符串（不含空格，长度 1-1000）。',
   '输出反转后的字符串。',
   '1 ≤ |s| ≤ 1000，字符为可打印 ASCII。',
   '双指针从两端交换字符，或者直接使用 StringBuilder.reverse()。',
   3000, 256, 'JAVA17',
   'import java.util.*;\n\npublic class Main {\n    // 请实现：返回反转后的字符串\n    public static String reverseString(String s) {\n        // TODO: 在这里编写你的代码\n        return new StringBuilder(s).reverse().toString();\n    }\n\n    public static void main(String[] args) {\n        Scanner sc = new Scanner(System.in);\n        String s = sc.nextLine();\n        System.out.println(reverseString(s));\n    }\n}\n',
   1, 3);

INSERT INTO algorithm_test_case (id, problem_id, input_data, expected_output, case_type, score, sort_no, enabled) VALUES
  (13, 3, 'hello', 'olleh', 'SAMPLE', 10, 1, 1),
  (14, 3, 'abc', 'cba', 'HIDDEN', 10, 2, 1),
  (15, 3, 'racecar', 'racecar', 'HIDDEN', 10, 3, 1),
  (16, 3, 'A1b2C3', '3C2b1A', 'HIDDEN', 10, 4, 1),
  (17, 3, 'z', 'z', 'HIDDEN', 10, 5, 1),
  (18, 3, 'OpenAI-2026', '6202-IAnepO', 'HIDDEN', 10, 6, 1);

-- 题目标签关联
INSERT INTO algorithm_problem_tag (problem_id, tag_id) VALUES
  (2, 1), (2, 6),
  (3, 2), (3, 15);
