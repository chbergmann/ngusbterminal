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

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.support.v7.app.ActionBarActivity;
import android.text.Editable;
import android.text.Selection;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;

import com.ftdi.j2xx.D2xxManager;

public class TerminalActivity extends ActionBarActivity {

	UsbSerialComm usb;
	static final String NEWLINE = "\n\r";
	
	public static class TerminalEditText extends TextView {    
		UsbSerialComm usb;
		int escseq = 0;
		int cursorLeft = 0;
		TerminalInputConnection inputConnection;
		
		public TerminalEditText(Context context) {
			this(context, null);
		}
		
		public TerminalEditText(Context context, AttributeSet attrs) {
			super(context, attrs);
	        setFocusableInTouchMode(true); 
		}
		
		public TerminalEditText(Context context, AttributeSet attrs, int defStyle) {
		    super(context, attrs, defStyle);
		}

	    @Override
	    protected MovementMethod getDefaultMovementMethod() {
	        return ArrowKeyMovementMethod.getInstance();
	    }
	    
	    @Override
	    public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
	        return new TerminalInputConnection(super.onCreateInputConnection(outAttrs),
	                true);
	    }

	    @Override
	    public Editable getText() {
	        return (Editable) super.getText();
	    }
	    
	    public void setSelection(int pos) {
	        Selection.setSelection(getText(), pos);
	    }
	    
	    private class TerminalInputConnection extends InputConnectionWrapper {

	        public TerminalInputConnection(InputConnection target, boolean mutable) {
	            super(target, mutable);
	            inputConnection = this;
	        }

	        // capture normal keys
	        @Override	       
	        public boolean commitText(CharSequence text, int newCursorPosition) {       	
	        	usb.sendText(text);
	            return false;
	        }

	        // capture special keys
	        @Override
	        public boolean sendKeyEvent(KeyEvent event) {    
	        	if(event.getAction() == KeyEvent.ACTION_DOWN) {
		        	switch(event.getKeyCode()) {
			        	case KeyEvent.KEYCODE_ENTER:
			        		usb.sendText(NEWLINE);
			        		break;
			        		
			        	case KeyEvent.KEYCODE_DEL:
			        		usb.sendText(String.valueOf((char)0x08));
			        		break;

			            case KeyEvent.KEYCODE_DPAD_UP:
				        	usb.sendText(String.valueOf((char)0x1b));
				        	usb.sendText(String.valueOf('['));
				        	usb.sendText(String.valueOf('A'));
				         	break;

			            case KeyEvent.KEYCODE_DPAD_DOWN:
			            	usb.sendText(String.valueOf(0x1b));
			            	usb.sendText(String.valueOf('['));
			            	usb.sendText(String.valueOf('B'));
			            	break;

			            case KeyEvent.KEYCODE_DPAD_RIGHT:
			            	if(cursorLeft > 0)
			            		cursorLeft--;
			            	
			            	usb.sendText(String.valueOf((char)0x1b));
			            	usb.sendText(String.valueOf('['));
			            	usb.sendText(String.valueOf('C'));
			            	break;

			            case KeyEvent.KEYCODE_DPAD_LEFT:
			            	if(cursorLeft < length());
			            		cursorLeft++;
			            		
			            	usb.sendText(String.valueOf((char)0x1b));
			            	usb.sendText(String.valueOf('['));
			            	usb.sendText(String.valueOf('D'));
			            	break;
			            	
		        		default:	        			
		        			return true;
		        	}
	        	}
	        	else {
        			return true;
	        	}
	            return false;
	        }
	        
