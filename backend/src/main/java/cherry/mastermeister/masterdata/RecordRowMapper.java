/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cherry.mastermeister.masterdata;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.RowMapper;

public class RecordRowMapper implements RowMapper<List<Object>> {

    @Override
    public List<Object> mapRow(ResultSet rs, int rowNum) throws SQLException {
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<Object> row = new ArrayList<>(columnCount);
        for (int i = 1; i <= columnCount; i++) {
            row.add(mapColumn(rs, metaData, i));
        }
        return row;
    }

    private Object mapColumn(ResultSet rs, ResultSetMetaData metaData, int columnIndex) throws SQLException {
        return switch (metaData.getColumnType(columnIndex)) {
            case Types.DATE -> rs.getObject(columnIndex, LocalDate.class);
            case Types.TIME, Types.TIME_WITH_TIMEZONE -> rs.getObject(columnIndex, LocalTime.class);
            case Types.TIMESTAMP -> rs.getObject(columnIndex, LocalDateTime.class);
            case Types.TIMESTAMP_WITH_TIMEZONE -> rs.getObject(columnIndex, OffsetDateTime.class);
            case Types.NUMERIC, Types.DECIMAL -> rs.getObject(columnIndex, BigDecimal.class);
            default -> rs.getObject(columnIndex);
        };
    }

}