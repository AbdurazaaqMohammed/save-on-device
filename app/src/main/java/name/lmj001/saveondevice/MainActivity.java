package name.lmj001.saveondevice;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import android.os.Build;

/*import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
*/
import java.io.FileOutputStream;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private static final int SAVE_FILE_REQUEST_CODE = 1;
    private static final int SAVE_FILES_REQUEST_CODE = 2;
    private Uri inputUri;
    private ArrayList<Uri> inputUris;
    private int currentFileIndex = 0;
    private boolean saveIndividually;

    private final static boolean isVeryOldAndroid = Build.VERSION.SDK_INT<19;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);

        saveIndividually = settings.getBoolean("saveIndividually", true);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_SEND.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                inputUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILE_REQUEST_CODE);
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                String text = intent.getStringExtra(Intent.EXTRA_TEXT);
                callSaveFileResultLauncherForPlainTextData(text);
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                inputUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (saveIndividually) {
                    inputUri = inputUris.get(currentFileIndex);
                    callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILES_REQUEST_CODE);
                }
                else callSaveFilesResultLauncherForMultipleUriDataAllAtOnce();
            }
        } else {
            setContentView(R.layout.activity_main);
            Switch mySwitch = findViewById(R.id.multiSaveSwitch);
            if(Build.VERSION.SDK_INT<21) {
                mySwitch.setChecked(true);
                mySwitch.setEnabled(false);
                findViewById(R.id.oldAndroidInfo).setVisibility(View.VISIBLE);
                if(isVeryOldAndroid) {
                    findViewById(R.id.veryOldAndroidInfo).setVisibility(View.VISIBLE);
                    EditText outputDirectoryField = findViewById(R.id.directorySaveFiles);
                    outputDirectoryField.setVisibility(View.VISIBLE);
                    outputDirectoryField.setText(settings.getString("directoryToSaveFiles", "/sdcard/Download"));
                    Button b = findViewById(R.id.saveDirectorySetting);
                    b.setVisibility(View.VISIBLE);
                    b.setOnClickListener(v -> settings.edit().putString("directoryToSaveFiles", outputDirectoryField.getText().toString()).apply());
                }
            } else {
                mySwitch.setChecked(saveIndividually);
                mySwitch.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("saveIndividually", isChecked).apply());
            }
            if(Build.VERSION.SDK_INT>22 && Build.VERSION.SDK_INT<29) requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    private void callSaveFilesResultLauncherForMultipleUriDataAllAtOnce() {
        // I don't know a way to fix the MIME type for this.
        if (isSupportedMimeTypes(inputUris)) {
            Intent saveFilesIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            startActivityForResult(saveFilesIntent, SAVE_FILES_REQUEST_CODE);
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.unsupported_mimetype), Toast.LENGTH_LONG).show();
            finish();
        }
    }

    private void showError(String error) {
        TextView errorBox = findViewById(R.id.errorField);
        errorBox.setVisibility(View.VISIBLE);
        errorBox.setText(error);
        Toast.makeText(this, error, Toast.LENGTH_SHORT).show();
    }

    private String getMimeType(Uri uri) {
        String mimeType = getApplicationContext().getContentResolver().getType(uri);

        if (mimeType == null || mimeType.isEmpty()) {
            String fileExtension = getOriginalFileName(this, uri);
            fileExtension = fileExtension.substring(fileExtension.lastIndexOf('.') + 1).trim();
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
            if (mimeType == null || mimeType.isEmpty()) mimeType = "application/octet-stream";
        }
        //If you use anything besides ContentResolver.getType to set the MIME type Android apparently no longer recognizes this as a file coming from the app or whatever and the file will be 0 bytes unless you grant WRITE_EXTERNAL_STORAGE. I am not sure if Google Play will allowing uploading the app with it.
        // I guess I can put this if lmj isn't uploading it on Play Store anyway
        return mimeType;
    }

    private boolean isSupportedMimeTypes(ArrayList<Uri> uris) {
        for (Uri uri : uris) {
            String mimeType = getApplicationContext().getContentResolver().getType(uri);
            if (mimeType == null || mimeType.isEmpty()) return false;
        }
        return true;
    }
    
    private void callSaveFileResultLauncherForIndividual(Uri inputUri, int code) {
        if(isVeryOldAndroid) {
            Uri outputUri = Uri.parse(getSharedPreferences("set", Context.MODE_PRIVATE).getString("directoryToSaveFiles", "/sdcard/Download") + getOriginalFileName(this, inputUri));
            new SaveFileAsyncTask(this).execute(inputUri, outputUri);
        } else {
            final String mimeType = getMimeType(inputUri);

            if (mimeType.isEmpty()) {
                Toast.makeText(getApplicationContext(), getString(R.string.unsupported_mimetype), Toast.LENGTH_LONG).show();
                finish();
            } else {
                Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                saveFileIntent.setType(mimeType);
                saveFileIntent.putExtra(Intent.EXTRA_TITLE, getOriginalFileName(this, inputUri));
                startActivityForResult(saveFileIntent, code);
            }
        }
    }
    private void callSaveFileResultLauncherForPlainTextData(String text) {
        if (text != null) {
            String fileName = slugify(text.substring(0, Math.min(text.length(), 20))) + ".txt";
            if(isVeryOldAndroid) {
                try {
                    OutputStream outputStream = new FileOutputStream(getSharedPreferences("set", Context.MODE_PRIVATE).getString("directoryToSaveFiles", "/sdcard/Download") + fileName);
                    outputStream.write(text.getBytes());
                    outputStream.flush();
                    outputStream.close();
                    finish();
                } catch (IOException e) {
                    showError(e.toString());
                }
            }
            Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            saveFileIntent.setType("text/plain");
            saveFileIntent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(saveFileIntent, SAVE_FILE_REQUEST_CODE);
        } else {
            Toast.makeText(getApplicationContext(), getString(R.string.unsupported_mimetype), Toast.LENGTH_LONG).show();
            finish();
        }
    }


    private String getOriginalFileName(Context context, Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    result = cursor.getString(cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME));
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private String slugify(String word) {
        return Normalizer.normalize(word, Normalizer.Form.NFD)
                .replaceAll("[^\\p{ASCII}]", "")
                .replaceAll("[^a-zA-Z0-9\\s]+", "").trim()
                .replaceAll("\\s+", "-")
                .toLowerCase();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode == RESULT_OK && data != null) {
            Uri outputUri = data.getData();
            if (outputUri != null) {
                switch (requestCode) {
                    case SAVE_FILE_REQUEST_CODE:
                        new SaveFileAsyncTask(this).execute(inputUri, outputUri);
                        finish();
                    break;
                    case SAVE_FILES_REQUEST_CODE:
                        if(saveIndividually) {
                            new SaveFileAsyncTask(this).execute(inputUri, outputUri);
                            // If there are more input URIs to process, save the next file; otherwise, finish the activity
                            if (inputUris != null && currentFileIndex < inputUris.size() - 1) {
                                currentFileIndex++;
                                inputUri = inputUris.get(currentFileIndex);
                                callSaveFileResultLauncherForIndividual(inputUri, SAVE_FILES_REQUEST_CODE);
                            } else {
                                finish();
                            }
                        } else {
                            new SaveMultipleFilesAsyncTask(this).execute(outputUri);
                            finish();
                        }
                    break;
                }
            }
        }
    }

    private static class SaveMultipleFilesAsyncTask extends AsyncTask<Uri, Void, Void> {
        private final WeakReference<MainActivity> activityReference;
        // only retain a weak reference to the activity

        public SaveMultipleFilesAsyncTask(MainActivity context) {
            this.activityReference = new WeakReference<>(context);
        }

        @TargetApi(Build.VERSION_CODES.LOLLIPOP)
        @Override
        protected Void doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();
            final Uri treeUri = uris[0];
            if (treeUri != null) {
                ContentResolver resolver = activity.getContentResolver();
                try {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                    for (Uri inputUri : activity.inputUris) {
                        String fromUriFileName = activity.getOriginalFileName(activity, inputUri);
                        InputStream inputStream = resolver.openInputStream(inputUri);
                        if (inputStream != null) {
                            Uri fileUri = DocumentsContract.createDocument(resolver, docUri, "*/*", fromUriFileName);
                            assert fileUri != null;
                            FileOutputStream outputStream = (FileOutputStream) resolver.openOutputStream(fileUri);
                            if (outputStream != null) {
                                byte[] buffer = new byte[4096];
                                int length;
                                while ((length = inputStream.read(buffer)) > 0) {
                                    outputStream.write(buffer, 0, length);
                                }
                            }
                            outputStream.close();
                        }
                        inputStream.close();
                    }
                } catch (Exception e) {
                    activity.showError(e.toString());
                }
            }
            return null;
        }
    }

    private static class SaveFileAsyncTask extends AsyncTask<Uri, Void, Void> {
        private final WeakReference<MainActivity> activityReference;
        // only retain a weak reference to the activity

        public SaveFileAsyncTask(MainActivity context) {
            this.activityReference = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();
            InputStream inputStream = null;
            OutputStream outputStream = null;
            try {
                ParcelFileDescriptor pfd = activity.getContentResolver().openFileDescriptor(uris[1], "w");
                if (pfd != null) {
                    outputStream = new FileOutputStream(pfd.getFileDescriptor());
                    if (uris[0] != null) {
                        inputStream = activity.getContentResolver().openInputStream(uris[0]);
                        if (inputStream != null) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                        }
                    } else {
                        // If the input URI is null, write the text from the intent directly to the output stream
                        String text = activity.getIntent().getStringExtra(Intent.EXTRA_TEXT);
                        if (text != null) {
                            outputStream.write(text.getBytes());
                        }
                    }
                    outputStream.flush();
                    pfd.close();
                }
            } catch (Exception e) {
                activity.showError(e.toString());
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                    if (outputStream != null) {
                        outputStream.close();
                    }
                } catch (Exception e) {
                    activity.showError(e.toString());
                }
            }
            //deleteDir(activity.getExternalCacheDir());
            return null;
        }
    }
