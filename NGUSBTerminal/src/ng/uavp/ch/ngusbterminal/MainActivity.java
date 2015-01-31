/*
 * NGUSBTerminal - The Next Generation Multicopter Android Terminal
 * Copyright (C) 2015 by the UAVP-NG Project,
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

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.support.v7.app.ActionBarActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.ftdi.j2xx.D2xxManager;

public class MainActivity extends ActionBarActivity {

	UsbSerialComm usb = null;
	ShellFragment shell = null;
	SettingsFragment settings = null;

    public interface ISerialComm {
    	public void addReceiveEventHandler(Handler mHandler);
    	public int sendBytes(byte[] data);
    	public int sendText(CharSequence text);
    }

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_terminal);

		usb = new UsbSerialComm(this);

		if(OpenUsb())
			SelectShell();
		else
			SelectSettings();
	}
	
	public boolean OpenUsb() {
		String[] devList;
		try {
			devList = usb.createDeviceList();
		} catch (D2xxManager.D2xxException e) {
			showToast(e.getLocalizedMessage(), Toast.LENGTH_SHORT);
			return false;
		}

		if (devList.length == 0) {
			return false;
		}
		
		usb.closeDevice();

		UsbSerialComm.UartSettings uart = new UsbSerialComm.UartSettings();

		SharedPreferences sharedPref = getSharedPreferences("uart_settings",
				Context.MODE_PRIVATE);

		String interfce = sharedPref.getString("interface", "X");
		uart.baudrate = sharedPref.getInt("baudrate", uart.baudrate);
		uart.dataBits = (byte) sharedPref.getInt("databits", uart.dataBits);
		uart.parity = (byte) sharedPref.getInt("parity", uart.parity);
		uart.stopBits = (byte) sharedPref.getInt("stopbits", uart.stopBits);
		uart.flowControl = (byte) sharedPref.getInt("flowcontrol", uart.flowControl);

		for (int i = 0; i < devList.length; i++) {
			// terminalView.setSelection(terminalView.getText().length());
			if (devList[i].equals(interfce)) {
				if(usb.openDevice(i, uart)) {	
					//shell.setText(getString(R.string.connected_to) + " " + devList[i] + "\n\n");
					return true;
				}
			}
		}
		
		return false;
	}
	
	public void SelectShell() {	
		if(shell == null) {
			shell = new ShellFragment(usb);
		}
		getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, shell).commit();	
	}

	public void SelectSettings() {	
		if(settings == null) {
			settings = new SettingsFragment();
		}
		getSupportFragmentManager().beginTransaction()
            	.replace(R.id.fragment_container, settings).commit();		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.terminal, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch (id) {
		case R.id.menu_settings: {
			SelectSettings();
			return true;
		}

		case R.id.menu_exit: {
			finish();
			return true;
		}

		case R.id.menu_readfile: {
			readFile();
			return true;
		}

		case R.id.menu_writefile: {
			writeFile();
			return true;
		}

		case R.id.menu_clear: {
			if(shell != null) {
				ShellFragment.TerminalEditText tet = (ShellFragment.TerminalEditText) 
						findViewById(R.id.editText1);
				tet.setText("");
			}
			return true;
		}
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	protected void onStop() {
		usb.closeDevice();
		super.onStop();
	}

	/* -------------------------- File Operations -------------------------- */

	boolean logging = false;

	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)) {
			return true;
		}
		return false;
	}

	/* Checks if external storage is available to at least read */
	public boolean isExternalStorageReadable() {
		String state = Environment.getExternalStorageState();
		if (Environment.MEDIA_MOUNTED.equals(state)
				|| Environment.MEDIA_MOUNTED_READ_ONLY.equals(state)) {
			return true;
		}
		return false;
	}

	protected void readFile() {
		String fragTag = getResources().getString(R.string.action_readfile);


	}

	protected void writeFile() {
		String filter = getResources().getString(R.string.file_extension_txt);
		String fragTag = getResources().getString(R.string.action_start_log);


	}

	// Act on a validated [positive] button click or a [negative] button
	// click. On [negative] click path and name are both null.
	public void onConfirmSave(String absolutePath, String fileName) {
		if (absolutePath != null && fileName != null) {
			// Recommend that file save for large amounts of data is handled
			// by an AsyncTask.
			// mySaveMethod(absolutePath, fileName);
		}
	}


	private void showToast(int stringRessource, int lengthShort) {
		Toast toast = Toast.makeText(this, getString(stringRessource), lengthShort);
		toast.show();	
	}

	private void showToast(String text, int lengthShort) {
		Toast toast = Toast.makeText(this, text, lengthShort);
		toast.show();	
	}
	
}
