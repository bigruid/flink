package org.apache.flink.connector.jdbc.dialect;

import org.apache.flink.connector.jdbc.internal.converter.JdbcRowConverter;
import org.apache.flink.connector.jdbc.internal.converter.VerticaRowConverter;
import org.apache.flink.table.types.logical.LogicalTypeRoot;
import org.apache.flink.table.types.logical.RowType;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * @author zengrd
 * @date 2023/5/8
 */
public class VerticaDialect extends AbstractDialect{

    private static final long serialVersionUID = 1L;

    // Define MAX/MIN precision of TIMESTAMP type according to Vertica docs:
    // https://www.vertica.com/docs/latest/HTML/Content/Authoring/SQLReferenceManual/DataTypes/Date-Time/TIMESTAMP.htm
    private static final int MAX_TIMESTAMP_PRECISION = 6;
    private static final int MIN_TIMESTAMP_PRECISION = 0;

    // Define MAX/MIN precision of DECIMAL type according to Vertica docs:
    // https://www.vertica.com/docs/11.1.x/HTML/Content/Authoring/SQLReferenceManual/DataTypes/Numeric/NUMERIC.htm
    private static final int MAX_DECIMAL_PRECISION = 1024;
    private static final int MIN_DECIMAL_PRECISION = 0;

    /**
     * Get the name of jdbc dialect.
     *
     * @return the dialect name.
     */
    @Override
    public String dialectName() {
        return "Vertica";
    }

    @Override
    public boolean canHandle(String url) {
        return url.startsWith("jdbc:vertica:");
    }

    /**
     * Get converter that convert jdbc object and Flink internal object each other.
     *
     * @param rowType the given row type
     * @return a row converter for the database
     */
    @Override
    public JdbcRowConverter getRowConverter(RowType rowType) {
        return new VerticaRowConverter(rowType);
    }

    @Override
    public String getLimitClause(long limit) {
        return "LIMIT " + limit;
    }

    @Override
    public Optional<String> defaultDriverName() {
        return Optional.of("com.vertica.jdbc.Driver");
    }

    @Override
    public Optional<String> getUpsertStatement(
            String tableName, String[] fieldNames, String[] uniqueKeyFields) {

        String sourceFields =
                Arrays.stream(fieldNames)
                        .map(f -> ":" + f + " " + quoteIdentifier(f))
                        .collect(Collectors.joining(", "));

        // deduplicate on temporary bach insert part for Exactly-Once
        String uniqueFields =
                Arrays.stream(uniqueKeyFields)
                        .map(this::quoteIdentifier)
                        .collect(Collectors.joining(", "));

        String onClause =
                Arrays.stream(uniqueKeyFields)
                        .map(f -> "t." + quoteIdentifier(f) + "=s." + quoteIdentifier(f))
                        .collect(Collectors.joining(" and "));

        String updateClause =
                Arrays.stream(fieldNames)
                        .map(f -> quoteIdentifier(f) + "=s." + quoteIdentifier(f))
                        .collect(Collectors.joining(", "));

        String insertFields =
                Arrays.stream(fieldNames)
                        .map(this::quoteIdentifier)
                        .collect(Collectors.joining(", "));

        String valuesClause =
                Arrays.stream(fieldNames)
                        .map(f -> "s." + quoteIdentifier(f))
                        .collect(Collectors.joining(", "));

        String mergeQuery =
                "MERGE INTO "
                        + tableName
                        + " t "
                        + " USING (SELECT "
                        + sourceFields
                        + " FROM DUAL LIMIT 1 OVER(PARTITION BY "
                        + uniqueFields
                        + " ORDER BY "
                        + uniqueFields
                        + ")) s "
                        + " ON ("
                        + onClause
                        + ") "
                        + " WHEN MATCHED THEN UPDATE SET "
                        + updateClause
                        + " WHEN NOT MATCHED THEN INSERT ("
                        + insertFields
                        + ")"
                        + " VALUES ("
                        + valuesClause
                        + ")";

        return Optional.of(mergeQuery);
    }

    @Override
    public String quoteIdentifier(String identifier) {
        return identifier;
    }

    @Override
    public int maxDecimalPrecision() {
        return MAX_DECIMAL_PRECISION;
    }

    @Override
    public int minDecimalPrecision() {
        return MIN_DECIMAL_PRECISION;
    }

    @Override
    public int maxTimestampPrecision() {
        return MAX_TIMESTAMP_PRECISION;
    }

    @Override
    public int minTimestampPrecision() {
        return MIN_TIMESTAMP_PRECISION;
    }

    /**
     * Defines the unsupported types for the dialect.
     *
     * @return a list of logical type roots.
     */
    @Override
    public List<LogicalTypeRoot> unsupportedTypes() {
        // TODO: We can't convert BINARY data type to
        //  PrimitiveArrayTypeInfo.BYTE_PRIMITIVE_ARRAY_TYPE_INFO in
        // LegacyTypeInfoDataTypeConverter.
        return Arrays.asList(
                LogicalTypeRoot.BINARY,
                LogicalTypeRoot.INTERVAL_YEAR_MONTH,
                LogicalTypeRoot.INTERVAL_DAY_TIME,
                LogicalTypeRoot.MULTISET,
                LogicalTypeRoot.MAP,
                LogicalTypeRoot.ROW,
                LogicalTypeRoot.DISTINCT_TYPE,
                LogicalTypeRoot.STRUCTURED_TYPE,
                LogicalTypeRoot.NULL,
                LogicalTypeRoot.RAW,
                LogicalTypeRoot.SYMBOL,
                LogicalTypeRoot.UNRESOLVED);
    }
}
