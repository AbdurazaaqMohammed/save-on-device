package name.lmj001.saveondevice;

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.view.View;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Objects;

/** @noinspection deprecation*/
public class MainActivity extends Activity {
    private static Uri inputUri;
    private static ArrayList<Uri> inputUris;
    private static String sharedText;
    private static boolean saveIndividually;
    private final static boolean supportsBuiltInAndroidFilePicker = Build.VERSION.SDK_INT>18;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        SharedPreferences settings = getSharedPreferences("set", Context.MODE_PRIVATE);
        saveIndividually = settings.getBoolean("saveIndividually", true);

        Intent intent = getIntent();
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            inputUri = intent.getData();
            callSaveFileResultLauncherForIndividual();
        } else if (Intent.ACTION_SEND.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                inputUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
                callSaveFileResultLauncherForIndividual();
            } else if (intent.hasExtra(Intent.EXTRA_TEXT)) {
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    String fileName = sharedText.substring(0, Math.min(sharedText.length(), 20));
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) {
                        fileName = Normalizer.normalize(fileName, Normalizer.Form.NFD)
                                .replaceAll("[^\\p{ASCII}]", "")
                                .replaceAll("[^a-zA-Z0-9\\s]+", "")
                                .trim()
                                .replaceAll("\\s+", "-")
                                .toLowerCase();
                    } else {
                        StringBuilder sb = new StringBuilder();
                        for (char c : fileName.toCharArray()) {
                            if ((int) c <= 127) {
                                sb.append(c);
                            }
                        }
                        fileName = sb.toString().replaceAll("[^a-zA-Z0-9\\s]", "")
                                .trim()
                                .replaceAll("\\s+", "-")
                                .toLowerCase();
                    }
                    if(supportsBuiltInAndroidFilePicker) {
                        Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                        saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
                        saveFileIntent.setType("text/plain");
                        saveFileIntent.putExtra(Intent.EXTRA_TITLE, fileName);
                        startActivityForResult(saveFileIntent, 0);
                    } else {
                        saveFile(Uri.fromFile(new File(getSharedPreferences("set", Context.MODE_PRIVATE).getString("directoryToSaveFiles", Environment.getExternalStorageDirectory().getPath() + File.separator + "Download") + File.separator + fileName)));
                    }
                } else {
                    Toast.makeText(getApplicationContext(), R.string.nothing, Toast.LENGTH_LONG).show();
                    finish();
                }
            }
        } else if (Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            if (intent.hasExtra(Intent.EXTRA_STREAM)) {
                inputUris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
                if (saveIndividually || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                    inputUri = inputUris.get(0);
                    inputUris.remove(0);
                    callSaveFileResultLauncherForIndividual();
                } else {
                    // I don't know a way to fix the MIME type for this.
                    for (Uri uri : inputUris) {
                        final String mimeType = getApplicationContext().getContentResolver().getType(uri);
                        if (mimeType==null || mimeType.isEmpty()) {
                            Toast.makeText(getApplicationContext(), R.string.unsupported_mimetype, Toast.LENGTH_LONG).show();
                            finish();
                        }
                    }
                    Intent saveFilesIntent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                    startActivityForResult(saveFilesIntent, 1);
                }
            }
        } else {
            setContentView(R.layout.activity_main);
            ToggleButton tb = findViewById(R.id.multiSaveSwitch);
            if(Build.VERSION.SDK_INT<21) {
                if(supportsBuiltInAndroidFilePicker) {
                    tb.setChecked(true);
                    tb.setEnabled(false);
                    findViewById(R.id.oldAndroidInfo).setVisibility(View.VISIBLE);
                } else {
                    findViewById(R.id.veryOldAndroidInfo).setVisibility(View.VISIBLE);
                    tb.setVisibility(View.INVISIBLE);
                    findViewById(R.id.multiSaveInfo).setVisibility(View.INVISIBLE);
                    findViewById(R.id.multiSaveLabel).setVisibility(View.INVISIBLE);
                    EditText outputDirectoryField = findViewById(R.id.directorySaveFiles);
                    outputDirectoryField.setVisibility(View.VISIBLE);
                    outputDirectoryField.setText(settings.getString("directoryToSaveFiles", Environment.getExternalStorageDirectory().getPath() + File.separator + "Download"));
                    Button b = findViewById(R.id.saveDirectorySetting);
                    b.setVisibility(View.VISIBLE);
                    b.setOnClickListener(v -> {
                        final SharedPreferences.Editor editor = settings.edit();
                        final String userFilePath = outputDirectoryField.getText().toString();
                        final File newFile = new File(userFilePath);
                        if (newFile.exists() || newFile.mkdir()) {
                            editor.putString("directoryToSaveFiles", userFilePath);
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD) editor.apply(); else editor.commit();
                        } else showError(getString(R.string.invalid_filepath));
                    });
                }
            } else {
                tb.setChecked(saveIndividually);
                tb.setOnCheckedChangeListener((buttonView, isChecked) -> settings.edit().putBoolean("saveIndividually", isChecked).apply());
            }
        }
    }

    private void showError(String err) {
        AlertDialog.Builder builder = (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) ?
                new AlertDialog.Builder(this, android.R.style.Theme_Black) : new AlertDialog.Builder(this);
        builder.setTitle(R.string.err);
        builder.setMessage(err);
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());

        runOnUiThread(() -> {
            Toast.makeText(this, err, Toast.LENGTH_SHORT).show();
            builder.create().show();
        });
    }
    @TargetApi(19)
    private void callSaveFileResultLauncherForIndividual() {
        String fileName = getOriginalFileName(this, inputUri);
        if (supportsBuiltInAndroidFilePicker) {
            String mimeType = getApplicationContext().getContentResolver().getType(inputUri);
            // It seems like some apps send the file with no MIME type on older Android versions. Possibly on newer versions the MIME type gets automatically set
            // ContentResolver will refuse to open an OutputStream for anything with invalid MIME type and the output file will be 0 bytes unless you grant WRITE_EXTERNAL_STORAGE.
            // I am not sure if Google Play will allowing uploading the app with it but I guess I can put this if lmj isn't uploading it on Play Store anyway

            if (mimeType==null || mimeType.isEmpty()) {
                if(Build.VERSION.SDK_INT>22 && Build.VERSION.SDK_INT<29 && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                    showError(getString(R.string.need_storage));
                    requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
                    if(checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                        // Backup: Copy the file to cache directory in case the user did not grant WRITE_EXTERNAL_STORAGE.
                        // If uploading on Play Store this should probably be default behaviour and maxSdkVersion for WRITE_EXTERNAL_STORAGE should be 18.
                        final File tempFile = new File(getExternalCacheDir() + File.separator + fileName);
                        Uri outputUri = Uri.fromFile(tempFile);
                        new SaveFileTask(this).execute(outputUri);
                        inputUri = outputUri; // lol
                    }
                }
                String fileExtension = fileName.substring(fileName.lastIndexOf('.') + 1).trim();
                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension.toLowerCase());
                if (mimeType==null || mimeType.isEmpty()) mimeType = "application/octet-stream"; // Generic MIME type
            }

            Intent saveFileIntent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
            saveFileIntent.addCategory(Intent.CATEGORY_OPENABLE);
            saveFileIntent.setType(mimeType);
            saveFileIntent.putExtra(Intent.EXTRA_TITLE, fileName);
            startActivityForResult(saveFileIntent, 0);
        } else {
            Uri outputUri = Uri.fromFile(new File(getSharedPreferences("set", Context.MODE_PRIVATE).getString("directoryToSaveFiles", Environment.getExternalStorageDirectory().getPath() + File.separator + "Download") + File.separator + fileName));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.CUPCAKE) {
                new SaveFileTask(this).execute(outputUri);
            } else {
                saveFile(outputUri);
            }
        }
    }

    private void saveFile(Uri outputUri) {
        try (OutputStream outputStream = getContentResolver().openOutputStream(outputUri)) {
            if (inputUri == null) {
                // If the input URI is null, write the text from the intent directly to the output stream
                outputStream.write(sharedText.getBytes());
            } else {
                try (InputStream inputStream = getContentResolver().openInputStream(inputUri)) {
                    if (inputStream != null) {
                        byte[] buffer = new byte[4096];
                        int length;
                        while ((length = inputStream.read(buffer)) > 0) {
                            outputStream.write(buffer, 0, length);
                        }
                    }
                }
            }
        } catch (Exception e) {
            showError(e.toString());
        }
    }

    private static String getOriginalFileName(Context context, Uri uri) {
        String result = null;
        try {
            if (Objects.equals(uri.getScheme(), "content")) {
                try (Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) {
                        result = cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME));
                    }
                }
            }
            if (result == null) {
                result = uri.getPath();
                int cut = Objects.requireNonNull(result).lastIndexOf('/'); // Ensure it throw the NullPointerException here to be caught
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        } catch (NullPointerException | IllegalArgumentException ignored) {
            result = "filename_not_found";
        }
        return result;
    }

    @TargetApi(19)
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        new SaveFileTask(this).execute(data.getData());
        Toast.makeText(this, R.string.success, Toast.LENGTH_SHORT).show();
        finish();
    }

    @TargetApi(Build.VERSION_CODES.CUPCAKE)
    private static class SaveFileTask extends AsyncTask<Uri, Void, Void> {
        private static WeakReference<MainActivity> activityReference;

        public SaveFileTask(MainActivity context) {
            activityReference = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Uri... uris) {
            MainActivity activity = activityReference.get();

            if (saveIndividually || Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
                activity.saveFile(uris[0]);
                if (inputUris != null && !inputUris.isEmpty()) {
                    inputUri = inputUris.get(0);
                    inputUris.remove(0);
                    activity.callSaveFileResultLauncherForIndividual();
                }
            } else {
                final Uri treeUri = uris[0];
                ContentResolver resolver = activity.getContentResolver();
                try {
                    Uri docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri));
                    for (Uri inputUri : inputUris) {
                        Uri fileUri = DocumentsContract.createDocument(resolver, docUri, "*/*", getOriginalFileName(activity, inputUri));
                        try (InputStream inputStream = resolver.openInputStream(inputUri);
                             OutputStream outputStream = resolver.openOutputStream(fileUri)) {
                            byte[] buffer = new byte[4096];
                            int length;
                            while ((length = inputStream.read(buffer)) > 0) {
                                outputStream.write(buffer, 0, length);
                            }
                        }
                    }
                } catch (Exception e) {
                    activity.showError(e.toString());
                }
            }
            return null;
        }
    }
}
