package gsoc.google.com.byop.utils;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.model.Point;

/**
 * Created by lgwork on 27/05/16.
 */
public class POIUtils {

    private static FragmentActivity activity;
    private static DriveDocument driveDoc;
    private static GoogleApiClient googleApiClient;

    private static POI managedPoi;


    public static void deletePOI(String poiName, String poiDescription, String poiLatitude, String poiLongitude, DriveDocument document, GoogleApiClient mGoogleApiClient, FragmentActivity act) {
        activity = act;
        driveDoc = document;
        googleApiClient = mGoogleApiClient;
        Point point = new Point(poiLatitude, poiLongitude);
        managedPoi = new POI(poiName, poiDescription, point);
    }

    /**
     * An asynchronous task that handles the Drive API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class DeletePOITask extends AsyncTask<Void, Void, List<POI>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String folderId = "";
        private ProgressDialog dialog;

        private List<POI> innerPOIList = new ArrayList<POI>();
        private boolean isCompleted = false;

        public DeletePOITask(GoogleAccountCredential credential, DriveDocument document) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(activity.getResources().getString(R.string.app_name))
                    .build();


        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(activity.getApplicationContext());
                dialog.setMessage(activity.getResources().getString(R.string.loading));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        //requestContentsTask.cancel(true);
                    }
                });
                dialog.show();
            }
        }

        /**
         * Background task to call Drive API.
         *
         * @param params no parameters needed for this task.
         */
        @Override
        protected List<POI> doInBackground(Void... params) {
            try {
                return getFileContentsFromApi();
            } catch (Exception e) {
                mLastError = e;
                cancel(true);
                return null;
            }
        }

        /**
         * Fetch a list of up to 10 file names and IDs.
         *
         * @return List of Strings describing files, or an empty list if no files
         * found.
         * @throws IOException
         */
        private List<POI> getFileContentsFromApi() throws IOException {

            File driveFile = mService.files().get(driveDoc.getResourceId()).execute();

            //DRIVE API
            Drive.DriveApi.fetchDriveId(googleApiClient, driveFile.getId()).setResultCallback(new ResultCallback<DriveApi.DriveIdResult>() {
                @Override
                public void onResult(@NonNull DriveApi.DriveIdResult driveIdResult) {
                    if (driveIdResult.getStatus().isSuccess()) {
                        final DriveFile[] file = {Drive.DriveApi.getFile(googleApiClient, driveIdResult.getDriveId())};
                        file[0].open(googleApiClient, DriveFile.MODE_READ_ONLY, null)
                                .setResultCallback(new ResultCallback<DriveApi.DriveContentsResult>() {
                                    @Override
                                    public void onResult(@NonNull DriveApi.DriveContentsResult driveContentsResult) {
                                        if (!driveContentsResult.getStatus().isSuccess()) {
                                            // display an error saying file can't be opened
                                            return;
                                        }
                                        // DriveContents object contains pointers
                                        // to the actual byte stream
                                        DriveContents contents = driveContentsResult.getDriveContents();

                                        deletePOIContents(contents.getInputStream(), managedPoi);

                                        //TODO: Update the file contents
                                        isCompleted = true;

                                    }
                                });
                    }
                }
            });
            while (!isCompleted) {/*wait for complete*/}
            return innerPOIList;
        }

        private void deletePOIContents(InputStream inputStream, POI managedPoi) {
            String strToDelete = "  <Placemark>\n" +
                    "        <name>" + managedPoi.getName() + "</name>\n" +
                    "        <description>" + managedPoi.getDescription() + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + managedPoi.getPoint().getLongitude() + "," + managedPoi.getPoint().getLatitude() + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>";

            String xmlString = StringUtils.getStringFromInputStream(inputStream);

            String newStr = xmlString.replaceAll(strToDelete, "");
        }


        @Override
        protected void onPostExecute(List<POI> output) {
            super.onPostExecute(output);
            if (output != null)
                //fillAdapter(output);
                if (dialog != null && dialog.isShowing())
                    dialog.hide();
            //refreshLayout.setRefreshing(false);
        }

        @Override
        protected void onCancelled() {

            if (mLastError != null) {
                if (mLastError instanceof GooglePlayServicesAvailabilityIOException) {
                    GooglePlayUtils.showGooglePlayServicesAvailabilityErrorDialog(activity,
                            ((GooglePlayServicesAvailabilityIOException) mLastError)
                                    .getConnectionStatusCode());
                } else if (mLastError instanceof UserRecoverableAuthIOException) {
                    activity.startActivityForResult(
                            ((UserRecoverableAuthIOException) mLastError).getIntent(),
                            Constants.REQUEST_AUTHORIZATION);
                } else {
                   /* AndroidUtils.showMessage(("The following error occurred:\n"
                            + mLastError.getMessage()), getActivity());*/
                }
            } else {
                AndroidUtils.showMessage("Request cancelled.", activity);
            }
        }
    }


}
