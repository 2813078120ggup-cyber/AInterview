-- Local development scenarios for interview-hall and interview-room testing.
-- Run after the Docker initialization baseline or after Flyway has migrated an empty database.
-- Passwords are not changed here.

START TRANSACTION;

INSERT INTO `interview` (`title`, `candidate_id`, `interviewer_id`, `scheduled_at`, `duration`, `started_at`, `status`, `type`, `meeting_url`, `remark`, `created_by`)
VALUES ('Java 并发能力模拟面试（进行中）', 6, 3, DATE_SUB(NOW(), INTERVAL 10 MINUTE), 60, DATE_SUB(NOW(), INTERVAL 10 MINUTE), 1, 'tech', 'https://meet.local/interview/liu-live', '候选人刘洋进行中测试场次', 2);
SET @liu_live_interview_id = LAST_INSERT_ID();
INSERT INTO `interview_question` (`interview_id`, `question_id`, `sequence_no`, `max_score`, `question_snapshot`, `source`)
VALUES
  (@liu_live_interview_id, 2, 1, 15.00, JSON_OBJECT('content', '请说明 HashMap 在 JDK 8 中的底层结构和扩容机制。', 'questionType', 'short_answer', 'difficulty', 2, 'score', 15), 'bank'),
  (@liu_live_interview_id, 4, 2, 15.00, JSON_OBJECT('content', 'volatile 关键字解决了哪些并发问题？', 'questionType', 'short_answer', 'difficulty', 2, 'score', 15), 'bank');

INSERT INTO `interview` (`title`, `candidate_id`, `interviewer_id`, `scheduled_at`, `duration`, `started_at`, `status`, `type`, `meeting_url`, `remark`, `created_by`)
VALUES ('Vue3 工程化模拟面试（进行中）', 7, 4, DATE_SUB(NOW(), INTERVAL 15 MINUTE), 45, DATE_SUB(NOW(), INTERVAL 15 MINUTE), 1, 'tech', 'https://meet.local/interview/sun-live', '候选人孙悦进行中测试场次', 2);
SET @sun_live_interview_id = LAST_INSERT_ID();
INSERT INTO `interview_question` (`interview_id`, `question_id`, `sequence_no`, `max_score`, `question_snapshot`, `source`)
VALUES
  (@sun_live_interview_id, 2, 1, 15.00, JSON_OBJECT('content', '请说明 HashMap 在 JDK 8 中的底层结构和扩容机制。', 'questionType', 'short_answer', 'difficulty', 2, 'score', 15), 'bank'),
  (@sun_live_interview_id, 6, 2, 20.00, JSON_OBJECT('content', '编写一个线程安全的单例模式实现，并说明为什么安全。', 'questionType', 'coding', 'difficulty', 3, 'score', 20), 'bank');

INSERT INTO `interview` (`title`, `candidate_id`, `interviewer_id`, `scheduled_at`, `duration`, `status`, `type`, `meeting_url`, `remark`, `created_by`)
VALUES
  ('Java 基础能力模拟面试（即将开始）', 6, 3, DATE_ADD(NOW(), INTERVAL 30 MINUTE), 45, 0, 'tech', 'https://meet.local/interview/liu-upcoming', '候选人刘洋待开始测试场次', 2),
  ('前端综合能力模拟面试（即将开始）', 7, 4, DATE_ADD(NOW(), INTERVAL 1 HOUR), 60, 0, 'comprehensive', 'https://meet.local/interview/sun-upcoming', '候选人孙悦待开始测试场次', 2),
  ('数据库专项模拟面试（即将开始）', 8, 3, DATE_ADD(NOW(), INTERVAL 2 HOUR), 45, 0, 'tech', 'https://meet.local/interview/zhou-upcoming', '候选人周航待开始测试场次', 2),
  ('HR 沟通能力模拟面试（即将开始）', 6, 4, DATE_ADD(NOW(), INTERVAL 1 DAY), 30, 0, 'hr', 'https://meet.local/interview/liu-hr', '候选人刘洋次日测试场次', 2);

-- For a multi-row INSERT, MySQL returns the first generated id.
SET @liu_upcoming_interview_id = LAST_INSERT_ID();
SET @sun_upcoming_interview_id = LAST_INSERT_ID() + 1;
SET @zhou_upcoming_interview_id = LAST_INSERT_ID() + 2;
SET @liu_hr_interview_id = LAST_INSERT_ID() + 3;
INSERT INTO `interview_question` (`interview_id`, `question_id`, `sequence_no`, `max_score`, `question_snapshot`, `source`)
VALUES
  (@liu_upcoming_interview_id, 2, 1, 15.00, JSON_OBJECT('content', '请说明 HashMap 在 JDK 8 中的底层结构和扩容机制。', 'questionType', 'short_answer', 'difficulty', 2, 'score', 15), 'bank'),
  (@sun_upcoming_interview_id, 4, 1, 15.00, JSON_OBJECT('content', 'volatile 关键字解决了哪些并发问题？', 'questionType', 'short_answer', 'difficulty', 2, 'score', 15), 'bank'),
  (@zhou_upcoming_interview_id, 6, 1, 20.00, JSON_OBJECT('content', '编写一个线程安全的单例模式实现，并说明为什么安全。', 'questionType', 'coding', 'difficulty', 3, 'score', 20), 'bank'),
  (@liu_hr_interview_id, 2, 1, 15.00, JSON_OBJECT('content', '请说明 HashMap 在 JDK 8 中的底层结构和扩容机制。', 'questionType', 'short_answer', 'difficulty', 2, 'score', 15), 'bank');

COMMIT;
