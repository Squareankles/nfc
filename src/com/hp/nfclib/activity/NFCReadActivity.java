package com.hp.nfclib.activity;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Bundle;
import android.os.Parcelable;
import android.widget.Toast;

public abstract class NFCReadActivity extends Activity {


	private NfcAdapter adapter;
	private PendingIntent pendingIntent;
	private IntentFilter writeTagFilters[];
	private Tag mytag;
	private Context ctx;
	private Activity callingActivity;
	//
	protected boolean willRead;

	//The method will be called when the tag contents are read
	protected abstract void stringFromTagUpdate(String tagString);

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		//Retrieve the context
		ctx = getApplicationContext();

		//Get the the concrete activity 
		callingActivity=this;

		//Get your adapter class, Deals with NFC Interactions on the Device	
		adapter = NfcAdapter.getDefaultAdapter(ctx);

		//Check the Device has NFC
		if(adapter!=null){

			//check the device has nfc enabled
			if (!adapter.isEnabled())
			{
				//Put into methods so they may be overwrote
				sendUnactivatedMessageToTheUI();
				openNFCSettings();
			}

			//Create the pending intent which is an intent that is created when something happens links with the NFCAdapter enableForegroundDispatch method
			//Intent.FLAG_ACTIVITY_SINGLE_TOP will cause the onNewIntent Method to be called, it will not create a new instance of the class on top of the original it will just call the onNewIntent() 
			pendingIntent = PendingIntent.getActivity(ctx, 0, new Intent(ctx, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
			//Filter the new Actions(Intents)for type of NfcAdapter.ACTION_TAG_DISCOVERED
			IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);

			//Add the IntentFilter to the Array of IntentFilters, which will be added to the enableForegroundDispatch method
			writeTagFilters = new IntentFilter[] { tagDetected };
			willRead=true;
		}
		else
		{
			willRead=false;
			Toast.makeText(getApplicationContext(), "Your device does not support NFC", Toast.LENGTH_LONG).show();
		}
	}

	@Override
	protected void onPause(){
		super.onPause();
		//Turn's off the ability to detect tags if the Inherited activity sleeps
		ReadModeOff();
	}

	@Override
	protected void onResume(){
		super.onResume();
		//Resumes the ability to detect tags
		ReadModeOn();
	}

	@Override
	protected void onNewIntent(Intent intent){
		// Check to see if the Tag Discovered from the NfcAdapter kicked off the intent
		if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
			tagDiscovered();
			if(willRead)
			{
				
				//Get's the Tag from the Intent
				mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);    
				ReadFromTag(intent);
			}
			else
			{
				readModeDisabled();
			}
			
		}
	}
	///////////////////////////////////////////////////////////
	//		
	//		Methods for reading from the tag
	//
	///////////////////////////////////////////////////////////
	
	private void ReadModeOn(){

		try
		{
			//This Line Sets up the Listener in the class
			adapter.enableForegroundDispatch(callingActivity, pendingIntent, writeTagFilters, null);
		}
		catch(Exception e)
		{
			e.toString();
		}
	}

	private void ReadModeOff(){
		if(adapter!=null){
		adapter.disableForegroundDispatch(callingActivity);
		}
	}


	@SuppressLint("ShowToast")
	private void ReadFromTag(Intent intent)
	{
		try{
			Ndef ndef =Ndef.get(mytag);
			ndef.connect();
			Parcelable[] messages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);

			if (messages != null) {
				NdefMessage[] ndefMessages = new NdefMessage[messages.length];

				for (int i = 0; i < messages.length; i++) {
					ndefMessages[i] = (NdefMessage) messages[i];
				}

				NdefRecord record = ndefMessages[0].getRecords()[0];
				//Send a message to the UI
				
				byte[] payload = record.getPayload();
				String text = new String(payload);
				tagInfoCollected();
				stringFromTagUpdate(text.trim());
				ndef.close();
			}
		}
		catch(Exception e)
		{
			unableToReadTag();
		}
	}

	/////////////////////////////////////////////////////////////////////////////
	//
	//
	//				These are the UI Messages which can all be overwrote
	//
	//
	//////////////////////////////////////////////////////////////////////////////

	//Message sent when NFC is unactivated
	protected void sendUnactivatedMessageToTheUI()
	{
		Toast.makeText(getApplicationContext(), "Please activate NFC and press back to return to the application!", Toast.LENGTH_LONG).show();
	}
	//Diverts the user to the NFC settings
	protected void openNFCSettings()
	{
		startActivity(new Intent(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
	}

	//Message when a tag is discovered
	protected void tagDiscovered()
	{
		Toast.makeText(this, "Tag Discovered", Toast.LENGTH_SHORT ).show();
	}
	//Message when a tag is Discovered but Write mode was disabled
	protected void readModeDisabled()
	{
		//Toast.makeText(this, "Write Mode is Disabled", Toast.LENGTH_SHORT ).show();
	}
	//Called if nfc is not supported
	
	//Called if the tag connection is either broken or cannot be read
	protected void unableToReadTag()
	{
		//Toast.makeText(ctx, "Sorry an Error occured while attempting to read tag please try again", Toast.LENGTH_LONG).show();
	}
	protected void tagInfoCollected()
	{
		//Toast.makeText(this, "Information Collected", Toast.LENGTH_LONG ).show();
	}
	
	protected void nfcNotSupported()
	{
		Toast.makeText(getApplicationContext(), "Your device does not support NFC", Toast.LENGTH_LONG).show();
	}
	

}
