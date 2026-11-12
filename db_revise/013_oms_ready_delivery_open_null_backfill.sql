-- Backfill mails that were opened before null-clearing used explicit SQL SET clauses.
-- MyBatis-Plus updateById ignores null entity fields by default, so package_id,
-- bag_no or current_slot may remain populated even after status became READY_FOR_DELIVERY.
UPDATE `mail`
SET
  `package_id` = NULL,
  `bag_no` = NULL,
  `current_slot` = NULL
WHERE `status` = 'READY_FOR_DELIVERY'
  AND (
    `package_id` IS NOT NULL
    OR `bag_no` IS NOT NULL
    OR `current_slot` IS NOT NULL
  );
