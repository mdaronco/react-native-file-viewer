package com.vinzscam.reactnativefileviewer;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;
import androidx.core.content.FileProvider;
import android.webkit.MimeTypeMap;

import com.facebook.react.bridge.ActivityEventListener;
import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.BaseActivityEventListener;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;

import com.facebook.react.modules.core.DeviceEventManagerModule;
import java.io.File;

public class RNFileViewerModule extends ReactContextBaseJavaModule {
    private final ReactApplicationContext reactContext;
    private static final String SHOW_OPEN_WITH_DIALOG = "showOpenWithDialog" ;
    private static final String SHOW_STORE_SUGGESTIONS ="showAppsSuggestions";
    private static final String OPEN_EVENT = "RNFileViewerDidOpen";
    private static final String DISMISS_EVENT = "RNFileViewerDidDismiss";
    private static final Integer RN_FILE_VIEWER_REQUEST = 33341;

    private final ActivityEventListener mActivityEventListener = new BaseActivityEventListener() {
        @Override
        public void onActivityResult(final Activity activity, final int requestCode, final int resultCode, final Intent intent) {
            sendEvent(DISMISS_EVENT, requestCode - RN_FILE_VIEWER_REQUEST, null);
        }
    };

    public RNFileViewerModule(ReactApplicationContext reactContext) {
        super(reactContext);
        this.reactContext = reactContext;
        reactContext.addActivityEventListener(mActivityEventListener);
    }

    private String getMimeType(Uri uri) {
        String mimeType;
        if (uri.getScheme().equals(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver cr = reactContext.getContentResolver();
            mimeType = cr.getType(uri);
        } else {
            String fileExtension = MimeTypeMap.getFileExtensionFromUrl(uri
                    .toString());
            mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                    fileExtension.toLowerCase());
        }
        return mimeType;
    }

    @ReactMethod
    public void open(String path, ReadableMap options) {
        Uri contentUri = null;
        Boolean showOpenWithDialog = options.hasKey(SHOW_OPEN_WITH_DIALOG) ? options.getBoolean(SHOW_OPEN_WITH_DIALOG) : false;
        Boolean showStoreSuggestions = options.hasKey(SHOW_STORE_SUGGESTIONS) ? options.getBoolean(SHOW_STORE_SUGGESTIONS) : false;

        Log.i("JAVA >>>", "open: path:" + path);
        // Log.i("JAVA >>>", "open: currentId:" + RN_FILE_VIEWER_REQUEST);

        if(path.startsWith("content://")) {
            contentUri = Uri.parse(path);
        } else {
            File newFile = new File(path);
            try {
                final String packageName = getCurrentActivity().getPackageName();
                final String authority = new StringBuilder(packageName).append(".provider").toString();
                contentUri = FileProvider.getUriForFile(getCurrentActivity(), authority, newFile);
            }
            catch(IllegalArgumentException e) {
                sendEvent(OPEN_EVENT, RN_FILE_VIEWER_REQUEST, e.getMessage());
                return;
            }
        }

        if(contentUri == null) {
            Log.i("JAVA >>>", "contentUri is NULL");

            sendEvent(OPEN_EVENT, RN_FILE_VIEWER_REQUEST, "Invalid file");
            return;
        }

        // String extension = MimeTypeMap.getFileExtensionFromUrl(path).toLowerCase();
        String mimeType = getMimeType(contentUri); // MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);

        Log.i("JAVA >>>", "mimeType: " + mimeType);
        // Log.i("JAVA >>>", "extension: " + extension);

        Intent shareIntent = new Intent();

        shareIntent.setAction(Intent.ACTION_VIEW);
        shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        shareIntent.setDataAndType(contentUri, mimeType);
        shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
        Intent intentActivity;

        if (showOpenWithDialog) {
            intentActivity = Intent.createChooser(shareIntent, "Open with");
        } else {
            intentActivity = shareIntent;
        }

        PackageManager pm = getCurrentActivity().getPackageManager();

        if (shareIntent.resolveActivity(pm) != null) {
            try {
                getCurrentActivity().startActivityForResult(intentActivity, RN_FILE_VIEWER_REQUEST);
                sendEvent(OPEN_EVENT, RN_FILE_VIEWER_REQUEST, null);
            }
            catch(Exception e) {
                sendEvent(OPEN_EVENT, RN_FILE_VIEWER_REQUEST, e.getMessage());
            }
        } else {
            try {
                if (showStoreSuggestions) {
                    if(mimeType == null) {
                        throw new Exception("It wasn't possible to detect the type of the file");
                    }
                    Intent storeIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + mimeType + "&c=apps"));
                    getCurrentActivity().startActivity(storeIntent);
                }
                throw new Exception("No app associated with this mime type");
            }
            catch(Exception e) {
                sendEvent(OPEN_EVENT, RN_FILE_VIEWER_REQUEST, e.getMessage());
            }
        }
    }

    @Override
    public String getName() {
        return "RNFileViewer";
    }

    private void sendEvent(String eventName, Integer currentId, String errorMessage) {
        WritableMap params = Arguments.createMap();
        params.putInt("id", currentId);
        if(errorMessage != null) {
            params.putString("error", errorMessage);
        }
        reactContext.getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }
}
