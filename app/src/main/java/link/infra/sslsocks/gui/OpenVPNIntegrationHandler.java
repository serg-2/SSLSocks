/*
 * Copyright (C) 2017-2021 comp500
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify this Program, or any covered work, by linking or combining
 * it with OpenSSL (or a modified version of that library), containing parts
 * covered by the terms of the OpenSSL License, the licensors of this Program
 * grant you additional permission to convey the resulting work.
 */

package link.infra.sslsocks.gui;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Objects;

import de.blinkt.openvpn.api.APIVpnProfile;
import de.blinkt.openvpn.api.IOpenVPNAPIService;

public class OpenVPNIntegrationHandler {
	private IOpenVPNAPIService srv = null;
	public static final int PERMISSION_REQUEST = 100;
	public static final int VPN_PERMISSION_REQUEST = 101;

	private static final String TAG = OpenVPNIntegrationHandler.class.getSimpleName();
	private final WeakReference<Context> ctxRef;
	private boolean isActivity = true;
	private final Runnable doneCallback;
	private final String profileName;
	private final boolean shouldDisconnect;

	public OpenVPNIntegrationHandler(Activity ctx, Runnable doneCallback, String profile, boolean shouldDisconnect) {
		this.ctxRef = new WeakReference<>(ctx);
		this.doneCallback = doneCallback;
		this.profileName = profile;
		this.shouldDisconnect = shouldDisconnect;
		Log.d(TAG, "created");
	}

	public OpenVPNIntegrationHandler(Context ctx, Runnable doneCallback, String profile, boolean shouldDisconnect) {
		isActivity = false;
		this.ctxRef = new WeakReference<>(ctx);
		this.doneCallback = doneCallback;
		this.profileName = profile;
		this.shouldDisconnect = shouldDisconnect;
		Log.d(TAG, "created");
	}

	private final ServiceConnection mConnection = new ServiceConnection() {
		public void onServiceConnected(ComponentName className, IBinder service) {
			srv = IOpenVPNAPIService.Stub.asInterface(service);
			try {
				Intent intent = srv.prepare(ctxRef.get().getPackageName());
				if (intent != null && isActivity) {
					Log.d(TAG, "requesting permission");
					((Activity)ctxRef.get()).startActivityForResult(intent, PERMISSION_REQUEST);
				} else {
					doVpnPermissionRequest();
				}
			} catch (RemoteException e) {
				Log.e(TAG, "Failed to connect to OpenVPN", e);
			}
		}

		public void onServiceDisconnected(ComponentName className) {
			srv = null;
		}
	};

	public void bind() {
		Log.d(TAG, "onBind");
		if (srv != null || ctxRef.get() == null) return;
		Intent intent = new Intent(IOpenVPNAPIService.class.getName());
		intent.setPackage("de.blinkt.openvpn");
		ctxRef.get().bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
		Log.d(TAG, "bound");
	}

	public void unbind() {
		Log.e(TAG, "Unbind");
		if (ctxRef.get() == null) return;
		ctxRef.get().unbindService(mConnection);
	}

	public void doVpnPermissionRequest() {
		Log.d(TAG, "onDoVpnPermissionRequest");
        try {
			Intent intent = srv.prepareVPNService();
			if (intent != null && isActivity) {
				Log.i(TAG, "Openvpn already working. Requesting vpn perms");
				((Activity)ctxRef.get()).startActivityForResult(intent, VPN_PERMISSION_REQUEST);
			} else {
                Log.i(TAG, "No openvpn activity found. Will start...");
				if (shouldDisconnect) {
                    Log.i(TAG, "Openvpn Should disconnect first");
					disconnect();
				} else {
                    Log.i(TAG, "Openvpn starting now ...");
					connectProfile();
				}
			}
		} catch (RemoteException e) {
			Log.e(TAG, "Failed to connect to OpenVPN", e);
		}
	}

	public void connectProfile() {
		Log.i(TAG, "Connecting to  profile...");
		try {
			List<APIVpnProfile> profiles = srv.getProfiles();
			APIVpnProfile foundProfile = null;
			for (APIVpnProfile profile : profiles) {
				if (Objects.equals(profile.mName, profileName)) {
					foundProfile = profile;
					break;
				}
			}
			if (foundProfile == null) {
				Log.e(TAG, "Failed to find profile");
				return;
			}
			Log.d(TAG, "starting profile");
			srv.startProfile(foundProfile.mUUID);
		} catch (RemoteException e) {
			Log.e(TAG, "Failed to connect to OpenVPN", e);
		}
		doneCallback.run();
	}

	public void disconnect() {
		if (srv != null) {
			try {
				srv.disconnect();
			} catch (RemoteException e) {
				Log.e(TAG, "Failed to connect to OpenVPN", e);
			}
		} else {
			Log.i(TAG, "Openvpn object is null");
		}
		if (shouldDisconnect) {
			doneCallback.run();
		}
	}
}
