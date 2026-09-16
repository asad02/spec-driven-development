-- ff4j's shipped schema sizes EVT_VALUE at VARCHAR(100), which is ample for its
-- own usage counters but too small for an audit detail that has to say what
-- changed and what would have broken. A guardrail refusal message alone runs to
-- ~120 characters, and the insert failed silently against the original width.
--
-- Widening rather than truncating: BR-10 makes the audit record a precondition of
-- the change, so an audit row that cannot hold its own explanation defeats the
-- purpose of having one.
ALTER TABLE ff4j_audit ALTER COLUMN evt_value TYPE VARCHAR(1000);
