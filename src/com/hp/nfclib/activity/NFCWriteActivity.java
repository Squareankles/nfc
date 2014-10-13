package com.hp.nfclib.activity;

import java.io.UnsupportedEncodingException;



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
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.widget.Toast;

public abstract class NFCWriteActivity extends Activity {


	private NfcAdapter adapter;
	private PendingIntent pendingIntent;
	private IntentFilter writeTagFilters[];
	private Tag mytag;
	private Context ctx;
	private Activity callingActivity;

	//true if the user can write
	protected boolean isWriteModeEnabled;

	//Abstract Method that when implemented returns the string to write to the tag
	protected abstract String getTextToWrite();


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
			
			isWriteModeEnabled=true;
		}
		else
		{
			nfcNotSupported();
			isWriteModeEnabled=false;
		}
	}	


	//Passed in the parameter of type Intent which the Tag kicks off thus has a tag object
	@Override
	protected void onNewIntent(Intent intent){
		// Check to see if the Tag Discovered from the NfcAdapter kicked off the intent
		if(NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())){
			tagDiscovered();
			//Get's the Tag from the Intent
			if(isWriteModeEnabled)
			{
			mytag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);    
			WriteToTag();
			}
			else
			{
				writeModeDisabled();
			}
		}
	}

	@Override
	protected void onPause(){
		super.onPause();
		//Turn's off the ability to detect tags if the Inherited activity sleeps
		writeModeOff();
	}

	@Override
	protected void onResume(){
		super.onResume();
		//Resumes the ability to detect tags
		writeModeOn();
	}
	/////////////////////////////////////////////////////////////////////////////////
	//
	//This is where the tag is written to uses 3 helper method createRecord(), WriteMessageToFormattedTag(), WriteToUnformattedTag() ;
	//
	/////////////////////////////////////////////////////////////////////////////////

	private void WriteToTag(){

		attemptToWriteToTag();

		try{
			boolean formattedTagWasWrittenTo=true;
			// Get the string from the method the user must implement 
			String textToWrite=getTextToWrite();
			//Return the ndef record from the create Record method add it to the array of records
			NdefRecord[] records = { createRecord(textToWrite.toString()) };
			//A message will be created from the record array
			NdefMessage  message = new NdefMessage(records);
			//Operation will write the message to the tag if the tag is not already formatted
			boolean wasTagFormatted=writeToUnformattedTag(message);
			//Check if the operation was successful, it is unsuccessful if the tag was previously written to
			if(!wasTagFormatted)
			{
				//Attempt to write to a tag which was previously formatted
				formattedTagWasWrittenTo=writeMessageToFormattedTag(message);
			}


			if(formattedTagWasWrittenTo||wasTagFormatted)
			{
				tagWriteSuccess();
			}
			else
			{
				tagWriteFail();
			}
		}
		catch(Exception e)
		{
			//Send a message to the UI
			tagWriteFail();
		}


	}

	private boolean writeToUnformattedTag(NdefMessage  message)
	{
		try{
			//Get the tag object will fail if the tag is already formatted
			NdefFormatable mNdefFormatable=NdefFormatable.get(mytag);
			mNdefFormatable.connect();
			//Format the tag and imprint the message
			mNdefFormatable.format(message);
			mNdefFormatable.close();
			return true;
		}
		catch(Exception e)
		{
			return false;
		}
	}

	private boolean writeMessageToFormattedTag(NdefMessage message)
	{
		try{
			//Get the Tag Object
			Ndef mNdef=Ndef.get(mytag);
			mNdef.connect();
			//Write the Message
			mNdef.writeNdefMessage(message);
			mNdef.close();
			return true;
		}
		catch(Exception e)
		{
			return false;
		}
	}

	private void writeModeOn(){

		try
		{
			//This Line Sets up the Listener in the class
			adapter.enableForegroundDispatch(callingActivity, pendingIntent, writeTagFilters, null);
		}
		catch(Exception e)
		{
			sendUnactivatedMessageToTheUI();
		}
	}

	private void writeModeOff(){
		//Switches off the Listener
		adapter.disableForegroundDispatch(callingActivity);
	}

	//This Method is used to create an NDF Record which is Implanted in the tag
	private NdefRecord createRecord(String text) throws UnsupportedEncodingException {
		String lang       = "en";
		byte[] textBytes  = text.getBytes();
		byte[] langBytes  = lang.getBytes("US-ASCII");
		int    langLength = langBytes.length;
		int    textLength = textBytes.length;
		byte[] payload    = new byte[1 + langLength + textLength];

		// set status byte (see NDEF spec for actual bits)
		payload[0] = (byte) langLength;

		// copy langbytes and textbytes into payload
		//System.arraycopy(langBytes, 0, payload, 1,langLength);
		System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength);
		NdefRecord recordNFC = new NdefRecord(NdefRecord.TNF_WELL_KNOWN,  NdefRecord.RTD_TEXT,  new byte[0], payload);
		return recordNFC;
	}

	//////////////////////////////////////////////////////////////////////////////
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
	protected void tagDiscovered(){
		Toast.makeText(this, "Tag Discovered", Toast.LENGTH_SHORT ).show();
	}
	//Message when a tag is Discovered but Write mode was disabled
	protected void writeModeDisabled()
	{
		//Toast.makeText(this, "Write Mode is Disabled", Toast.LENGTH_SHORT ).show();
	}
	//Message when the tag is been written to
	protected void attemptToWriteToTag()
	{
		//Toast.makeText(this, "Attempting to Write to Tag", Toast.LENGTH_SHORT ).show();
	}
	//Message when tag was successfully written to
	protected void tagWriteSuccess()
	{
		//Toast.makeText(this, "Tag Write Successful", Toast.LENGTH_LONG ).show();
	}
	//Message when tag was not written to
	protected void tagWriteFail()
	{
		//Toast.makeText(this, "Tag Write Unsuccessful. Try Again", Toast.LENGTH_LONG ).show();
	}
	protected void nfcNotSupported()
	{
		Toast.makeText(getApplicationContext(), "Your device does not support NFC", Toast.LENGTH_LONG).show();
	}





}
