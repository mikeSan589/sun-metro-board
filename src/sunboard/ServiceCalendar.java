package sunboard;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class ServiceCalendar {

    private static final DateTimeFormatter YMD = DateTimeFormatter.BASIC_ISO_DATE;

    private static final class Entry {
        String serviceId;
        LocalDate start, end;
        boolean[] days = new boolean[7];
    }

    private final List<Entry> entries = new ArrayList<>();
    private final Map<LocalDate, Set<String>> added = new HashMap<>();
    private final Map<LocalDate, Set<String>> removed = new HashMap<>();

    void addCalendarRow(Csv.Row r) {
        Entry e = new Entry();
        e.serviceId = r.get("service_id");
        try {
            e.start = LocalDate.parse(r.get("start_date"), YMD);
            e.end = LocalDate.parse(r.get("end_date"), YMD);
        } catch (Exception ex) {
            return;
        }
        String[] cols = {"monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday"};
        for (int i = 0; i < 7; i++) {
            e.days[i] = r.get(cols[i]).equals("1");
        }
        entries.add(e);
    }

    // Type 1 adds service on a date, type 2 removes it. This is how holidays
    // work in GTFS (Labor Day = weekday service removed, Sunday service added).
    void addExceptionRow(Csv.Row r) {
        String serviceId = r.get("service_id");
        LocalDate date;
        try {
            date = LocalDate.parse(r.get("date"), YMD);
        } catch (Exception ex) {
            return;
        }
        String type = r.get("exception_type");
        if (type.equals("1")) {
            added.computeIfAbsent(date, d -> new HashSet<>()).add(serviceId);
        } else if (type.equals("2")) {
            removed.computeIfAbsent(date, d -> new HashSet<>()).add(serviceId);
        }
    }

    Set<String> activeOn(LocalDate date) {
        Set<String> active = new HashSet<>();
        DayOfWeek dow = date.getDayOfWeek();
        for (Entry e : entries) {
            if (date.isBefore(e.start) || date.isAfter(e.end)) continue;
            if (e.days[dow.getValue() - 1]) active.add(e.serviceId);
        }
        Set<String> add = added.get(date);
        if (add != null) active.addAll(add);
        Set<String> rem = removed.get(date);
        if (rem != null) active.removeAll(rem);
        return active;
    }
}
