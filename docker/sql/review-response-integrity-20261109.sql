-- HOTEL database. Additive; never remove or reassign historical rows.
-- Duplicate replies require an explicit retention decision before deployment.
BEGIN;
SELECT pg_advisory_xact_lock(hashtextextended('checkinn-review-response-integrity-20261109',0));
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM review_responses GROUP BY review_id HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Duplicate review replies require manual review';
    END IF;
END $$;
CREATE UNIQUE INDEX IF NOT EXISTS uq_review_response_review ON review_responses(review_id);
DO $$ BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='review_responses'::regclass AND conname='fk_review_response_review') THEN
        ALTER TABLE review_responses ADD CONSTRAINT fk_review_response_review
            FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE NOT VALID;
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_constraint WHERE conrelid='review_feedbacks'::regclass AND conname='fk_review_feedback_review') THEN
        ALTER TABLE review_feedbacks ADD CONSTRAINT fk_review_feedback_review
            FOREIGN KEY (review_id) REFERENCES hotel_reviews(id) ON DELETE CASCADE NOT VALID;
    END IF;
END $$;
COMMIT;
-- NOT VALID preserves existing orphans while protecting all new writes.
-- Do not VALIDATE until historical orphans have been resolved with owner approval.
