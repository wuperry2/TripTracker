package com.example.TripTracker;

import android.content.Context;
import android.graphics.Color;
import android.location.Location;
import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.PolylineOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

public class TripManager {

    public interface TripDrawer {
        void onDrawTripLine(PolylineOptions options);
        void onDrawDistance(String distance);
    }

    public final int MODE_CURR = 0;
    public final int MODE_PAST = 1;
    public final double MIN_DISTANCE = 0.5; //minimum distance travelled to be recorded, in meters
    public final double MIN_SPEED = 1.0; //minimum speed that will be recorded
    public final double MAX_DISTANCE_FOR_METERS = 1000; // if distance is greater than this value (in meters),
                                                        // then distanceString will be in km instead of m
    public final String TAG_LOC = "LOC:";
    public final int CURR_TRIP_COLOR = Color.GREEN;
    public final int PAST_TRIP_COLOR = Color.RED;


    private List<LatLng> currLocations;
    private List<LatLng> pastLocations;
    private double currDistance;
    private double pastDistance;
    private int viewMode;
    private boolean isTracking;
    private int stoppedCounter;
    private PolylineOptions currPolyLine;
    private TripDrawer tripDrawer;
    private AppCompatActivity mapApp;
    private FileOutputStream tripWriter;
    private String tripFile;
    private String lastLine;

    // constructs a new TripManager object
    public TripManager(AppCompatActivity mapApp, int mode) {
        currLocations = new ArrayList<LatLng>();
        pastLocations = new ArrayList<LatLng>();
        pastDistance = 0;
        pastDistance = 0;
        this.viewMode = mode;
        isTracking  = false;
        stoppedCounter = 0;
        this.mapApp = mapApp;
        lastLine = "";

        currPolyLine = new PolylineOptions().width(5).geodesic(true).color(CURR_TRIP_COLOR);
    }

    public void setTripDrawer(TripDrawer drawer) {
        tripDrawer = drawer;
        mapApp = (AppCompatActivity)drawer;
    }

    public void setTripFile(String filename) {
        try {
            tripFile = filename;
            tripWriter = mapApp.openFileOutput(tripFile, Context.MODE_APPEND);
        } catch(Exception e) {}
    }

    // @return the current distance travelled
    public double getDistance() {
        if (viewMode == MODE_CURR)
            return currDistance;
        else
            return pastDistance;
    }

    public String getDistanceString() {
        double distance;
        if (viewMode == MODE_CURR)
            distance = currDistance;
         else
            distance = pastDistance;

        if (distance > MAX_DISTANCE_FOR_METERS)
            return round(distance / 1000, 2) + "km";
        else
            return round(distance, 2) + "m";
    }

    public void redrawTrip() {
        if (viewMode == MODE_PAST) {
            try {
                pastLocations.clear();
                pastDistance = 0.0;
                FileInputStream fileInputStream = mapApp.openFileInput(tripFile);
                InputStreamReader inputStreamReader = new InputStreamReader(fileInputStream);
                BufferedReader bufferedReader = new BufferedReader(inputStreamReader);
                LoaderTask loaderTask = new LoaderTask();
                loaderTask.execute(bufferedReader);
            } catch (Exception e) {
            }
        } else {
            drawCurrTrip();
        }
    }

    public List<LatLng> getTripLocations() {
        if (viewMode == MODE_CURR)
            return currLocations;
        else
            return pastLocations;
    }

    public void switchView() {
        if (viewMode == MODE_CURR) {
            viewMode = MODE_PAST;
        } else {
            viewMode = MODE_CURR;
            drawCurrTrip();
        }
    }

    public int getViewMode() {
        return viewMode;
    }

    public String getViewModeString() {
        if (viewMode == MODE_CURR)
            return "current trip";
        else
            return "past trip";
    }

    // resets the trip
    public void resetPastTrip() {
        File dir = mapApp.getFilesDir();
        try {
            if (tripWriter != null) {
                tripWriter.close();
            }
            new File(dir, tripFile).delete();
            setTripFile(tripFile);
        } catch(Exception e) {}

        pastLocations.clear();
        pastDistance = 0.0;
    }

    public void saveLocation(Location location) {
        if (tripWriter != null) {
            try {
                float gpsspeed = location.getSpeed() * 3600 / 1000;

                // only saves location if tracker is moving
                if (gpsspeed >= MIN_SPEED) {
                    stoppedCounter = 0;
                } else {
                    stoppedCounter ++;
                }

                if (stoppedCounter < 3 || stoppedCounter % 60 == 0) {
                    String line = TAG_LOC + location.getTime() + "," + location.getLatitude() + "," + location.getLongitude() + "," + gpsspeed + "\n";

                    if (!line.equals(lastLine)) {
                        tripWriter.write(line.getBytes());
                        tripWriter.flush();
                        lastLine = line;
                    }
                }
            } catch(Exception e) {}
        }
    }

