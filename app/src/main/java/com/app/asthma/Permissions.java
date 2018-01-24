package com.app.asthma;

/**
 * Created by siddartha on 11/7/16.
 */

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.v4.app.ActivityCompat;

public class Permissions {

    public static final int PERMISSION_STORAGE_RESULT = 2;
    public static final int PERMISSION_LOCATION_RESULT = 3;
    public static final int PERMISSION_ALL_RESULT = 4;
    public static final String PERMISSION_STORAGE_WRITE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    @SuppressLint("InlinedApi")
    public static final String PERMISSION_STORAGE_READ = Manifest.permission.READ_EXTERNAL_STORAGE;
    public static final String PERMISSION_LOCATION = Manifest.permission.ACCESS_FINE_LOCATION;

    @TargetApi(23)
    public static boolean selfPermissionGranted(Context context, String permission) {
        // For Android < Android M, self permissions are always granted.
        boolean result = true;
        int targetSdkVersion = 22;
        try {
            final PackageInfo info = context.getPackageManager().getPackageInfo(
                    context.getPackageName(), 0);
            targetSdkVersion = info.applicationInfo.targetSdkVersion;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            if (targetSdkVersion >= Build.VERSION_CODES.M) {
                // targetSdkVersion >= Android M, we can
                // use Context#checkSelfPermission
                result = context.checkSelfPermission(permission)
                        == PackageManager.PERMISSION_GRANTED;
            }
        }

        return result;
    }

    @TargetApi(23)
    public static void requestPermissionsForM(Activity activity, String[] permissionList, int requestCode) {
        ActivityCompat.requestPermissions(activity, permissionList, requestCode);
    }

}
