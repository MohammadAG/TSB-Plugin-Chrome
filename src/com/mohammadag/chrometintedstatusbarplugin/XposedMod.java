package com.mohammadag.chrometintedstatusbarplugin;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.widget.FrameLayout;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

public class XposedMod implements IXposedHookLoadPackage {
	private boolean mIsTablet = false;

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {
		if (!"com.android.chrome".equals(lpparam.packageName)
				&& !"com.chrome.beta".equals(lpparam.packageName))
			return;

		XposedHelpers.findAndHookMethod("com.google.android.apps.chrome.toolbar.ToolbarPhone",
				lpparam.classLoader, "updateToolbarBackground", int.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				FrameLayout layout = (FrameLayout) param.thisObject;
				if (mIsTablet) {
					StatusBarTintApi.sendColorChangeIntent(Color.BLACK, -3,
							-3, -3, layout.getContext());
					return;
				}
				Drawable drawable = (Drawable) XposedHelpers.getObjectField(param.thisObject,
						"mToolbarBackground");
				int color = getMainColorFromActionBarDrawable(drawable);

				StatusBarTintApi.sendColorChangeIntent(color, getIconColorForColor(color),
						-3, -3, layout.getContext());
			}
		});

		XposedHelpers.findAndHookMethod("com.google.android.apps.chrome.Main", lpparam.classLoader,
				"onWindowFocusChanged", boolean.class, new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				boolean focused = (Boolean) param.args[0];
				if (focused) {
					Activity activity = (Activity) param.thisObject;
					if (mIsTablet) {
						StatusBarTintApi.sendColorChangeIntent(Color.BLACK, -3,
								-3, -3, activity.getApplicationContext());
						return;
					}
					Object mToolbar = XposedHelpers.getObjectField(param.thisObject, "mToolbar");
					Object mActualView = XposedHelpers.callMethod(mToolbar, "getView");

					Drawable drawable = (Drawable) XposedHelpers.getObjectField(mActualView, "mToolbarBackground");
					int color = getMainColorFromActionBarDrawable(drawable);

					StatusBarTintApi.sendColorChangeIntent(color, getIconColorForColor(color),
							-3, -3, activity.getApplicationContext());
				}
			}
		});

		XposedHelpers.findAndHookMethod("com.google.android.apps.chrome.Main", lpparam.classLoader,
				"preInflationStartup", new XC_MethodHook() {
			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable {
				mIsTablet = XposedHelpers.getBooleanField(param.thisObject, "mIsTablet");
			}
		});
	}

	public static int getMainColorFromActionBarDrawable(Drawable drawable) throws IllegalArgumentException {
		/* This should fix the bug where a huge part of the ActionBar background is drawn white. */
		Drawable copyDrawable = drawable.getConstantState().newDrawable();

		if (copyDrawable instanceof ColorDrawable) {
			return ((ColorDrawable) drawable).getColor();
		}

		Bitmap bitmap = drawableToBitmap(copyDrawable);
		int pixel = bitmap.getPixel(0, 40);
		int red = Color.red(pixel);
		int blue = Color.blue(pixel);
		int green = Color.green(pixel);
		int alpha = Color.alpha(pixel);
		copyDrawable = null;
		return Color.argb(alpha, red, green, blue);
	}

	public static Bitmap drawableToBitmap(Drawable drawable) throws IllegalArgumentException {
		if (drawable instanceof BitmapDrawable) {
			return ((BitmapDrawable)drawable).getBitmap();
		}
		Bitmap bitmap;

		try {
			bitmap = Bitmap.createBitmap(1, 80, Config.ARGB_8888);
			bitmap.setDensity(480);
			Canvas canvas = new Canvas(bitmap); 
			drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
			drawable.draw(canvas);
		} catch (IllegalArgumentException e) {
			throw e;
		}

		return bitmap;
	}

	public static int getIconColorForColor(int color) {
		float hsvMaxValue = 0.7f;
		float[] hsv = new float[3];
		Color.colorToHSV(color, hsv);
		float value = hsv[2];

		if (value > hsvMaxValue) {
			return Color.BLACK;
		} else {
			return Color.WHITE;
		}
	}
}
