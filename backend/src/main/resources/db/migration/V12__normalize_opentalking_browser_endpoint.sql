-- Browsers must access OpenTalking through the same-origin Nginx/Vite proxy.
-- Only replace known loopback defaults; preserve custom public or domain URLs.
UPDATE ai_provider_config
SET base_url = '/opentalking',
    updated_at = CURRENT_TIMESTAMP
WHERE code = 'open-talking-virtual-human'
  AND base_url IN (
    'http://127.0.0.1:8000',
    'http://localhost:8000',
    'http://127.0.0.1:8210',
    'http://localhost:8210'
  );
