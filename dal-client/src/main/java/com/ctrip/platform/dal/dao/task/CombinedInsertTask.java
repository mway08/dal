package com.ctrip.platform.dal.dao.task;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.ctrip.platform.dal.dao.DalHints;
import com.ctrip.platform.dal.dao.DalContextClient;
import com.ctrip.platform.dal.dao.StatementParameters;
import com.ctrip.platform.dal.exceptions.DalRuntimeException;

public class CombinedInsertTask<T> extends InsertTaskAdapter<T> implements BulkTask<Integer, T>, KeyHolderAwaredTask {
	public static final String TMPL_SQL_MULTIPLE_INSERT = "INSERT INTO %s(%s) VALUES %s";

	@Override
	public Integer getEmptyValue() {
		return 0;
	}

//	@Override
//	public BulkTaskContext<T> createTaskContext(DalHints hints, List<Map<String, ?>> daoPojos, List<T> rawPojos) throws SQLException {
//		BulkTaskContext<T> context = new BulkTaskContext<T>(rawPojos);
//		Set<String> unqualifiedColumns = filterUnqualifiedColumns(hints, daoPojos, rawPojos);
//		context.setUnqualifiedColumns(unqualifiedColumns);
//		if (context instanceof DalContextConfigure)
//			context.setShardingCategory(shardingCategory);
//		return context;
//	}

	@Override
	public Integer execute(DalHints hints, Map<Integer, Map<String, ?>> daoPojos, DalBulkTaskContext<T> taskContext) throws SQLException {
		StatementParameters parameters = new StatementParameters();
		StringBuilder values = new StringBuilder();

		Set<String> unqualifiedColumns = taskContext.getUnqualifiedColumns();
		
		List<String> finalInsertableColumns = buildValidColumnsForInsert(unqualifiedColumns);
		
		String insertColumns = combineColumns(finalInsertableColumns, COLUMN_SEPARATOR);

		List<Map<String, Object>> identityFields = new ArrayList<>();
		
		int startIndex = 1;
		for (Integer index :daoPojos.keySet()) {
			Map<String, ?> pojo = daoPojos.get(index);
			removeUnqualifiedColumns(pojo, unqualifiedColumns);

			Map<String, Object> identityField = getIdentityField(pojo);
			if (identityField != null) {
				identityFields.add(identityField);
			}

			int paramCount = addParameters(startIndex, parameters, pojo, finalInsertableColumns);
			startIndex += paramCount;
			values.append(String.format("(%s),", combine("?", paramCount, ",")));
		}

		// Put identityFields into context
		if (taskContext instanceof DefaultTaskContext)
			((DefaultTaskContext) taskContext).setIdentityFields(identityFields);

		String tableName = getRawTableName(hints);
		if (taskContext instanceof DalContextConfigure) {
			((DalContextConfigure) taskContext).addTables(tableName);
			((DalContextConfigure) taskContext).setShardingCategory(shardingCategory);
		}

		String sql = String.format(TMPL_SQL_MULTIPLE_INSERT,
				quote(tableName), insertColumns,
				values.substring(0, values.length() - 2) + ")");

		if (client instanceof DalContextClient)
			return ((DalContextClient) client).update(sql, parameters, hints, taskContext);
		else
			throw new DalRuntimeException("The client is not instance of DalClient");
	}

	@Override
	public BulkTaskResultMerger<Integer> createMerger() {
		return new ShardedIntResultMerger();
	}
}