package com.skipsecurity;

import java.lang.reflect.Field;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * 搜狗输入法 Hook
 * - 解除剪贴板字数限制(原1000字符)
 * - 解除剪贴板条数限制(原100条)
 * - 禁用浮动模式自动开启
 */
public class SogouClipboardHook {

    private static final String TAG = "[SkipSecurityCheck][Sogou]";

    public static void init(XC_LoadPackage.LoadPackageParam param) {
        XposedBridge.log(TAG + " Initializing Sogou hooks...");

        hookWordLimit(param);
        hookItemCountLimit(param);
        hookDisableFloatMode(param);
    }

    /**
     * 字数限制：Hook String.substring(int, int)
     * 当搜狗输入法剪贴板触发截断 substring(0, 1000) 时，返回完整原字符串
     */
    private static void hookWordLimit(XC_LoadPackage.LoadPackageParam param) {
        try {
            XposedBridge.hookAllMethods(
                String.class,
                "substring",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam hookParam) throws Throwable {
                        if (hookParam.args.length != 2) return;

                        int endIndex = (Integer) hookParam.args[1];
                        // 只关心截断到1000字符的调用
                        if (endIndex != 1000) return;

                        // 通过调用栈判断是否来自剪贴板截断
                        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                        for (StackTraceElement element : stack) {
                            String className = element.getClassName();
                            String methodName = element.getMethodName();
                            // hx0.onPrimaryClipChanged() 是剪贴板监听回调
                            if ("hx0".equals(className) && "onPrimaryClipChanged".equals(methodName)) {
                                String original = (String) hookParam.thisObject;
                                hookParam.setResult(original);
                                XposedBridge.log(TAG + " Word limit bypassed, original length: " + original.length());
                                return;
                            }
                        }
                    }
                }
            );
            XposedBridge.log(TAG + " Word limit hook installed (1000 chars -> unlimited)");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " hookWordLimit failed: " + e.getMessage());
        }
    }

    /**
     * 条数限制：完全替换 hx0$a.a(List) 方法
     * 原逻辑：超过100条时删除最旧的一条
     * 新逻辑：不限制条数
     */
    private static void hookItemCountLimit(XC_LoadPackage.LoadPackageParam param) {
        try {
            Class<?> innerCallbackClass = XposedHelpers.findClass("hx0$a", param.classLoader);
            Class<?> cx0Class = XposedHelpers.findClass("cx0", param.classLoader);
            Class<?> ex0Class = XposedHelpers.findClass("ex0", param.classLoader);

            XposedBridge.hookAllMethods(innerCallbackClass, "a", new XC_MethodReplacement() {
                @Override
                @SuppressWarnings("unchecked")
                protected Object replaceHookedMethod(MethodHookParam hookParam) throws Throwable {
                    List list = (List) hookParam.args[0];
                    if (list == null) {
                        list = new java.util.ArrayList();
                    }

                    // 获取外部类 hx0 实例
                    Object hx0Instance = XposedHelpers.getObjectField(hookParam.thisObject, "this$0");

                    // 找到 List<String> 类型的待处理队列字段（混淆后字段名都叫 a，需要按类型找）
                    List<String> pendingList = null;
                    Field[] fields = hx0Instance.getClass().getDeclaredFields();
                    for (Field field : fields) {
                        if (List.class.isAssignableFrom(field.getType())) {
                            field.setAccessible(true);
                            pendingList = (List<String>) field.get(hx0Instance);
                            break;
                        }
                    }
                    if (pendingList == null || pendingList.size() == 0) {
                        return null;
                    }

                    String newText = pendingList.get(0);
                    pendingList.remove(0);

                    // 查找是否已存在相同内容，存在则移到最前面
                    boolean exists = false;
                    for (int i = 0; i < list.size(); i++) {
                        Object item = list.get(i);
                        String itemText = (String) XposedHelpers.getObjectField(item, "d");
                        if (itemText.equals(newText)) {
                            list.remove(i);
                            list.add(0, item);
                            XposedHelpers.setLongField(item, "a", System.currentTimeMillis());
                            exists = true;
                            break;
                        }
                    }

                    Object ex0Instance = XposedHelpers.callStaticMethod(ex0Class, "a");

                    if (exists) {
                        XposedHelpers.callMethod(ex0Instance, "m5153a", list);
                        return null;
                    }

                    // 添加新条目 —— 不再删除旧条目，条数无上限
                    Object newItem = cx0Class.getConstructor(String.class).newInstance(newText);
                    list.add(0, newItem);

                    XposedHelpers.callMethod(ex0Instance, "m5153a", list);
                    XposedBridge.log(TAG + " Clipboard item added, total count: " + list.size());
                    return null;
                }
            });

            XposedBridge.log(TAG + " Item count limit hook installed (100 -> unlimited)");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " hookItemCountLimit failed: " + e.getMessage());
        }
    }

    /**
     * 禁用浮动模式自动开启
     * 防止调整键盘高度到550dp以上时自动切换到浮动模式，导致键盘调节按钮消失
     */
    private static void hookDisableFloatMode(XC_LoadPackage.LoadPackageParam param) {
        try {
            // Hook qp1.d(true) - 这个方法是设置浮动模式开关的
            // 之前Hook错成了c()，现在修正为d()
            Class<?> qp1Class = XposedHelpers.findClass("qp1", param.classLoader);
            XposedBridge.hookAllMethods(qp1Class, "d", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam hookParam) throws Throwable {
                    if (hookParam.args.length > 0 && hookParam.args[0] instanceof Boolean) {
                        boolean enable = (Boolean) hookParam.args[0];
                        if (enable) {
                            // 阻止开启浮动模式，但不阻止关闭
                            hookParam.setResult(null);
                            XposedBridge.log(TAG + " Blocked float mode enable (d(true))");
                        }
                    }
                }
            });

            XposedBridge.log(TAG + " Float mode disable hook installed (d method)");
        } catch (Throwable e) {
            XposedBridge.log(TAG + " hookDisableFloatMode failed: " + e.getMessage());
        }
    }
}