/*
Another way to avoid the MIME type problems at the cost of efficiency - copy it to the cache directory then you can set whatever MIME type we want
    private static class CopyTask extends AsyncTask<Uri, Void, String> {
        private WeakReference<MainActivity> activityReference;

        // only retain a weak reference to the activity
        CopyTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }
        @Override
        protected String doInBackground(Uri...uris) {
            MainActivity activity = activityReference.get();

            // This will avoid having to set the correct URI
            File fileInCacheDir = new File(activity.getExternalCacheDir() + File.separator + activity.getOriginalFileName(activity, uris[0]));
            InputStream inputStream = null;
            try {
                inputStream = new FileInputStream(fileInCacheDir);

                OutputStream outputStream = activity.getContentResolver().openOutputStream(uris[0]);

                byte[] buffer = new byte[1024];
                int length;
                while ((length = inputStream.read(buffer)) > 0) {
                    outputStream.write(buffer, 0, length);
                }

                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                activity.showError(e.toString());
            }
            return "";
        }

        @Override
        protected void onPostExecute(String result) {
            MainActivity activity = activityReference.get();
            Toast.makeText(activity.getApplicationContext(), result, Toast.LENGTH_SHORT).show();
        }
    }
    public static void deleteDir(File dir){
        if(!dir.exists()){
            return;
        }
        if(dir.isFile()){
            dir.delete();
            return;
        }
        if(!dir.isDirectory()){
            return;
        }
        File[] files=dir.listFiles();
        if(files==null){
            deleteEmptyDirectories(dir);
            return;
        }
        for(File file:files){
            deleteDir(file);
        }
        deleteEmptyDirectories(dir);
    }
    public static void deleteEmptyDirectories(File dir){
        if(dir==null || !dir.isDirectory()){
            return;
        }
        File[] filesList = dir.listFiles();
        if(filesList == null || filesList.length == 0){
            dir.delete();
            return;
        }
        int count = filesList.length;
        for(int i = 0; i < count; i++){
            File file = filesList[i];
            if(file.isFile() && file.length() != 0){
                return;
            }
        }
        count = filesList.length;
        for(int i = 0; i < count; i++){
            File file = filesList[i];
            if(file.isDirectory()){
                deleteEmptyDirectories(file);
            }
        }
        filesList = dir.listFiles();
        if(filesList == null || filesList.length == 0){
            dir.delete();
        }
    }
 */
}
