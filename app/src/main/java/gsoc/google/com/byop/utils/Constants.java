package gsoc.google.com.byop.utils;

import com.google.api.services.drive.DriveScopes;

/**
 * Created by lgwork on 26/05/16.
 */
public class Constants {
    /**
     * Standard activity result: operation canceled.
     */
    public static final int RESULT_CANCELED = 0;
    /**
     * Standard activity result: operation succeeded.
     */
    public static final int RESULT_OK = -1;
    /**
     * Start of user-defined activity results.
     */
    public static final int RESULT_FIRST_USER = 1;

    public static final int RC_SIGN_IN = 9001;

    public static final int REQUEST_ACCOUNT_PICKER = 1000;
    public static final int REQUEST_AUTHORIZATION = 1001;
    public static final int REQUEST_GOOGLE_PLAY_SERVICES = 1002;
    public static final int REQUEST_PERMISSION_GET_ACCOUNTS = 1003;
    //  public static final String PREF_ACCOUNT_NAME = "accountName";
    public static final String PREF_ACCOUNT_EMAIL = "accountEmail";



    /**
     * Location
     *
     **/
    public static final int LOCATION_PERMISSION_REQUEST_CODE = 1;

    public static final String[] SCOPES = {DriveScopes.DRIVE};
    public static final String DRIVE_SCOPE = "https://www.googleapis.com/auth/drive";
}
