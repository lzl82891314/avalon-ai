alter table audit_record add column execution_trace_json clob default '[]';
alter table audit_record add column policy_summary_json clob default '{}';
