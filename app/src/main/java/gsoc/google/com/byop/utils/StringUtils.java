package gsoc.google.com.byop.utils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Created by lgwork on 27/05/16.
 */
public class StringUtils {

    // convert InputStream to String
    public static String getStringFromInputStream(InputStream is) {

        BufferedReader r = new BufferedReader(new InputStreamReader(is));
        StringBuilder total = new StringBuilder();
        String line;
        try {
            while ((line = r.readLine()) != null) {
                total.append(line).append('\n');
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return total.toString();
    }

}
