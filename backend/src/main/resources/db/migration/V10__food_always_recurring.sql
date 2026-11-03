-- Food and groceries recur every month and can't be one-off: make any saved as one-off recurring.
UPDATE entries
SET end_month = NULL
WHERE type = 'EXPENSE'
  AND end_month = start_month
  AND (LOWER(category) LIKE '%food%' OR LOWER(category) LIKE '%grocer%');
