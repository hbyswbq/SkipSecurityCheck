package com.skipsecurity;

import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedHelpers;

public class VirusCheckHook extends XC_MethodReplacement {
    @Override
    protected Object replaceHookedMethod(MethodHookParam param) throws Throwable {
        Object mHandler = XposedHelpers.getObjectField(
            param.thisObject, "mHandler");
        XposedHelpers.callMethod(mHandler, "sendEmptyMessage", 5);
        return null;
    }
}
