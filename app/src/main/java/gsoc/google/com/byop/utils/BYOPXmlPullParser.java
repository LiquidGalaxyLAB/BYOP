package gsoc.google.com.byop.utils;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by lgwork on 26/05/16.
 */
public class BYOPXmlPullParser {

    private static final String ns = null;

    // We don't use namespaces

    public List<POI> parse(InputStream in) throws XmlPullParserException, IOException {
        try {
            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(in, null);
            parser.nextTag();
            return readPOIS(parser);
        } finally {
            in.close();
        }
    }

    private List<POI> readPOIS(XmlPullParser parser) throws XmlPullParserException, IOException {
        List<POI> entries = new ArrayList<POI>();

        parser.require(XmlPullParser.START_TAG, ns, "kml");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the entry tag
            if (name.equals("Folder")) {
                entries.add(readEntry(parser));
            } else if (!name.equals("Document")) {
                skip(parser);
            }
        }
        return entries;
    }

    public static class Point {
        public final String latitude;
        public final String longitude;

        public Point() {
            this.latitude = "";
            this.longitude = "";
        }

        private Point(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public String getLatitude() {
            return latitude;
        }

        public String getLongitude() {
            return longitude;
        }
    }

    // This class represents a single entry (post) in the XML feed.
    // It includes the data members "title," "link," and "summary."
    public static class POI {
        public final String title;
        public final Point point;

        public POI() {
            this.title = "";
            this.point = new Point();
        }

        public POI(String title, Point point) {
            this.title = title;
            this.point = point;
        }

        public String getTitle() {
            return title;
        }

        public Point getPoint() {
            return point;
        }
    }

    // Parses the contents of an entry. If it encounters a title, summary, or link tag, hands them
    // off
    // to their respective &quot;read&quot; methods for processing. Otherwise, skips the tag.
    private POI readEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "Placemark");
        String title = null;
        Point point = null;
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("name")) {
                title = readTitle(parser);
            } else if (name.equals("Point")) {
                point = readPoint(parser);
            } else if (!name.equals("Placemark")) {
                skip(parser);
            }
        }
        return new POI(title, point);
    }

    // Processes title tags in the feed.
    private String readTitle(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "name");
        String title = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "name");
        return title;
    }

    // Processes link tags in the feed.
    private Point readPoint(XmlPullParser parser) throws IOException, XmlPullParserException {
        Point point = new Point();
        parser.require(XmlPullParser.START_TAG, ns, "Point");
        String tag = parser.getName();

        if (tag.equals("Point")) {
            point = readCoordinates(parser);
        }
        parser.require(XmlPullParser.END_TAG, ns, "Point");
        return point;
    }

    private Point readCoordinates(XmlPullParser parser) throws IOException, XmlPullParserException {
        String coordinates = "";
        String[] coordinatesSplit = new String[]{};
        //  parser.require(XmlPullParser.START_TAG, ns, "coordinates");
        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equals("coordinates")) {
                coordinates = readText(parser);
                coordinatesSplit = coordinates.split(",");
            } else {
                skip(parser);
            }
        }
        parser.require(XmlPullParser.END_TAG, ns, "coordinates");

        return new Point(coordinatesSplit[1], coordinatesSplit[0]);
    }


    // For the tags title and summary, extracts their text values.
    private String readText(XmlPullParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    // Skips tags the parser isn't interested in. Uses depth to handle nested tags. i.e.,
    // if the next tag after a START_TAG isn't a matching END_TAG, it keeps going until it
    // finds the matching END_TAG (as indicated by the value of "depth" being 0).
    private void skip(XmlPullParser parser) throws XmlPullParserException, IOException {
        if (parser.getEventType() != XmlPullParser.START_TAG) {
            throw new IllegalStateException();
        }
        int depth = 1;
        while (depth != 0) {
            switch (parser.next()) {
                case XmlPullParser.END_TAG:
                    depth--;
                    break;
                case XmlPullParser.START_TAG:
                    depth++;
                    break;
            }
        }
    }

}
