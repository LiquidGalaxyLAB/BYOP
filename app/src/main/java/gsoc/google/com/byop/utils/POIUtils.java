package gsoc.google.com.byop.utils;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;

import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.drive.Drive;
import com.google.android.gms.drive.DriveApi;
import com.google.android.gms.drive.DriveContents;
import com.google.android.gms.drive.DriveFile;
import com.google.android.gms.drive.MetadataChangeSet;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential;
import com.google.api.client.googleapis.extensions.android.gms.auth.GooglePlayServicesAvailabilityIOException;
import com.google.api.client.googleapis.extensions.android.gms.auth.UserRecoverableAuthIOException;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.drive.model.File;

import org.xmlpull.v1.XmlPullParserException;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import gsoc.google.com.byop.R;
import gsoc.google.com.byop.model.DriveDocument;
import gsoc.google.com.byop.model.POI;
import gsoc.google.com.byop.model.Point;
import gsoc.google.com.byop.ui.poisList.POISListFragment;

/**
 * Created by lgwork on 27/05/16.
 */
public class POIUtils {

    private FragmentActivity activity;
    private DriveDocument driveDoc;
    private GoogleApiClient googleApiClient;

    private POI managedPoi;

    private EditPOITask editPoiTask;
    private DeletePOITask deletePoitask;
    private POISListFragment poisFragment;

    /**
     * Needed for  DRIVE REST API V3
     */
    private GoogleAccountCredential credential;

    public POIUtils(String poiName, String poiDesc, String poiLat, String poiLon, DriveDocument document, GoogleApiClient mGoogleApiClient, GoogleAccountCredential mCredential, FragmentActivity activity, POISListFragment poisFragment) {
        this.activity = activity;
        this.driveDoc = document;
        this.googleApiClient = mGoogleApiClient;

        Point mPoint = new Point(poiLat, poiLon);

        POI mPoi = new POI(poiName, poiDesc, mPoint);

        this.managedPoi = mPoi;
        this.credential = mCredential;
        this.poisFragment = poisFragment;
    }

    public FragmentActivity getActivity() {
        return activity;
    }

    public void setActivity(FragmentActivity activity) {
        this.activity = activity;
    }

    public void deletePOI() {
        deletePoitask = new DeletePOITask(credential, driveDoc);
        deletePoitask.execute();
    }

    private class EditPOITask extends AsyncTask<Void, Void, List<POI>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;
        private String folderId = "";
        private ProgressDialog dialog;
        private POI editedPoi = null;

        private List<POI> innerPOIList = new ArrayList<POI>();
        private boolean isCompleted = false;

