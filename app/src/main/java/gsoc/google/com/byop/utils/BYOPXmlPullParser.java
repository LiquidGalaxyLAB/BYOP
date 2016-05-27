package gsoc.google.com.byop.utils;

import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.model.Point;

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
            // Starts by looking for the folder tag
            if (name.equalsIgnoreCase("Folder")) {
                entries = readFolder(parser);
            } else if (!name.equalsIgnoreCase("Document")) {
                skip(parser);
            }
        }
        return entries;
    }

    private List<POI> readFolder(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "Folder");

        List<POI> entries = new ArrayList<POI>();

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase("Placemark")) {
                entries.add(readPlacemark(parser));
            } else {
                skip(parser);
            }
        }
        return entries;
    }


    private POI readPlacemark(XmlPullParser parser) throws XmlPullParserException, IOException {
        parser.require(XmlPullParser.START_TAG, ns, "Placemark");

        String poiName = "";
        String poiDescription = "";
        Point point = null;

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase("name")) {
                poiName = readPoiName(parser);
            } else if (name.equals("description")) {
                poiDescription = readPoiDescription(parser);
            } else if (name.equalsIgnoreCase("Point")) {
                point = readPoint(parser);
            } else if (!name.equalsIgnoreCase("Placemark")) {
                skip(parser);
            }
        }
        return new POI(poiName, poiDescription, point);
    }

    // Processes the name of the placemark
    private String readPoiName(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "name");
        String title = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "name");
        return title;
    }

    // Processes the description of the placemark
    private String readPoiDescription(XmlPullParser parser) throws IOException, XmlPullParserException {
        parser.require(XmlPullParser.START_TAG, ns, "description");
        String description = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "description");
        return description;
    }

    // Processes the point of the placemark
    private Point readPoint(XmlPullParser parser) throws IOException, XmlPullParserException {
        Point point = new Point();
        parser.require(XmlPullParser.START_TAG, ns, "Point");
        String tag = parser.getName();

        if (tag.equalsIgnoreCase("Point")) {
            point = readCoordinates(parser);
        }
        parser.require(XmlPullParser.END_TAG, ns, "Point");
        return point;
    }

    private Point readCoordinates(XmlPullParser parser) throws IOException, XmlPullParserException {

        parser.require(XmlPullParser.START_TAG, ns, "Point");

        Point point = new Point();

        while (parser.next() != XmlPullParser.END_TAG) {
            if (parser.getEventType() != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            if (name.equalsIgnoreCase("coordinates")) {
                point = readCoordinatesString(parser);
            } else {
                skip(parser);
            }
        }
        return point;

    }

    private Point readCoordinatesString(XmlPullParser parser) throws IOException, XmlPullParserException {

        String coordinates = "";
        String[] coordinatesSplit = new String[]{};

        parser.require(XmlPullParser.START_TAG, ns, "coordinates");
        coordinates = readText(parser);
        parser.require(XmlPullParser.END_TAG, ns, "coordinates");

        coordinatesSplit = coordinates.split(",");

        return new Point(coordinatesSplit[1], coordinatesSplit[0]);
    }


    // Extract the text value of a tag
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
