-- Upgrade the built-in simulation follow-up prompt without overwriting a
-- version that an administrator has already customized. On a fresh database,
-- this becomes the initial active version before the default initializer runs.
SET @has_simulation_follow_up := (
  SELECT COUNT(*)
  FROM `ai_prompt_version`
  WHERE `prompt_code` = 'simulation.follow_up'
);

SET @previous_builtin_version := (
  SELECT `version_no`
  FROM `ai_prompt_version`
  WHERE `prompt_code` = 'simulation.follow_up'
    AND `is_active` = 1
    AND `change_note` = '系统内置初始版本'
  ORDER BY `version_no` DESC
  LIMIT 1
);

SET @next_simulation_follow_up_version := (
  SELECT COALESCE(MAX(`version_no`), 0) + 1
  FROM `ai_prompt_version`
  WHERE `prompt_code` = 'simulation.follow_up'
);

SET @apply_simulation_follow_up_upgrade :=
  IF(@has_simulation_follow_up = 0 OR @previous_builtin_version IS NOT NULL, 1, 0);

UPDATE `ai_prompt_version`
SET `is_active` = 0
WHERE `prompt_code` = 'simulation.follow_up'
  AND `version_no` = @previous_builtin_version
  AND @apply_simulation_follow_up_upgrade = 1;

INSERT INTO `ai_prompt_version` (
  `prompt_code`, `prompt_name`, `category`, `version_no`, `system_template`, `user_template`,
  `is_active`, `change_note`, `created_by`, `created_at`, `activated_at`
)
SELECT
  'simulation.follow_up',
  '模拟面试追问',
  'SIMULATION_INTERVIEW',
  @next_simulation_follow_up_version,
  '你是专业的中文技术面试官，正在进行正式模拟面试。表达要像真实面试中的即时交流：专业、克制、具体，不暴露自己是 AI，也不使用聊天助手口吻。',
  '当前面试官风格：$${interviewerStyle}\n当前主问题：$${originalQuestion}\n候选人刚刚的回答：$${answer}\n\n请只输出面试官接下来要说的话，使用中文，控制在 40 到 90 个汉字左右。\n输出规则：\n1. 从回答中的一个具体点切入，用半句到一句作自然回应；不必每次都先表扬，也不要套用“回答很好/回答不错/回答非常扎实/那么请问”等固定开场。\n2. 只提出一个追问，优先追问候选人尚未说清的一个原理、边界、异常、取舍、线程安全或实践细节；等候选人回答后再继续。\n3. 追问只能基于当前主问题和本次回答，不能跳到简历、项目泛聊、其他题目或下一道题。\n4. 回答完整时，选一个更深的边界或取舍继续探查；回答较少或有误时，只给一个简短方向，再问一个更基础、可回答的问题。不要完整讲解标准答案。\n5. 不要重复主问题，不要一次提出多个问题，不要评分、寒暄、标题、编号或“过渡到下一题”。',
  1,
  '系统优化：追问自然度与题目边界',
  NULL,
  NOW(),
  NOW()
WHERE @apply_simulation_follow_up_upgrade = 1;

INSERT INTO `ai_prompt_activation_log` (
  `prompt_code`, `from_version_no`, `to_version_no`, `action`, `note`, `operator_id`, `created_at`
)
SELECT
  'simulation.follow_up',
  @previous_builtin_version,
  @next_simulation_follow_up_version,
  IF(@has_simulation_follow_up = 0, 'INITIAL', 'ACTIVATE'),
  '系统优化：追问自然度与题目边界',
  NULL,
  NOW()
WHERE @apply_simulation_follow_up_upgrade = 1;
