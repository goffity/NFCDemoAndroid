package com.goffity.demo.nfcdemo;

import java.io.UnsupportedEncodingException;
import java.util.Arrays;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareClassic;
import android.nfc.tech.MifareUltralight;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.util.Log;
import android.view.Menu;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

	public static final String TAG = "NfcDemo";

	public static final String MIME_TEXT_PLAIN = "text/plain";

	private TextView mTextView;
	private NfcAdapter mNfcAdapter;

	private IntentFilter[] mFilters;

	private String[][] mTechLists;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		setContentView(R.layout.activity_main);

		mTextView = (TextView) findViewById(R.id.textView_explanation);
		mNfcAdapter = NfcAdapter.getDefaultAdapter(MainActivity.this);

		if (mNfcAdapter == null) {
			// Stop here, we definitely need NFC
			Toast.makeText(this, "This device doesn't support NFC.",
					Toast.LENGTH_LONG).show();
			finish();
			return;
		}
		if (!mNfcAdapter.isEnabled()) {
			mTextView.setText("NFC is disabled.");
		} else {
			mTextView.setText(R.string.hello_world);
		}

		// PendingIntent mPendingIntent = PendingIntent.getActivity(this, 0,
		// new Intent(this, getClass())
		// .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
		//
		// IntentFilter ndef = new
		// IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
		//
		// try {
		// ndef.addDataType("*/*");
		// } catch (MalformedMimeTypeException e) {
		// throw new RuntimeException("fail", e);
		// }
		// mFilters = new IntentFilter[] { ndef, };
		//
		// // Setup a tech list for all NfcF tags
		// mTechLists = new String[][] { new String[] { MifareClassic.class
		// .getName() } };

		handleIntent(getIntent());
	}

	private void handleIntent(Intent intent) {
		Log.i(TAG, "handleIntent()");
		String action = intent.getAction();
		Log.d(TAG, "Action: " + action);

		if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(action)) {
			Log.d(TAG, NfcAdapter.ACTION_NDEF_DISCOVERED);
			String type = intent.getType();
			if (MIME_TEXT_PLAIN.equals(type)) {
				Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
				new NdefReaderTask().execute(tag);
			} else {
				Log.d(TAG, "Wrong mime type: " + type);
			}
		} else if (NfcAdapter.ACTION_TECH_DISCOVERED.equals(action)) {
			Log.d(TAG, NfcAdapter.ACTION_TECH_DISCOVERED);
			// In case we would still use the Tech Discovered Intent
			Tag tag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);
			String[] techList = tag.getTechList();
			String searchedTech = Ndef.class.getName();
			for (String tech : techList) {
				if (searchedTech.equals(tech)) {
					new NdefReaderTask().execute(tag);
					break;
				}
			}
		} else {
			Parcelable[] rawMsgs = intent
					.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
			NdefMessage[] msgs;

			if (rawMsgs != null) {
				msgs = new NdefMessage[rawMsgs.length];
				for (int i = 0; i < rawMsgs.length; i++) {
					msgs[i] = (NdefMessage) rawMsgs[i];
				}
			} else {
				// Unknown tag type
				byte[] empty = new byte[0];
				byte[] id = intent.getByteArrayExtra(NfcAdapter.EXTRA_ID);
				Parcelable tag = intent
						.getParcelableExtra(NfcAdapter.EXTRA_TAG);
				Log.d(TAG, "dump data: " + dumpTagData(tag));
				byte[] payload = dumpTagData(tag).getBytes();
				NdefRecord record = new NdefRecord(NdefRecord.TNF_UNKNOWN,
						empty, id, payload);
				NdefMessage msg = new NdefMessage(new NdefRecord[] { record });
				msgs = new NdefMessage[] { msg };
			}
		}
	}

	@Override
	protected void onResume() {
		super.onResume();
		/**
		 * It's important, that the activity is in the foreground (resumed).
		 * Otherwise an IllegalStateException is thrown.
		 */
		setupForegroundDispatch(this, mNfcAdapter);
	}

	@Override
	protected void onPause() {
		/**
		 * Call this before onPause, otherwise an IllegalArgumentException is
		 * thrown as well.
		 */
		stopForegroundDispatch(this, mNfcAdapter);
		super.onPause();
	}

	@Override
	protected void onNewIntent(Intent intent) {
		/**
		 * This method gets called, when a new Intent gets associated with the
		 * current activity instance. Instead of creating a new activity,
		 * onNewIntent will be called. For more information have a look at the
		 * documentation.
		 * 
		 * In our case this method gets called, when the user attaches a Tag to
		 * the device.
		 */
		setIntent(intent);
		handleIntent(intent);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	/**
	 * @param activity
	 *            The corresponding {@link Activity} requesting the foreground
	 *            dispatch.
	 * @param adapter
	 *            The {@link NfcAdapter} used for the foreground dispatch.
	 */
	public static void setupForegroundDispatch(final Activity activity,
			NfcAdapter adapter) {
		Log.i(TAG, "setupForegroundDispatch()");

		final Intent intent = new Intent(activity.getApplicationContext(),
				activity.getClass());

		intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
		final PendingIntent pendingIntent = PendingIntent.getActivity(
				activity.getApplicationContext(), 0, intent, 0);

		IntentFilter[] filters = new IntentFilter[1];
		String[][] techList = new String[][] {};

		// Notice that this is the same filter as in our manifest.
		filters[0] = new IntentFilter();
		filters[0].addAction(NfcAdapter.ACTION_NDEF_DISCOVERED);
		filters[0].addCategory(Intent.CATEGORY_DEFAULT);

		try {
			filters[0].addDataType(MIME_TEXT_PLAIN);
		} catch (MalformedMimeTypeException e) {
			throw new RuntimeException("Check your mime type.");
		}
		adapter.enableForegroundDispatch(activity, pendingIntent, filters,
				techList);
	}

	/**
	 * @param activity
	 *            The corresponding {@link BaseActivity} requesting to stop the
	 *            foreground dispatch.
	 * @param adapter
	 *            The {@link NfcAdapter} used for the foreground dispatch.
	 */
	public static void stopForegroundDispatch(final Activity activity,
			NfcAdapter adapter) {
		Log.i(TAG, "stopForegroundDispatch()");
		adapter.disableForegroundDispatch(activity);
	}

	private String dumpTagData(Parcelable p) {
		StringBuilder sb = new StringBuilder();
		Tag tag = (Tag) p;
		byte[] id = tag.getId();
		sb.append("Tag ID (hex): ").append(getHex(id)).append("\n");
		sb.append("Tag ID (dec): ").append(getDec(id)).append("\n");
		sb.append("ID (reversed): ").append(getReversed(id)).append("\n");

		String prefix = "android.nfc.tech.";
		sb.append("Technologies: ");
		for (String tech : tag.getTechList()) {
			sb.append(tech.substring(prefix.length()));
			sb.append(", ");
		}
		sb.delete(sb.length() - 2, sb.length());
		for (String tech : tag.getTechList()) {
			if (tech.equals(MifareClassic.class.getName())) {
				sb.append('\n');
				MifareClassic mifareTag = MifareClassic.get(tag);
				String type = "Unknown";
				switch (mifareTag.getType()) {
				case MifareClassic.TYPE_CLASSIC:
					type = "Classic";
					break;
				case MifareClassic.TYPE_PLUS:
					type = "Plus";
					break;
				case MifareClassic.TYPE_PRO:
					type = "Pro";
					break;
				}
				sb.append("Mifare Classic type: ");
				sb.append(type);
				sb.append('\n');

				sb.append("Mifare size: ");
				sb.append(mifareTag.getSize() + " bytes");
				sb.append('\n');

				sb.append("Mifare sectors: ");
				sb.append(mifareTag.getSectorCount());
				sb.append('\n');

				sb.append("Mifare blocks: ");
				sb.append(mifareTag.getBlockCount());
			}

			if (tech.equals(MifareUltralight.class.getName())) {
				sb.append('\n');
				MifareUltralight mifareUlTag = MifareUltralight.get(tag);
				String type = "Unknown";
				switch (mifareUlTag.getType()) {
				case MifareUltralight.TYPE_ULTRALIGHT:
					type = "Ultralight";
					break;
				case MifareUltralight.TYPE_ULTRALIGHT_C:
					type = "Ultralight C";
					break;
				}
				sb.append("Mifare Ultralight type: ");
				sb.append(type);
			}
		}

		return sb.toString();
	}

	private String getHex(byte[] bytes) {
		StringBuilder sb = new StringBuilder();
		for (int i = bytes.length - 1; i >= 0; --i) {
			int b = bytes[i] & 0xff;
			if (b < 0x10)
				sb.append('0');
			sb.append(Integer.toHexString(b));
			if (i > 0) {
				sb.append(" ");
			}
		}
		return sb.toString();
	}

	private long getDec(byte[] bytes) {
		long result = 0;
		long factor = 1;
		for (int i = 0; i < bytes.length; ++i) {
			long value = bytes[i] & 0xffl;
			result += value * factor;
			factor *= 256l;
		}
		return result;
	}

	private long getReversed(byte[] bytes) {
		long result = 0;
		long factor = 1;
		for (int i = bytes.length - 1; i >= 0; --i) {
			long value = bytes[i] & 0xffl;
			result += value * factor;
			factor *= 256l;
		}
		return result;
	}

	/**
	 * Background task for reading the data. Do not block the UI thread while
	 * reading.
	 * 
	 * @author Ralf Wondratschek
	 * 
	 */
	private class NdefReaderTask extends AsyncTask<Tag, Void, String> {
		@Override
		protected String doInBackground(Tag... params) {
			Log.i(TAG, "1");
			Tag tag = params[0];
			Ndef ndef = Ndef.get(tag);
			if (ndef == null) {
				// NDEF is not supported by this Tag.
				return null;
			}
			NdefMessage ndefMessage = ndef.getCachedNdefMessage();
			NdefRecord[] records = ndefMessage.getRecords();
			for (NdefRecord ndefRecord : records) {
				if (ndefRecord.getTnf() == NdefRecord.TNF_WELL_KNOWN
						&& Arrays.equals(ndefRecord.getType(),
								NdefRecord.RTD_TEXT)) {
					try {
						return readText(ndefRecord);
					} catch (UnsupportedEncodingException e) {
						Log.e(TAG, "Unsupported Encoding", e);
					}
				}
			}
			return null;
		}

		private String readText(NdefRecord record)
				throws UnsupportedEncodingException {
			Log.i(TAG, "2");
			/*
			 * See NFC forum specification for "Text Record Type Definition" at
			 * 3.2.1
			 * 
			 * http://www.nfc-forum.org/specs/
			 * 
			 * bit_7 defines encoding bit_6 reserved for future use, must be 0
			 * bit_5..0 length of IANA language code
			 */
			byte[] payload = record.getPayload();
			// Get the Text Encoding
			String textEncoding = ((payload[0] & 128) == 0) ? "UTF-8"
					: "UTF-16";
			// Get the Language Code
			int languageCodeLength = payload[0] & 0063;
			// String languageCode = new String(payload, 1, languageCodeLength,
			// "US-ASCII");
			// e.g. "en"
			// Get the Text
			return new String(payload, languageCodeLength + 1, payload.length
					- languageCodeLength - 1, textEncoding);
		}

		@Override
		protected void onPostExecute(String result) {
			Log.i(TAG, "3");

			Log.d(TAG, "Result: " + result);
			if (result != null) {
				mTextView.setText("Read content: " + result);
			}
		}
	}

}
