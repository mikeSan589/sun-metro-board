package sunboard;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Quick CSV parser. GTFS has quoted fields with commas inside
// them so String.split(",") won't cut it, but we don't need a library either.
final class Csv {

    static final class Row {
        private final Map<String, Integer> header;
        private final List<String> fields;

        Row(Map<String, Integer> header, List<String> fields) {
            this.header = header;
            this.fields = fields;
        }

        String get(String col) {
            Integer i = header.get(col);
            if (i == null || i >= fields.size()) return "";
            return fields.get(i).trim();
        }
    }

    interface RowHandler {
        void row(Row r);
    }

    private Csv() {}

    static void parse(Reader in, RowHandler handler) throws IOException {
        Map<String, Integer> header = null;
        List<String> fields = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        boolean firstChar = true;

        int c;
        while ((c = in.read()) != -1) {
            if (firstChar) {
                firstChar = false;
                if (c == '\ufeff') continue; // BOM
            }
            if (inQuotes) {
                if (c == '"') {
                    int next = in.read();
                    if (next == '"') {
                        cur.append('"');
                    } else {
                        inQuotes = false;
                        c = next;
                        if (c == -1) break;
                        if (c == ',') {
                            fields.add(cur.toString());
                            cur.setLength(0);
                        } else if (c == '\n' || c == '\r') {
                            header = endLine(header, fields, cur, handler);
                            if (c == '\r') in.read();
                        }
                    }
                } else {
                    cur.append((char) c);
                }
            } else if (c == '"' && cur.length() == 0) {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(cur.toString());
                cur.setLength(0);
            } else if (c == '\n') {
                header = endLine(header, fields, cur, handler);
            } else if (c != '\r') {
                cur.append((char) c);
            }
        }
        if (cur.length() > 0 || !fields.isEmpty()) {
            endLine(header, fields, cur, handler);
        }
    }

    private static Map<String, Integer> endLine(Map<String, Integer> header,
                                                List<String> fields,
                                                StringBuilder cur,
                                                RowHandler handler) {
        fields.add(cur.toString());
        cur.setLength(0);
        if (fields.size() == 1 && fields.get(0).isEmpty()) {
            fields.clear();
            return header;
        }
        if (header == null) {
            header = new HashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                header.put(fields.get(i).trim(), i);
            }
        } else {
            handler.row(new Row(header, new ArrayList<>(fields)));
        }
        fields.clear();
        return header;
    }
}
