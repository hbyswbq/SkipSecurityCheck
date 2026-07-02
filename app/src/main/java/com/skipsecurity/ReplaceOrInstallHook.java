package com.skipsecurity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class ReplaceOrInstallHook extends XC_MethodHook {
    @Override
    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
        XposedHelpers.setObjectField(param.thisObject, "mAppInfo", null);
    }
}
