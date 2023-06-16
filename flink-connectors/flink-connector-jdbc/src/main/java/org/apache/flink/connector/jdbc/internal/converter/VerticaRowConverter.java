package org.apache.flink.connector.jdbc.internal.converter;

import org.apache.flink.table.types.logical.LogicalType;
import org.apache.flink.table.types.logical.RowType;

/**
 * @author zengrd
 * @date 2023/5/8
 */
public class VerticaRowConverter extends AbstractJdbcRowConverter {

    private static final long serialVersionUID = 1L;

    @Override
    public String converterName() {
        return "Vertica";
    }

    public VerticaRowConverter(RowType rowType) {
        super(rowType);
    }

    @Override
    public JdbcDeserializationConverter createInternalConverter(LogicalType type) {
        switch (type.getTypeRoot()) {
            case TINYINT:
                return val -> val instanceof Long ? ((Long) val).byteValue() : val;
            case SMALLINT:
                return val -> val instanceof Long ? ((Long) val).shortValue() : val;
            case INTEGER:
            case BIGINT:
                return val -> val instanceof Long ? ((Long) val).intValue() : val;
            case FLOAT:
                return val -> val instanceof Double ? ((Double) val).floatValue() : val;
            default:
                return super.createInternalConverter(type);
        }
    }
}
