package com.hidevoicemsg.xposed;

import android.view.View;
import android.view.ViewGroup;
import android.view.Menu;
import android.view.MenuItem;
import android.os.Handler;
import android.os.Looper;
import android.view.ViewTreeObserver;
import android.app.Notification;
import android.widget.TextView;
import java.util.HashSet;
import java.util.Set;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MainHook implements IXposedHookLoadPackage {

    private static final String GOOGLE_VOICE_PACKAGE = "com.google.android.apps.googlevoice";
    private static final Set<View> monitoredViews = new HashSet<>();
    private static Handler handler;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals(GOOGLE_VOICE_PACKAGE)) {
            return;
        }

        XposedBridge.log("HideVoiceMsg: Loaded into Google Voice");

        handler = new Handler(Looper.getMainLooper());

        // Hook View.onAttachedToWindow to catch when navigation views are added
        try {
            Class<?> viewClass = XposedHelpers.findClass("android.view.View", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(viewClass, "onAttachedToWindow",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;

                        // Immediately hide message views before they attach
                        CharSequence desc = view.getContentDescription();
                        if (desc != null && desc.toString().toLowerCase().contains("message")) {
                            String descStr = desc.toString().toLowerCase();
                            // Make sure it's not a call-related message
                            if (!descStr.contains("call") && !descStr.contains("voicemail")) {
                                view.setVisibility(View.GONE);
                                view.setAlpha(0f);
                                XposedBridge.log("HideVoiceMsg: Pre-hid message view before attach: " + desc);
                            }
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        String className = view.getClass().getName();

                        // Check if this is a bottom navigation view
                        if (className.contains("BottomNavigation") ||
                            className.contains("NavigationBar") ||
                            className.contains("NavigationView")) {

                            XposedBridge.log("HideVoiceMsg: Found navigation view: " + className);

                            if (!monitoredViews.contains(view)) {
                                monitoredViews.add(view);
                                startContinuousMonitoring(view);
                            }
                        }
                    }
                });

            XposedBridge.log("HideVoiceMsg: Hooked View.onAttachedToWindow");
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error hooking View: " + t.getMessage());
        }

        // Hook View's measure method to prevent message views from being measured
        try {
            Class<?> viewClass = XposedHelpers.findClass("android.view.View", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(viewClass, "measure",
                int.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        CharSequence desc = view.getContentDescription();

                        if (desc != null && desc.toString().toLowerCase().contains("message")) {
                            String descStr = desc.toString().toLowerCase();
                            if (!descStr.contains("call") && !descStr.contains("voicemail")) {
                                // Force the view to have 0 dimensions
                                view.setVisibility(View.GONE);
                                param.args[0] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
                                param.args[1] = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.EXACTLY);
                            }
                        }
                    }
                });

            XposedBridge.log("HideVoiceMsg: Hooked View.measure");
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error hooking measure: " + t.getMessage());
        }

        // Hook View's draw method to prevent rendering
        try {
            Class<?> viewClass = XposedHelpers.findClass("android.view.View", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(viewClass, "draw",
                android.graphics.Canvas.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View view = (View) param.thisObject;
                        CharSequence desc = view.getContentDescription();

                        if (desc != null && desc.toString().toLowerCase().contains("message")) {
                            String descStr = desc.toString().toLowerCase();
                            if (!descStr.contains("call") && !descStr.contains("voicemail")) {
                                // Don't draw the view at all
                                param.setResult(null);
                            }
                        }
                    }
                });

            XposedBridge.log("HideVoiceMsg: Hooked View.draw");
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error hooking draw: " + t.getMessage());
        }

        // Hook Toast to block messaging toasts
        try {
            Class<?> toastClass = XposedHelpers.findClass("android.widget.Toast", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(toastClass, "show",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Object toast = param.thisObject;

                        try {
                            // Get the toast text
                            View view = (View) XposedHelpers.callMethod(toast, "getView");
                            if (view != null && view instanceof ViewGroup) {
                                String toastText = extractTextFromView(view);
                                if (toastText != null && isMessageRelatedText(toastText)) {
                                    XposedBridge.log("HideVoiceMsg: Blocked message toast: " + toastText);
                                    param.setResult(null);
                                }
                            }
                        } catch (Throwable t) {
                            // Try alternative method for newer Android versions
                            try {
                                CharSequence text = (CharSequence) XposedHelpers.callMethod(toast, "getText");
                                if (text != null && isMessageRelatedText(text.toString())) {
                                    XposedBridge.log("HideVoiceMsg: Blocked message toast: " + text);
                                    param.setResult(null);
                                }
                            } catch (Throwable ignored) {
                            }
                        }
                    }
                });

            XposedBridge.log("HideVoiceMsg: Hooked Toast.show");
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error hooking Toast: " + t.getMessage());
        }

        // Hook ViewGroup.addView globally to catch message banners/toasts
        try {
            Class<?> viewGroupClass = XposedHelpers.findClass("android.view.ViewGroup", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(viewGroupClass, "addView",
                View.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        View child = (View) param.args[0];

                        // Check if the view being added contains message text
                        String text = extractTextFromView(child);
                        if (text != null && isMessageRelatedText(text)) {
                            XposedBridge.log("HideVoiceMsg: Blocked addView with message text: " + text);
                            param.setResult(null);
                        }

                        // Also check content description
                        CharSequence desc = child.getContentDescription();
                        if (desc != null && isMessageRelatedText(desc.toString())) {
                            XposedBridge.log("HideVoiceMsg: Blocked addView with message desc: " + desc);
                            param.setResult(null);
                        }
                    }
                });

            XposedBridge.log("HideVoiceMsg: Hooked ViewGroup.addView");
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error hooking ViewGroup.addView: " + t.getMessage());
        }

        // Hook NotificationManager to block messaging notifications
        try {
            Class<?> notificationManagerClass = XposedHelpers.findClass("android.app.NotificationManager", lpparam.classLoader);

            XposedHelpers.findAndHookMethod(notificationManagerClass, "notify",
                String.class, int.class, Notification.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Notification notification = (Notification) param.args[2];

                        if (notification != null && isMessageRelatedNotification(notification)) {
                            XposedBridge.log("HideVoiceMsg: Blocked messaging notification");
                            param.setResult(null);
                        }
                    }
                });

            XposedHelpers.findAndHookMethod(notificationManagerClass, "notify",
                int.class, Notification.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        Notification notification = (Notification) param.args[1];

                        if (notification != null && isMessageRelatedNotification(notification)) {
                            XposedBridge.log("HideVoiceMsg: Blocked messaging notification (int version)");
                            param.setResult(null);
                        }
                    }
                });

            XposedBridge.log("HideVoiceMsg: Hooked NotificationManager.notify");
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error hooking NotificationManager: " + t.getMessage());
        }
    }

    private String extractTextFromView(View view) {
        try {
            if (view instanceof android.widget.TextView) {
                CharSequence text = ((android.widget.TextView) view).getText();
                return text != null ? text.toString() : null;
            } else if (view instanceof ViewGroup) {
                ViewGroup vg = (ViewGroup) view;
                for (int i = 0; i < vg.getChildCount(); i++) {
                    String text = extractTextFromView(vg.getChildAt(i));
                    if (text != null) {
                        return text;
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore
        }
        return null;
    }

    private boolean isMessageRelatedText(String text) {
        if (text == null) return false;
        String lower = text.toLowerCase();

        // Exclude call/voicemail related
        if (lower.contains("call") || lower.contains("voicemail") || lower.contains("dial")) {
            return false;
        }

        // Check for message-related keywords
        return lower.contains("message") || lower.contains("text") ||
               lower.contains("sms") || lower.contains("mms") ||
               lower.contains("conversation") || lower.contains("chat");
    }

    private boolean isMessageRelatedNotification(Notification notification) {
        try {
            // Check notification title and text
            if (notification.extras != null) {
                CharSequence title = notification.extras.getCharSequence("android.title");
                CharSequence text = notification.extras.getCharSequence("android.text");
                CharSequence bigText = notification.extras.getCharSequence("android.bigText");

                String[] contents = new String[] {
                    title != null ? title.toString().toLowerCase() : "",
                    text != null ? text.toString().toLowerCase() : "",
                    bigText != null ? bigText.toString().toLowerCase() : ""
                };

                for (String content : contents) {
                    if (content.contains("message") || content.contains("text") ||
                        content.contains("conversation") || content.contains("sms") ||
                        content.contains("mms")) {
                        return true;
                    }
                }
            }

            // Check notification channel ID
            if (android.os.Build.VERSION.SDK_INT >= 26) { // Android O
                String channelId = notification.getChannelId();
                if (channelId != null) {
                    String channelLower = channelId.toLowerCase();
                    if (channelLower.contains("message") || channelLower.contains("text") ||
                        channelLower.contains("conversation") || channelLower.contains("sms") ||
                        channelLower.contains("mms")) {
                        return true;
                    }
                }
            }

        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error checking notification: " + t.getMessage());
        }

        return false;
    }

    private void startContinuousMonitoring(View navigationView) {
        // Set up a ViewTreeObserver to monitor for changes
        try {
            navigationView.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        try {
                            hideMessageItemsFromNavigation(navigationView);
                        } catch (Throwable t) {
                            XposedBridge.log("HideVoiceMsg: Error in layout listener: " + t.getMessage());
                        }
                    }
                }
            );

            XposedBridge.log("HideVoiceMsg: Set up continuous monitoring for: " + navigationView.getClass().getName());
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error setting up monitoring: " + t.getMessage());
        }

        // Hook ViewGroup.addView to intercept message views being added
        if (navigationView instanceof ViewGroup) {
            try {
                XposedHelpers.findAndHookMethod(ViewGroup.class, "addView",
                    View.class,
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            if (param.thisObject == navigationView) {
                                View child = (View) param.args[0];
                                CharSequence desc = child.getContentDescription();
                                if (desc != null && desc.toString().toLowerCase().contains("message")) {
                                    XposedBridge.log("HideVoiceMsg: Blocked addView of Messages tab");
                                    param.setResult(null);
                                }
                            }
                        }
                    });
            } catch (Throwable t) {
                XposedBridge.log("HideVoiceMsg: Error hooking addView: " + t.getMessage());
            }
        }

        // Do immediate check
        hideMessageItemsFromNavigation(navigationView);

        // Also do very frequent periodic checks
        final Runnable periodicCheck = new Runnable() {
            @Override
            public void run() {
                try {
                    if (navigationView.isAttachedToWindow()) {
                        hideMessageItemsFromNavigation(navigationView);
                        handler.postDelayed(this, 50); // Check every 50ms for instant hiding
                    } else {
                        monitoredViews.remove(navigationView);
                    }
                } catch (Throwable t) {
                    XposedBridge.log("HideVoiceMsg: Error in periodic check: " + t.getMessage());
                }
            }
        };

        handler.post(periodicCheck);
    }

    private boolean isMessageRelatedView(View view) {
        try {
            String className = view.getClass().getName();

            // Check content description
            CharSequence contentDesc = view.getContentDescription();
            if (contentDesc != null) {
                String descStr = contentDesc.toString().toLowerCase();
                // Exclude call-related items explicitly
                if (descStr.contains("call") || descStr.contains("dial") || descStr.contains("phone")) {
                    return false;
                }
                if (descStr.contains("message") || descStr.contains("text") ||
                    descStr.contains("conversation") || descStr.contains("sms") || descStr.contains("chat")) {
                    XposedBridge.log("HideVoiceMsg: Detected message view by contentDesc: " + descStr);
                    return true;
                }
            }

            // Check tag
            Object tag = view.getTag();
            if (tag != null) {
                String tagStr = tag.toString().toLowerCase();
                // Exclude call-related items explicitly
                if (tagStr.contains("call") || tagStr.contains("dial") || tagStr.contains("phone")) {
                    return false;
                }
                if (tagStr.contains("message") || tagStr.contains("text") ||
                    tagStr.contains("conversation") || tagStr.contains("sms") || tagStr.contains("chat")) {
                    XposedBridge.log("HideVoiceMsg: Detected message view by tag: " + tagStr);
                    return true;
                }
            }

            // Check resource name
            try {
                int viewId = view.getId();
                if (viewId != View.NO_ID) {
                    String resourceName = view.getResources().getResourceEntryName(viewId).toLowerCase();
                    // Exclude call-related items explicitly
                    if (resourceName.contains("call") || resourceName.contains("dial") || resourceName.contains("phone")) {
                        return false;
                    }
                    if (resourceName.contains("message") || resourceName.contains("text") ||
                        resourceName.contains("conversation") || resourceName.contains("sms") || resourceName.contains("chat")) {
                        XposedBridge.log("HideVoiceMsg: Detected message view by resourceName: " + resourceName);
                        return true;
                    }
                }
            } catch (Throwable ignored) {
            }

        } catch (Throwable t) {
            // Ignore
        }

        return false;
    }

    private void hideMessageItemsFromNavigation(View navigationView) {
        try {
            XposedBridge.log("HideVoiceMsg: Attempting to hide messages from: " + navigationView.getClass().getName());

            // Try to get the menu from the navigation view
            try {
                Menu menu = (Menu) XposedHelpers.callMethod(navigationView, "getMenu");

                if (menu != null) {
                    XposedBridge.log("HideVoiceMsg: Found menu with " + menu.size() + " items");

                    for (int i = menu.size() - 1; i >= 0; i--) {
                        MenuItem item = menu.getItem(i);
                        CharSequence title = item.getTitle();

                        if (title != null) {
                            String titleStr = title.toString().toLowerCase();
                            XposedBridge.log("HideVoiceMsg: Found menu item: " + title);

                            if (titleStr.contains("message") || titleStr.contains("text") ||
                                titleStr.contains("conversation") || titleStr.contains("sms")) {
                                XposedBridge.log("HideVoiceMsg: Removing Messages menu item: " + title);
                                menu.removeItem(item.getItemId());
                            }
                        }
                    }
                }
            } catch (Throwable t) {
                XposedBridge.log("HideVoiceMsg: Error accessing menu: " + t.getMessage());
            }

            // Also search child views for message-related views and hide them
            if (navigationView instanceof ViewGroup) {
                hideMessageViewsRecursively((ViewGroup) navigationView);
            }

        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error in hideMessageItemsFromNavigation: " + t.getMessage());
        }
    }

    private void hideMessageViewsRecursively(ViewGroup viewGroup) {
        try {
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                View child = viewGroup.getChildAt(i);

                // Get all identifiable info about the view
                CharSequence contentDesc = child.getContentDescription();
                Object tag = child.getTag();
                String resourceName = "";

                try {
                    int viewId = child.getId();
                    if (viewId != View.NO_ID) {
                        resourceName = child.getResources().getResourceEntryName(viewId).toLowerCase();
                    }
                } catch (Throwable ignored) {
                }

                // Build a combined string for checking
                StringBuilder combined = new StringBuilder();
                if (contentDesc != null) combined.append(contentDesc.toString().toLowerCase()).append(" ");
                if (tag != null) combined.append(tag.toString().toLowerCase()).append(" ");
                combined.append(resourceName);

                String combinedStr = combined.toString();

                // Explicitly exclude call, voicemail, and settings related items
                if (combinedStr.contains("call") || combinedStr.contains("dial") ||
                    combinedStr.contains("phone") || combinedStr.contains("voicemail") ||
                    combinedStr.contains("setting") || combinedStr.contains("account") ||
                    combinedStr.contains("search")) {
                    // Don't hide - this is not a message view
                    if (child instanceof ViewGroup) {
                        hideMessageViewsRecursively((ViewGroup) child);
                    }
                    continue;
                }

                // Check if this is message-related
                boolean isMessageView = false;
                if (combinedStr.contains("message") || combinedStr.contains("text message") ||
                    combinedStr.contains("conversation") || combinedStr.contains("sms") ||
                    combinedStr.contains("mms") || combinedStr.contains("chat")) {
                    isMessageView = true;
                }

                if (isMessageView) {
                    // Hide immediately regardless of current visibility
                    if (child.getVisibility() != View.GONE) {
                        XposedBridge.log("HideVoiceMsg: Hiding message view: " + child.getClass().getName() +
                                        " | desc=" + (contentDesc != null ? contentDesc : "null") +
                                        " | tag=" + (tag != null ? tag : "null") +
                                        " | res=" + resourceName);
                        child.setVisibility(View.GONE);

                        // Also set alpha to 0 to prevent any flicker
                        try {
                            child.setAlpha(0f);
                        } catch (Throwable ignored) {}
                    }

                    // Don't traverse children if we're hiding the parent
                    continue;
                }

                // Recursively check child views
                if (child instanceof ViewGroup) {
                    hideMessageViewsRecursively((ViewGroup) child);
                }
            }
        } catch (Throwable t) {
            XposedBridge.log("HideVoiceMsg: Error in hideMessageViewsRecursively: " + t.getMessage());
        }
    }

}
