package cn.shoudle.push;

import java.util.ArrayList;

import cn.shoudle.listener.EventListener;
import cn.shoudle.service.SdService;
import cn.shoudle.util.PreferenceUtils;
import cn.shoudle.util.SdLog;
import cn.shoudle.v1.SdConstants;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.text.TextUtils;

public class SdBroadcastReceiver extends BroadcastReceiver{

	public static final String BOOT_COMPLETED_ACTION = "com.way.action.BOOT_COMPLETED";
	private static final String TAG = "SdBroadcastReceiver";
	public static ArrayList<EventListener> mListeners = new ArrayList<EventListener>();

	@Override
	public void onReceive(Context context, Intent intent) {
		String action = intent.getAction();
		SdLog.i(TAG,"action = " + action);
		if (TextUtils.equals(action, ConnectivityManager.CONNECTIVITY_ACTION)) {
			if (mListeners.size() > 0)// 通知接口完成加载
				for (EventListener handler : mListeners) {
					handler.onNetChange();
				}
		} else if (intent.getAction().equals(Intent.ACTION_SHUTDOWN)) {
			SdLog.d(TAG,"System shutdown, stopping service.");
			Intent xmppServiceIntent = new Intent(context, SdService.class);
			context.stopService(xmppServiceIntent);
		} else {
			if (!TextUtils.isEmpty(PreferenceUtils.getPrefString(context,
					SdConstants.CONS_PASSWORD, ""))
					&& PreferenceUtils.getPrefBoolean(context,
							SdConstants.CONS_AUTO_START, true)) {
				Intent i = new Intent(context, SdService.class);
				i.setAction(BOOT_COMPLETED_ACTION);
				context.startService(i);
			}
		}
	}
}
