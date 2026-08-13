-- Candidate resumes reuse private media storage but need their own media category.
ALTER TABLE `media_file`
  DROP CHECK `chk_media_type`,
  ADD CONSTRAINT `chk_media_type`
    CHECK (`media_type` IN ('audio', 'video', 'image', 'pdf', 'resume'));
