-- 语音分析感知模块：为逐题评测增加可追溯的语音维度
-- 语音分析由独立 Python 服务（voice-analysis-service）计算，
-- 结果以旁路增强的方式写入评测，未评估时保持 NULL（对应「未评估」语义）。
ALTER TABLE `evaluation`
    ADD COLUMN `voice_score`       DECIMAL(5,2) DEFAULT NULL COMMENT 'Voice analysis score, 0-100; NULL means not evaluated',
    ADD COLUMN `voice_evidence`    TEXT         DEFAULT NULL COMMENT 'Voice analysis traceable evidence (JSON): dimensions, raw features, rules',
    ADD COLUMN `voice_evaluated_at` DATETIME    DEFAULT NULL COMMENT 'When voice analysis completed; NULL means not evaluated';

ALTER TABLE `evaluation`
    ADD CONSTRAINT `chk_evaluation_voice` CHECK (`voice_score` IS NULL OR `voice_score` BETWEEN 0 AND 100);
