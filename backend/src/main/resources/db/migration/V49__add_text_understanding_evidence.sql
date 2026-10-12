-- 文本理解与评价感知模块：为逐题评测增加可追溯的文本理解证据
-- 文本理解与评价由评分引擎（DeepSeek 语义评分）完成，
-- 证据结构化记录输入摘要、命中分档、四维原始分与评分规则版本，便于报告可追溯。
ALTER TABLE `evaluation`
    ADD COLUMN `text_evidence` TEXT DEFAULT NULL COMMENT 'Text understanding & evaluation traceable evidence (JSON): input digest, band, raw dimensions, scoring rule version';
