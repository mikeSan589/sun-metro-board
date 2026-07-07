package sunboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

final class Feed {

    static final class Stop {
        final String id, code, name;
        Stop(String id, String code, String name) {
            this.id = id;
            this.code = code;
            this.name = name;
        }
    }

    static final class Trip {
        final String routeId, serviceId, headsign;
        Trip(String routeId, String serviceId, String headsign) {
            this.routeId = routeId;
            this.serviceId = serviceId;
            this.headsign = headsign;
        }
    }

    static final class StopEvent {
        final int seconds;
        final String tripId;
        StopEvent(int seconds, String tripId) {
            this.seconds = seconds;
            this.tripId = tripId;
        }
    }

    static final class Departure {
        final int waitSecs;
        final int scheduledSecs;
        final Trip trip;
        Departure(int waitSecs, int scheduledSecs, Trip trip) {
            this.waitSecs = waitSecs;
            this.scheduledSecs = scheduledSecs;
            this.trip = trip;
        }
    }

    final Map<String, Stop> stops = new HashMap<>();
    final Map<String, String> routeNames = new HashMap<>();
    final Map<String, Trip> trips = new HashMap<>();
    final Map<String, List<StopEvent>> eventsByStop = new HashMap<>();
    final ServiceCalendar calendar = new ServiceCalendar();

    static Feed load(ZipFile zip) throws IOException {
        Feed feed = new Feed();

        feed.eachRow(zip, "stops.txt", r ->
            feed.stops.put(r.get("stop_id"),
                new Stop(r.get("stop_id"), r.get("stop_code"), r.get("stop_name"))));

        feed.eachRow(zip, "routes.txt", r -> {
            String shortName = r.get("route_short_name");
            String longName = r.get("route_long_name");
            String name = shortName.isEmpty() ? longName
                        : longName.isEmpty() ? shortName
                        : shortName + " " + longName;
            feed.routeNames.put(r.get("route_id"), name);
        });

        feed.eachRow(zip, "trips.txt", r ->
            feed.trips.put(r.get("trip_id"),
                new Trip(r.get("route_id"), r.get("service_id"), r.get("trip_headsign"))));

        feed.eachRow(zip, "stop_times.txt", r -> {
            String t = r.get("departure_time");
            if (t.isEmpty()) t = r.get("arrival_time");
            int secs = parseTime(t);
            if (secs < 0) return;
            feed.eventsByStop
                .computeIfAbsent(r.get("stop_id"), k -> new ArrayList<>())
                .add(new StopEvent(secs, r.get("trip_id")));
        });

        for (List<StopEvent> list : feed.eventsByStop.values()) {
            list.sort(Comparator.comparingInt(e -> e.seconds));
        }

        feed.eachRow(zip, "calendar.txt", feed.calendar::addCalendarRow);
        feed.eachRow(zip, "calendar_dates.txt", feed.calendar::addExceptionRow);

        return feed;
    }

    List<Departure> nextDepartures(String stopId, LocalDate today, int nowSecs, int limit) {
        List<StopEvent> events = eventsByStop.get(stopId);
        List<Departure> out = new ArrayList<>();
        if (events == null) return out;

        Set<String> todayServices = calendar.activeOn(today);
        Set<String> yesterdayServices = calendar.activeOn(today.minusDays(1));

        for (StopEvent e : events) {
            Trip trip = trips.get(e.tripId);
            if (trip == null) continue;

            if (todayServices.contains(trip.serviceId) && e.seconds >= nowSecs) {
                out.add(new Departure(e.seconds - nowSecs, e.seconds, trip));
            }
            // GTFS allows times past 24:00. A 25:10 departure is 1:10 AM on the
            // clock but still belongs to yesterday's service day, so check those too.
            int fromYesterday = e.seconds - 24 * 3600;
            if (fromYesterday >= nowSecs && yesterdayServices.contains(trip.serviceId)) {
                out.add(new Departure(fromYesterday - nowSecs, e.seconds, trip));
            }
        }

        out.sort(Comparator.comparingInt(d -> d.waitSecs));
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    // hours can go past 23
    static int parseTime(String hms) {
        if (hms == null || hms.isEmpty()) return -1;
        String[] p = hms.split(":");
        if (p.length != 3) return -1;
        try {
            return Integer.parseInt(p[0].trim()) * 3600
                 + Integer.parseInt(p[1].trim()) * 60
                 + Integer.parseInt(p[2].trim());
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void eachRow(ZipFile zip, String name, Csv.RowHandler handler) throws IOException {
        ZipEntry entry = zip.getEntry(name);
        if (entry == null) return;
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))) {
            Csv.parse(in, handler);
        }
    }
}
