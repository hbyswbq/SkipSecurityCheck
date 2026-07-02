package com.skipsecurity;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
public class MainHook implements IXposedHookLoadPackage {

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam param) {
        if (!"com.android.packageinstaller".equals(param.packageName)) return;

        XposedBridge.log("[SkipSecurityCheck] Hooking com.android.packageinstaller");

        // bypass security check during install
        enableCheckInstaller(param);
    }

    private void enableCheckInstaller(XC_LoadPackage.LoadPackageParam param) {
        try {
            Class<?> clazz = XposedHelpers.findClass(
                "com.android.packageinstaller.FlymePackageInstallerActivity",
                param.classLoader
            );

            // Hook setVirusCheckTime
            XposedHelpers.findAndHookMethod(clazz, "setVirusCheckTime",
                new VirusCheckHook());
            XposedBridge.log("[SkipSecurityCheck] setVirusCheckTime hooked");

            // Hook replaceOrInstall
            XposedHelpers.findAndHookMethod(clazz, "replaceOrInstall", String.class,
                new ReplaceOrInstallHook());
            XposedBridge.log("[SkipSecurityCheck] replaceOrInstall hooked");

        } catch (Throwable e) {
            XposedBridge.log("[SkipSecurityCheck] enableCheckInstaller failed: " + e.getMessage());
        }
    }

}