-- The verified flag was never part of a review workflow (approval itself is the review), so it is removed.
ALTER TABLE quotes DROP COLUMN verified;