        public EditPOITask(GoogleAccountCredential credential, DriveDocument document, POI meditedPoi) {
            HttpTransport transport = AndroidHttp.newCompatibleTransport();
            JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();
            mService = new com.google.api.services.drive.Drive.Builder(
                    transport, jsonFactory, credential)
                    .setApplicationName(poisFragment.getActivity().getResources().getString(R.string.app_name))
                    .build();

            editedPoi = meditedPoi;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (dialog == null) {
                dialog = new ProgressDialog(poisFragment.getActivity());
                dialog.setMessage(poisFragment.getActivity().getResources().getString(R.string.loading));
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
                        file[0].open(googleApiClient, DriveFile.MODE_READ_WRITE, null)
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
                                        ParcelFileDescriptor parcelFileDescriptor = contents.getParcelFileDescriptor();
                                        FileInputStream fileInputStream = new FileInputStream(parcelFileDescriptor
                                                .getFileDescriptor());

                                        String newContents = editPOIContents(fileInputStream, managedPoi, editedPoi);

                                        //Update the file contents
                                        FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());

                                        try {
                                            fileOutputStream.getChannel().position(0);
                                            Writer writer = new OutputStreamWriter(fileOutputStream);
                                            writer.write(newContents);
                                            writer.close();

                                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setMimeType("text/xml").build();

                                            contents.commit(googleApiClient, changeSet).setResultCallback(new ResultCallback<com.google.android.gms.common.api.Status>() {
                                                @Override
                                                public void onResult(com.google.android.gms.common.api.Status result) {
                                                    //Do nothing
                                                }
                                            });
                                            isCompleted = true;
                                            innerPOIList = reloadPOIS(fileInputStream);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } catch (XmlPullParserException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                    }
                }
            });
            while (!isCompleted) {/*wait for complete*/}

            return innerPOIList;
        }


        private String editPOIContents(FileInputStream fileInputStream, POI managedPoi, POI editedPoi) {
            String originalPOIStr = "      <Placemark>\n" +
                    "        <name>" + managedPoi.getName() + "</name>\n" +
                    "        <description>" + managedPoi.getDescription() + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + managedPoi.getPoint().getLongitude() + "," + managedPoi.getPoint().getLatitude() + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>";

            String xmlString = StringUtils.getStringFromInputStream(fileInputStream);

            String editedPOIStr = "      <Placemark>\n" +
                    "        <name>" + editedPoi.getName() + "</name>\n" +
                    "        <description>" + editedPoi.getDescription() + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + editedPoi.getPoint().getLongitude() + "," + editedPoi.getPoint().getLatitude() + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>";

            return xmlString.trim().replaceAll(originalPOIStr.trim(), editedPOIStr.trim());
        }


        @Override
        protected void onPostExecute(List<POI> output) {
            super.onPostExecute(output);
            if (output != null)
                poisFragment.fillAdapter(output);
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
                AndroidUtils.showMessage(getActivity().getResources().getString(R.string.request_cancelled), activity);
            }
        }

        private List<POI> reloadPOIS(InputStream inputStream) throws IOException, XmlPullParserException {
            BYOPXmlPullParser parser = new BYOPXmlPullParser();
            List<POI> poiList = parser.parse(inputStream);

            return poiList;
        }

    }


    /**
     * An asynchronous task that handles the Drive API call.
     * Placing the API calls in their own task ensures the UI stays responsive.
     */
    private class DeletePOITask extends AsyncTask<Void, Void, List<POI>> {
        private com.google.api.services.drive.Drive mService = null;
        private Exception mLastError = null;

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
                dialog = new ProgressDialog(activity);
                dialog.setMessage(activity.getResources().getString(R.string.loading));
                dialog.setIndeterminate(false);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setCancelable(true);
                dialog.setCanceledOnTouchOutside(false);
                dialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        deletePoitask.cancel(true);
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
                        file[0].open(googleApiClient, DriveFile.MODE_READ_WRITE, null)
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
                                        ParcelFileDescriptor parcelFileDescriptor = contents.getParcelFileDescriptor();
                                        FileInputStream fileInputStream = new FileInputStream(parcelFileDescriptor
                                                .getFileDescriptor());

                                        String newContents = deletePOIContents(fileInputStream, managedPoi);

                                        //Update the file contents
                                        FileOutputStream fileOutputStream = new FileOutputStream(parcelFileDescriptor.getFileDescriptor());

                                        try {
                                            fileOutputStream.getChannel().position(0);
                                            Writer writer = new OutputStreamWriter(fileOutputStream);
                                            writer.write(newContents);
                                            writer.close();

                                            MetadataChangeSet changeSet = new MetadataChangeSet.Builder().setMimeType("text/xml").build();

                                            contents.commit(googleApiClient, changeSet).setResultCallback(new ResultCallback<com.google.android.gms.common.api.Status>() {
                                                @Override
                                                public void onResult(com.google.android.gms.common.api.Status result) {
                                                    //Do nothing
                                                }
                                            });
                                            isCompleted = true;
                                            innerPOIList = reloadPOIS(fileInputStream);
                                        } catch (IOException e) {
                                            e.printStackTrace();
                                        } catch (XmlPullParserException e) {
                                            e.printStackTrace();
                                        }
                                    }
                                });
                    }
                }
            });
            while (!isCompleted) {/*wait for complete*/}

            return innerPOIList;
        }

        private String deletePOIContents(FileInputStream fileInputStream, POI managedPoi) {
            String strToDelete = "      <Placemark>\n" +
                    "        <name>" + managedPoi.getName() + "</name>\n" +
                    "        <description>" + managedPoi.getDescription() + "</description>\n" +
                    "        <Point>\n" +
                    "          <coordinates>" + managedPoi.getPoint().getLongitude() + "," + managedPoi.getPoint().getLatitude() + ",0</coordinates>\n" +
                    "        </Point>\n" +
                    "      </Placemark>";

            String xmlString = StringUtils.getStringFromInputStream(fileInputStream);

            String newStr = xmlString.trim().replaceAll(strToDelete.trim() + "\n", "");

            return newStr;
        }


        @Override
        protected void onPostExecute(List<POI> output) {
            super.onPostExecute(output);
            if (output != null)
                poisFragment.fillAdapter(output);
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
                    AndroidUtils.showMessage((getActivity().getResources().getString(R.string.following_error) + ":\n"
                            + mLastError.getMessage()), getActivity());
                }
            } else {
                AndroidUtils.showMessage(getActivity().getResources().getString(R.string.request_cancelled), activity);
            }
        }

        private List<POI> reloadPOIS(InputStream inputStream) throws IOException, XmlPullParserException {
            BYOPXmlPullParser parser = new BYOPXmlPullParser();
            List<POI> poiList = parser.parse(inputStream);

            return poiList;
        }

    }


}
