/*
 * ServeStream: A HTTP stream browser/player for Android
 * Copyright 2014 William Seemann
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.sourceforge.servestream.fragment;

import java.math.BigInteger;
import java.security.SecureRandom;

import net.sourceforge.servestream.R;
import net.sourceforge.servestream.activity.AboutActivity;
import net.sourceforge.servestream.activity.BluetoothOptionsActivity;
import net.sourceforge.servestream.activity.MainActivity;
import net.sourceforge.servestream.billing.IabHelper;
import net.sourceforge.servestream.billing.IabResult;
import net.sourceforge.servestream.billing.Purchase;
import net.sourceforge.servestream.utils.BackupUtils;
import net.sourceforge.servestream.utils.Constants;
import net.sourceforge.servestream.preference.PreferenceConstants;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceScreen;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

public class PreferenceFragment extends net.sourceforge.servestream.preference.PreferenceFragment {
	private static final String TAG = PreferenceFragment.class.getName();

	private static final String PUBLIC_KEY = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAr6Z/tJghrCn7OSo2lWndIo+tibBhZN/mQ/Spu4IsorzHwMVW3BKPzIiyqkZa78sEs6cH68HvfoAW7QpDgJ021ZKQZaTV3m714TkLZ9RZr+rtMdPBvRkcyVWtDj3L941I4cjczs08AhAcxoIRDtA3hHZ1sKfjEgHRY19Z8oas7+f2CqCoCdRBrBCQAN55YrFw06SsnGCjHuGQgx3+pzcxuNO91s7HvJIYtCDMz+dquvQ5cU51Ia5uG3HB8ezFoag1qMq65wGed3uXANwHZUconDG6ZMYhTF4hgsS2/6es0rDZSqsgqOQ8pRIBKSg0aRmvneW6+liSycMAoL+/hl8yRwIDAQAB";
	private static final String DONATION_SKU_SMALL = "donation_small";
	private static final String DONATION_SKU_MEDIUM = "donation_medium";
	private static final String DONATION_SKU_LARGE = "donation_large";
	private static final String DONATION_SKU_XLARGE = "donation_xlarge";
	
	private String mPayload;
	
	private static final String PREF_BLUETOOTH_OPTIONS = "bluetooth_options";
	private static final String PREF_BACKUP = "backup";
	private static final String PREF_RESTORE = "restore";
	private static final String PREF_ABOUT = "about";
	private static final String PREF_DONATE = "donate";
	
	private IabHelper mHelper;
	
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		return inflater.inflate(R.layout.fragment_settings, null);
	}
	
	@Override
	public void onActivityCreated(Bundle savedInstanceState) {
		super.onActivityCreated(savedInstanceState);
		
		if (savedInstanceState == null) {
			addPreferencesFromResource(R.xml.preferences);

			findPreference(PREF_BLUETOOTH_OPTIONS).setOnPreferenceClickListener(
					new OnPreferenceClickListener() {

						@Override
						public boolean onPreferenceClick(Preference preference) {
							getActivity().startActivity(new Intent(
									getActivity(), BluetoothOptionsActivity.class));
							return true;
						}

					});

			findPreference(PREF_ABOUT).setOnPreferenceClickListener(
					new OnPreferenceClickListener() {

						@Override
						public boolean onPreferenceClick(Preference preference) {
							getActivity().startActivity(new Intent(
									getActivity(), AboutActivity.class));
							return true;
						}

					});

			findPreference(PREF_DONATE).setOnPreferenceClickListener(
					new OnPreferenceClickListener() {

						@Override
						public boolean onPreferenceClick(Preference preference) {
							mHelper = new IabHelper(getActivity(), PUBLIC_KEY);
							mHelper.startSetup(new IabHelper.OnIabSetupFinishedListener() {
								public void onIabSetupFinished(IabResult result) {
									if (!result.isSuccess()) {
										Log.d(TAG, "Problem setting up In-app Billing: " + result);
										// IAB couldn't be used lets take the user to the project's webpage instead.
										Intent intent = new Intent(Intent.ACTION_VIEW);
										intent.setData(Uri.parse(Constants.SERVESTREAM_DONATE_PAGE));
										startActivity(intent);
									} else {
										Log.d(TAG, "Hooray, IAB is fully set up!");
										showDonationAmountsDialog();
									}
								}
							});
							return true;
						}

					});
			
			findPreference(PreferenceConstants.THEME).setOnPreferenceChangeListener(
                    new OnPreferenceChangeListener() {

                        @Override
                        public boolean onPreferenceChange(Preference preference, Object newValue) {
                			Intent intent = new Intent(getActivity(), MainActivity.class);
                			intent.putExtra("restart_app", true);
                			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                			startActivity(intent);
                			getActivity().finish();
                            return true;
                        }
                    });
		}
	}

	@Override
	public void onDestroy() {
	    super.onDestroy();
        if (mHelper != null) {
            mHelper.dispose();
            mHelper = null;
        }
	}
	
	@Override
	public boolean onPreferenceTreeClick (PreferenceScreen preferenceScreen, Preference preference) {
		if (preference.getKey() != null) {
			if (preference.getKey().equals(PREF_BACKUP)) {
				BackupUtils.backup(getActivity());
			} else if (preference.getKey().equals(PREF_RESTORE)) {
				BackupUtils.restore(getActivity());
			} 
		}
		
		return true;
	}

	private void showDonationAmountsDialog() {
		AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
		builder.setTitle(R.string.donation_amount)
			.setItems(R.array.donation_amounts,
				new DialogInterface.OnClickListener() {

				@Override
				public void onClick(DialogInterface dialog, int which) {
					makeDonation(which);
					dialog.dismiss();
				}
			})
			.setNegativeButton(android.R.string.cancel, null);

		builder.create().show();
	}
	
    private void makeDonation(int which) {
    	String donationSku;

    	switch (which) {
    		case (0):
    			donationSku = DONATION_SKU_SMALL;
    			break;
    		case (1):
    			donationSku = DONATION_SKU_MEDIUM;
    			break;
    		case (2):
    			donationSku = DONATION_SKU_LARGE;
    			break;
    		case (3):
    			donationSku = DONATION_SKU_XLARGE;
    			break;
    		default:
    			return;
    	}

    	mPayload = new BigInteger(130, new SecureRandom()).toString(32);
    	mHelper.launchPurchaseFlow(getActivity(), donationSku, 10001,
    			mPurchaseFinishedListener, mPayload);
    }
	
    /** Verifies the developer payload of a purchase. */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        if (mPayload.equals(payload)) {
        	return true;
        } else {
        	return false;
        }
    }
    
	// Callback for when a purchase is finished
    IabHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new IabHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            Log.d(TAG, "Purchase finished: " + result + ", purchase: " + purchase);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure()) {
                complain("Error purchasing: " + result);
                return;
            }
            if (!verifyDeveloperPayload(purchase)) {
                complain("Error purchasing. Authenticity verification failed.");
                return;
            }

            Log.d(TAG, "Purchase successful.");

            if (purchase.getSku().equals(DONATION_SKU_SMALL) ||
            		purchase.getSku().equals(DONATION_SKU_MEDIUM) ||
            		purchase.getSku().equals(DONATION_SKU_LARGE) ||
            		purchase.getSku().equals(DONATION_SKU_XLARGE)) {
            	Toast.makeText(getActivity(), getString(R.string.donation_successful_message), Toast.LENGTH_LONG).show();
            	mHelper.consumeAsync(purchase, mConsumeFinishedListener);
            }
        }
    };
    
    // Called when consumption is complete
    IabHelper.OnConsumeFinishedListener mConsumeFinishedListener = new IabHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            Log.d(TAG, "Consumption finished. Purchase: " + purchase + ", result: " + result);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;
        }
    };
    
    private void complain(String message) {
        Log.e(TAG, "**** Error: " + message);
    }
}