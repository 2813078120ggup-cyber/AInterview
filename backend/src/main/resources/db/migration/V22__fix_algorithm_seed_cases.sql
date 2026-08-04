-- 修正算法模块种子用例的期望输出（V21 中两数之和两道隐藏用例有误）：
-- 1) nums=[-3,4,3,90,0], target=0 时正确答案为下标 0 和 2（-3+3=0），不是 0 和 4；
-- 2) [-1,-2,-3,-4] 不存在和为 -8 的一对，改为和为 -7（-3+-4），期望下标 2 3 不变。

UPDATE algorithm_test_case
SET expected_output = '0 2'
WHERE id = 10;

UPDATE algorithm_test_case
SET input_data = '4
-1 -2 -3 -4
-7'
WHERE id = 11;
