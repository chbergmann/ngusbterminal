/*
 * NGUSBTerminal - The Next Generation Multicopter Android Terminal
 * Copyright (C) 2007 - 2015 by the UAVP-NG Project,
 *     Christian Bergmann <christi@dev.uavp.ch>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
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
 * You can find our website at <http://ng.uavp.ch>.
 *
 * Many people helped and are helping developing NGOS. Please
 * have a look at <http://ng.uavp.ch/moin/Authors> for details.
 */

package ng.uavp.ch.ngusbterminal;


import ng.uavp.ch.ngusbterminal.UsbSerialComm.UartSettings;

import com.ftdi.j2xx.D2xxManager;

import android.support.v7.app.ActionBarActivity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;

public class SettingsActivity extends ActionBarActivity {

	UsbSerialComm usb;
	int usbDevicesFound = 0;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);
		
	    if (getIntent().getBooleanExtra("EXIT", false)) {
	         finish();
	    }	

		SharedPreferences sharedPref = getSharedPreferences("uart_settings", 
				Context.MODE_PRIVATE);
		
		UartSettings defaults = new UartSettings();
	    
	    Spinner spinner1 = (Spinner) findViewById(R.id.spinnerBaud);
	    // Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter1 = ArrayAdapter.createFromResource(this,
		         R.array.baud_array, R.layout.spinner_item_settings);
		// Specify the layout to use when the list of choices appears
		adapter1.setDropDownViewResource(R.layout.spinner_item_settings);
		// Apply the adapter to the spinner
		spinner1.setAdapter(adapter1);
		SetSpinnerSelection(sharedPref, spinner1, "baudrate", defaults.baudrate);
		

	    Spinner spinner2 = (Spinner) findViewById(R.id.spinnerBits);
	    // Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter2 = ArrayAdapter.createFromResource(this,
		         R.array.databits_array, R.layout.spinner_item_settings);
		// Specify the layout to use when the list of choices appears
		adapter2.setDropDownViewResource(R.layout.spinner_item_settings);
		// Apply the adapter to the spinner
		spinner2.setAdapter(adapter2);
		SetSpinnerSelection(sharedPref, spinner2, "databits", defaults.dataBits);
		

	    Spinner spinner3 = (Spinner) findViewById(R.id.spinnerParity);
	    // Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter3 = ArrayAdapter.createFromResource(this,
		         R.array.parity_array, R.layout.spinner_item_settings);
		// Specify the layout to use when the list of choices appears
		adapter3.setDropDownViewResource(R.layout.spinner_item_settings);
		// Apply the adapter to the spinner
		spinner3.setAdapter(adapter3);
		spinner3.setSelection(sharedPref.getInt("parity", defaults.parity));

	    Spinner spinner4 = (Spinner) findViewById(R.id.spinnerStopbits);
	    // Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter4 = ArrayAdapter.createFromResource(this,
		         R.array.stopbits_array, R.layout.spinner_item_settings);
		// Specify the layout to use when the list of choices appears
		adapter4.setDropDownViewResource(R.layout.spinner_item_settings);
		// Apply the adapter to the spinner
		spinner4.setAdapter(adapter4);
		SetSpinnerSelection(sharedPref, spinner4, "stopbits", defaults.stopBits);

	    Spinner spinner5 = (Spinner) findViewById(R.id.spinnerFlow);
	    // Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<CharSequence> adapter5 = ArrayAdapter.createFromResource(this,
		         R.array.flowcontrol, R.layout.spinner_item_settings);
		// Specify the layout to use when the list of choices appears
		adapter5.setDropDownViewResource(R.layout.spinner_item_settings);
		// Apply the adapter to the spinner
		spinner5.setAdapter(adapter5);
		spinner5.setSelection(sharedPref.getInt("flowcontrol", defaults.flowControl));
		
		usb = new UsbSerialComm(this);
		ScanAdapter();
	}

	private void SetSpinnerSelection(SharedPreferences sharedPref, Spinner spinner, String key, int defValue) {
		
		String str = String.valueOf(sharedPref.getInt(key, defValue));
		
		for(int i=0; i<spinner.getAdapter().getCount(); i++)
		{
			String s = spinner.getItemAtPosition(i).toString();
			if(s.equals(str)) {
				spinner.setSelection(i);
				break;
			}
		}
	}
	
	void ScanAdapter()
	{
		String[] devList;
		try
		{
			devList = usb.createDeviceList();
			usbDevicesFound = devList.length;
		}
		catch (D2xxManager.D2xxException e) {
			devList = new String[1];
			devList[0] = e.getLocalizedMessage();
		}
		
		if(devList.length == 0) {
			devList = new String[1];
			devList[0] = getString(R.string.error_noAdapter);
		}
		
	    Spinner spinner5 = (Spinner) findViewById(R.id.spinnerInterfce);
	    
	    ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<String>(this, 
	    		R.layout.spinner_item_settings, devList); 
	    spinnerArrayAdapter.setDropDownViewResource(R.layout.spinner_item_settings);
	    spinner5.setAdapter(spinnerArrayAdapter);
	    
	    Button button1 = (Button) findViewById(R.id.button1);
	    button1.setEnabled(usbDevicesFound > 0);
	    
		if(usbDevicesFound == 0)
			spinner5.setBackgroundColor(Color.MAGENTA);
		else
			spinner5.setBackgroundColor(Color.TRANSPARENT);
	}
 
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		return super.onOptionsItemSelected(item);
	}

	public void doConnect(View view) {
		SharedPreferences sharedPref = getSharedPreferences("uart_settings", 
				Context.MODE_PRIVATE);
		
		SharedPreferences.Editor editor = sharedPref.edit();
		
		Spinner spinner1 = (Spinner) findViewById(R.id.spinnerInterfce);
		editor.putString("interface", spinner1.getSelectedItem().toString());

		Spinner spinner2 = (Spinner) findViewById(R.id.spinnerBaud);
		String baud = spinner2.getSelectedItem().toString();
		editor.putInt("baudrate", Integer.parseInt(baud));

		Spinner spinner3 = (Spinner) findViewById(R.id.spinnerBits);
		String bits = spinner3.getSelectedItem().toString();
		editor.putInt("databits", Integer.parseInt(bits));

		Spinner spinner4 = (Spinner) findViewById(R.id.spinnerParity);
		editor.putInt("parity", spinner4.getSelectedItemPosition());

		Spinner spinner5 = (Spinner) findViewById(R.id.spinnerStopbits);
		String stop = spinner5.getSelectedItem().toString();
		editor.putInt("stop", Integer.parseInt(stop));

		Spinner spinner6 = (Spinner) findViewById(R.id.spinnerFlow);
		editor.putInt("flowcontrol", spinner6.getSelectedItemPosition());
		
		editor.commit();
		
		Intent intent = new Intent(this, TerminalActivity.class);
		startActivity(intent);
	}

}
