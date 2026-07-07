package sunboard;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.zip.ZipFile;

public final class DepartureBoard {

    private static final String FEED_URL =
        "http://transit.sunmetro.net/google_transit/google_transit.zip";
    private static final Path CACHE = Path.of("google_transit.zip");

    // NB: El Paso is Mountain time, unlike the rest of Texas
    private static final ZoneId TZ = ZoneId.of("America/Denver");

    public static void main(String[] args) throws Exception {
        Path zipPath = null;
        List<String> rest = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("--gtfs") && i + 1 < args.length) {
                zipPath = Path.of(args[++i]);
            } else {
                rest.add(args[i]);
            }
        }
        if (rest.isEmpty()) {
            usage();
            return;
        }

        if (zipPath == null) zipPath = fetchFeed();

        Feed feed;
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            feed = Feed.load(zip);
        }

        if (rest.get(0).equalsIgnoreCase("find")) {
            findStops(feed, rest.subList(1, rest.size()));
        } else {
            int limit = 3;
            if (rest.size() > 1) {
                try {
                    limit = Integer.parseInt(rest.get(1));
                } catch (NumberFormatException e) {
                    System.err.println("count should be a number, using 3");
                }
            }
            printBoard(feed, rest.get(0), limit);
        }
    }

    private static void printBoard(Feed feed, String stopId, int limit) {
        Feed.Stop stop = feed.stops.get(stopId);
        if (stop == null) {
            // maybe they typed the code off the sign instead of the id
            for (Feed.Stop s : feed.stops.values()) {
                if (s.code.equals(stopId)) {
                    stop = s;
                    break;
                }
            }
        }
        if (stop == null) {
            System.out.println("No stop with id or code \"" + stopId + "\". Try: find <part of name>");
            return;
        }

        ZonedDateTime now = ZonedDateTime.now(TZ);
        int nowSecs = now.toLocalTime().toSecondOfDay();
        List<Feed.Departure> next = feed.nextDepartures(stop.id, now.toLocalDate(), nowSecs, limit);

        System.out.println();
        System.out.println(stop.name + "  (stop " + stop.id + ")");
        System.out.println("-".repeat(Math.max(30, stop.name.length() + 14)));

        if (next.isEmpty()) {
            System.out.println("No more departures today.");
            return;
        }
        for (Feed.Departure d : next) {
            String route = feed.routeNames.getOrDefault(d.trip.routeId, d.trip.routeId);
            String dest = d.trip.headsign.isEmpty() ? route : route + " -> " + d.trip.headsign;
            System.out.printf("%9s   %-38s %s%n",
                clock(d.scheduledSecs), trim(dest, 38), wait(d.waitSecs));
        }
        System.out.println();
    }

    private static void findStops(Feed feed, List<String> words) {
        if (words.isEmpty()) {
            System.out.println("find what?");
            return;
        }
        String needle = String.join(" ", words).toLowerCase(Locale.ROOT);
        int hits = 0;
        for (Feed.Stop s : feed.stops.values()) {
            if (s.name.toLowerCase(Locale.ROOT).contains(needle) || s.code.equals(needle)) {
                System.out.printf("%-8s %s%n", s.id, s.name);
                if (++hits >= 25) {
                    System.out.println("(more matches, narrow it down)");
                    break;
                }
            }
        }
        if (hits == 0) System.out.println("No stops matching \"" + needle + "\"");
    }

    private static String clock(int secs) {
        int h = (secs / 3600) % 24;
        int m = (secs % 3600) / 60;
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format("%d:%02d %s", h12, m, h < 12 ? "AM" : "PM");
    }

    private static String wait(int secs) {
        int min = secs / 60;
        if (min == 0) return "due";
        if (min < 60) return "in " + min + " min";
        return "in " + (min / 60) + "h " + (min % 60) + "m";
    }

    private static String trim(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }

    private static Path fetchFeed() throws IOException, InterruptedException {
        if (Files.exists(CACHE)) {
            return CACHE;
        }
        System.err.println("downloading " + FEED_URL + " ...");
        HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(20))
            .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(FEED_URL)).build();
        HttpResponse<Path> resp = client.send(req, HttpResponse.BodyHandlers.ofFile(CACHE));
        if (resp.statusCode() != 200) {
            Files.deleteIfExists(CACHE);
            throw new IOException("feed download failed, HTTP " + resp.statusCode());
        }
        return CACHE;
    }

    private static void usage() {
        System.out.println("usage:");
        System.out.println("  DepartureBoard find <part of stop name>");
        System.out.println("  DepartureBoard <stop_id> [count]");
        System.out.println("  --gtfs path/to/feed.zip to use a local copy");
    }
}
