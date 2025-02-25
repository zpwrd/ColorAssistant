package com.zpwrd.colorassistant;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class PowerButtonInterceptor implements IXposedHookLoadPackage {
    private static final String TARGET_PACKAGE = "com.parallelc.micts";
    private static final String TARGET_ACTIVITY = "ui.activity.MainActivity";
    private static final long LONG_PRESS_DELAY = 200;

    private Handler handler;
    private boolean isHoldingPowerButton = false;
    private boolean isAppLaunched = false;
    private Runnable launchRunnable;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (!lpparam.packageName.equals("android")) {
            return;
        }
        //XposedBridge.log("PowerButtonInterceptor: Модуль загружен для пакета " + lpparam.packageName);

        // Инициализация Handler на главном потоке
        handler = new Handler(Looper.getMainLooper());

        // Определение задачи для запуска активности
        launchRunnable = new Runnable() {
            @Override
            public void run() {
                if (isHoldingPowerButton) {
                    XposedBridge.log("PowerButtonInterceptor: Удержание более 0,5 сек, запуск активности");
                    launchApp();
                    isAppLaunched = true;
                }
            }
        };

        // Перехват метода interceptKeyBeforeQueueing для обработки нажатий кнопки питания
        Class<?> phoneWindowManagerClass = XposedHelpers.findClass(
                "com.android.server.policy.PhoneWindowManager", lpparam.classLoader
        );

        XposedHelpers.findAndHookMethod(
                phoneWindowManagerClass,
                "interceptKeyBeforeQueueing",
                KeyEvent.class, int.class,
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        KeyEvent event = (KeyEvent) param.args[0];
                        int keyCode = event.getKeyCode();

                        if (keyCode == KeyEvent.KEYCODE_POWER) {
                            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                                // Нажатие кнопки питания
                                if (!isHoldingPowerButton) {
                                    isHoldingPowerButton = true;
                                    //XposedBridge.log("PowerButtonInterceptor: Кнопка питания нажата, запуск таймера");
                                    handler.postDelayed(launchRunnable, LONG_PRESS_DELAY);
                                }
                            } else if (event.getAction() == KeyEvent.ACTION_UP) {
                                // Отжатие кнопки питания
                                //XposedBridge.log("PowerButtonInterceptor: Кнопка питания отжата, isAppLaunched: " + isAppLaunched);
                                if (!isAppLaunched) {
                                    handler.removeCallbacks(launchRunnable); // Отмена запуска, если менее 0,5 сек
                                }
                                isHoldingPowerButton = false;
                                isAppLaunched = false; // Сброс флагов
                            }
                        }
                    }
                }
        );

        // Перехват метода powerLongPress для подавления меню выключения
        /* XposedHelpers.findAndHookMethod(
                phoneWindowManagerClass,
                "powerLongPress",
                new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        if (isAppLaunched) {
                            XposedBridge.log("PowerButtonInterceptor: Подавление меню выключения");
                            param.setResult(null); // Подавляем вызов меню выключения
                        }
                    }
                }
        ); */
    }

    private void launchApp() {
        //XposedBridge.log("PowerButtonInterceptor: Попытка запустить " + TARGET_PACKAGE);
        handler.post(() -> {
            try {
                Context context = (Context) XposedHelpers.callMethod(
                        XposedHelpers.callStaticMethod(
                                XposedHelpers.findClass("android.app.ActivityThread", null),
                                "currentActivityThread"
                        ),
                        "getSystemContext"
                );
                //XposedBridge.log("PowerButtonInterceptor: Получен системный контекст");

                Intent intent = new Intent();
                intent.setClassName(TARGET_PACKAGE, TARGET_PACKAGE + "." + TARGET_ACTIVITY);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                //XposedBridge.log("PowerButtonInterceptor: Активность успешно запущена");
            } catch (Throwable t) {
                XposedBridge.log("PowerButtonInterceptor: Ошибка при запуске активности: " + t.getMessage());
            }
        });
    }
}