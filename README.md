# sun-metro-board

Next bus departures for Sun Metro (El Paso) in the terminal, from the
agency's public GTFS feed. No dependencies, needs JDK 17+.

```
javac -d out src/sunboard/*.java

java -cp out sunboard.DepartureBoard find glory road
java -cp out sunboard.DepartureBoard 2600
java -cp out sunboard.DepartureBoard 2600 5
```

Stop id or the code on the sign both work. First run downloads
google_transit.zip into the current dir and reuses it, delete it to refresh.
`--gtfs some/other/feed.zip` works too, so this runs against pretty much any
agency's GTFS, not just Sun Metro.

Handles the usual GTFS stuff: holiday exceptions from calendar_dates.txt,
and times past 24:00 (a 25:10 departure belongs to yesterday's schedule but
shows up as 1:10 AM today). Schedule data only, no realtime.