	        public void deleteChar() {
	        	super.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_DEL));
	        }
	    }
	    
	    public void HookUsbDevice(UsbSerialComm serial) {
	    	usb = serial;
	    	Handler mHandler = new Handler(Looper.getMainLooper()) {		
	    		@Override
	            public void handleMessage(Message inputMessage) {
	    			byte[] receivedData = (byte[]) inputMessage.obj;
	    			OnReceived(receivedData);
	    		}
	        };

	    	usb.addReceiveEventHandler(mHandler);
	    }

        @Override
	    public void setText(CharSequence text, BufferType type) {
	        super.setText(text, type);
	        setSelection(text.length());
	    }
    	
        int npos = 0;
        int rpos = -1;
        
    	public int OnReceived(byte[] data) {
    		StringBuilder str = new StringBuilder();
			for(int i=0; i<data.length; i++) {
				switch(data[i]) {  
				case 0x1b: 	// ESC sequence
					escseq = 1;
					break;
					
				case '\n':
			    	npos = length() + str.length();
			    	rpos = -1;
			    	str.append((char)data[i]);
			    	cursorLeft = 0;
					break;

				case '\r':
			    	rpos = length() + str.length();
					break;
				
				default:
				    if(escseq > 0) {
				    	escseq++;
				    	if(escseq == 3) {
				    		if(data[i] == 'D')
				    			inputConnection.deleteChar();
				    		
				    		escseq = 0;
				    	}
				    }
				    else if (rpos >= 0 && rpos < length()) {
				    	setText(getText().subSequence(0, rpos));
				    }
				    else {
				    	str.append((char)data[i]);
				    }
				    break;
				}		    	
			}
							
			if(str.length() > 0) {
				append(str.toString());
				setSelection(length() - cursorLeft);
			}
			return 0;
    	}
	}
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {	    
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_terminal);
		
		usb = new UsbSerialComm(this);
		TerminalEditText terminalView = (TerminalEditText)findViewById(R.id.editText1);

		String[] devList;
		try {
			devList = usb.createDeviceList();
		}
		catch (D2xxManager.D2xxException e) {
			terminalView.setText(e.getLocalizedMessage());
			return;
		}
		
		if(devList.length == 0) {
			/* open Settings dialog if not connected */
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return;
		}

		UsbSerialComm.UartSettings uart = new UsbSerialComm.UartSettings();

		SharedPreferences sharedPref = getSharedPreferences("uart_settings", 
				Context.MODE_PRIVATE);
		
		String interfce = sharedPref.getString("interface", "X");
		uart.baudrate = sharedPref.getInt("baudrate", uart.baudrate);
		uart.dataBits = (byte)sharedPref.getInt("databits", uart.dataBits);
		uart.parity = (byte)sharedPref.getInt("parity", uart.parity);
		uart.stopBits = (byte)sharedPref.getInt("stopbits", uart.stopBits);
		uart.flowControl = (byte)sharedPref.getInt("flowcontrol", uart.flowControl);
		
		for(int i=0; i<devList.length; i++) {
			//terminalView.setSelection(terminalView.getText().length());
			if(devList[i].equals(interfce)) {
				usb.openDevice(i, uart);
				terminalView.setText(getString(R.string.connected_to) + " " + devList[i] + "\n\n");
				terminalView.HookUsbDevice(usb);
				
				    if (terminalView.requestFocus()) {
				        InputMethodManager imm = (InputMethodManager)
				                getSystemService(Context.INPUT_METHOD_SERVICE);
				        imm.showSoftInput(terminalView, InputMethodManager.SHOW_IMPLICIT);
				    }
				
				return;
			}
		}
		
		/* open Settings dialog if not connected to the selected device */
		Intent intent = new Intent(this, SettingsActivity.class);
		startActivity(intent);
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
		if (id == R.id.action_settings) {
			Intent intent = new Intent(this, SettingsActivity.class);
			startActivity(intent);
			return true;
		}

		if (id == R.id.action_exit) {
			Intent intent = new Intent(getApplicationContext(), SettingsActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			intent.putExtra("EXIT", true);
			startActivity(intent);
			
			finish();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	protected void onStop() {
	    usb.closeDevice();
	    super.onStop();  
	}
}