    public boolean isTracking() {
        return isTracking;
    }

    public void startTrip() {
        isTracking = true;
    }

    public void stopTrip() {
        isTracking = false;
    }

    public void drawCurrTrip() {
        LoaderTask drawer = new LoaderTask();
        BufferedReader temp = null;
        drawer.execute(temp);
    }

    public void drawLine(LatLng nextLocation) {
        currPolyLine.add(nextLocation);
        if (tripDrawer != null) {
            tripDrawer.onDrawTripLine(currPolyLine);
            tripDrawer.onDrawDistance(getDistanceString());
        }
    }

    public void updateCurrentLocation(Location loc) {
        addLocation(currLocations, new LatLng(loc.getLatitude(), loc.getLongitude()), true);
        saveLocation(loc);
    }

    private void addLocation(List<LatLng> locations, LatLng loc, boolean toDraw) {
        if (isTracking) {
            if (locations.isEmpty() ||
                    locations.get(locations.size() - 1).latitude != loc.latitude ||
                    locations.get(locations.size() - 1).longitude != loc.longitude) {

                double dist = 0.0;

                if (locations.size() >= 2) {
                    dist = calcDistance(locations.get(locations.size() - 1), loc);
                }

                if (dist >= MIN_DISTANCE|| locations.size() < 2) {
                    locations.add(loc);
                    if (locations.equals(currLocations))
                        currDistance += dist;
                    else
                        pastDistance += dist;
                }

                if (toDraw && viewMode == MODE_CURR)
                    drawLine(loc);
            }
        }
    }

    private class LoaderTask extends AsyncTask<BufferedReader, Integer, PolylineOptions> {
        @Override
        protected PolylineOptions doInBackground(BufferedReader... input) {

            PolylineOptions options = new PolylineOptions().width(5).geodesic(true);
            if (input[0] != null) {
                try {

                    BufferedReader bufferedReader = input[0];
                    String lines;
                    int lineNum = 0;

                    while ((lines = bufferedReader.readLine()) != null) {
                        if (lines.startsWith(TAG_LOC)) {
                            String[] data = lines.split(",");

                            //double speed = Double.parseDouble(data[3]);
                            //long  time = Long.parseLong(data[0].substring(TAG_LOC.length()));
                            double lat = Double.parseDouble(data[1]);
                            double lng = Double.parseDouble(data[2]);

                            lineNum++;
                            //Log.d(TripMap.TAG,"#" + lineNum + " " + convertTimeWithTimeZome(time) + ", Speed = " + speed + ", lat/lng = " +
                            //        lat + "/" + lng);
                            //convertTimeWithTimeZome(time);

                            addLocation(pastLocations, new LatLng(lat, lng), false);
                        }
                    }

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                options.addAll(pastLocations);
                options.color(Color.RED);
            } else {
                if (viewMode == MODE_CURR) {
                    options.addAll(currLocations);
                    options.color(CURR_TRIP_COLOR);
                } else {
                    options.addAll(pastLocations);
                    options.color(PAST_TRIP_COLOR);
                }
            }

            return options;
        }



        @Override
        protected void onPostExecute(PolylineOptions result) {
            if (tripDrawer != null && result != null) {
                tripDrawer.onDrawTripLine(result);
                tripDrawer.onDrawDistance(getDistanceString());
            }
        }
    }

    // @Return distance btwn 2 LatLng in meters
    private double calcDistance(LatLng start, LatLng dest) {
        int R = 6371; // radius of earth

        double latDistance = Math.toRadians(dest.latitude - start.latitude);
        double lngDistance = Math.toRadians(dest.longitude - start.longitude);

        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
                + Math.cos(Math.toRadians(start.latitude)) * Math.cos(Math.toRadians(dest.latitude))
                * Math.sin(lngDistance / 2) * Math.sin(lngDistance / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return R * c * 1000; // convert to meters
    }

    // decimals = # of decimal points to round to (must be >= 1)
    private double round(double x, int decimals) {
        return Math.round(x * Math.pow(10, decimals)) / Math.pow(10, decimals);
    }

    public String convertTimeWithTimeZome(long time) {
        Calendar cal = Calendar.getInstance();
        /*
        cal.setTimeZone(TimeZone.getTimeZone("UTC"));
        cal.setTimeInMillis(time);
        String curTime = String.format("%s-%s-%s  %02d:%02d:%02d",cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), cal.get(Calendar.SECOND));
        */


        TimeZone tz = cal.getTimeZone();

        /* debug: is it local time? */
        Log.d("Time zone: ", tz.getDisplayName());

        /* date formatter in local timezone */
        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");
        sdf.setTimeZone(tz);

        /* print your timestamp and double check it's the date you expect */
        String localTime = sdf.format(new Date(time)); // I assume your timestamp is in seconds and you're converting to milliseconds?
        Log.d("Time: ", localTime);


        return localTime;
    }
}
