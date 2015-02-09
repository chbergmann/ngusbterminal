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

import android.support.v4.app.Fragment;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.text.Editable;
import android.text.Selection;
import android.text.method.ArrowKeyMovementMethod;
import android.text.method.MovementMethod;
import android.util.AttributeSet;
import android.view.KeyCharacterMap;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import ng.uavp.ch.ngusbterminal.MainActivity.ISerialComm;

public class ShellFragment extends Fragment {
	TerminalEditText terminalView;
	ISerialComm serial;
	
	public ShellFragment(ISerialComm serial) {
		super();
		this.serial = serial;
	}
	
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.shell, container, false);

		terminalView = (TerminalEditText) view.findViewById(R.id.editText1);
		terminalView.HookSerialDevice(serial);
		
		if (terminalView.requestFocus()) {
			InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.showSoftInput(terminalView,
					InputMethodManager.SHOW_IMPLICIT);
		}
		
		return view;
    }
    
    public void setText(String text) {
    	terminalView.setText(text);
    }
    
    public void HookSerialDevice(ISerialComm serial) {
    	terminalView.HookSerialDevice(serial);
    }

	public static class TerminalEditText extends TextView {
		ISerialComm usb;
		int escseq = 0;
		int cursorLeft = 0;
		TerminalInputConnection inputConnection;
		static final String NEWLINE = "\n\r";

		public TerminalEditText(Context context) {
			this(context, null);
		}

		public TerminalEditText(Context context, AttributeSet attrs) {
			super(context, attrs);
			setFocusableInTouchMode(true);
		}

		public TerminalEditText(Context context, AttributeSet attrs,
				int defStyle) {
			super(context, attrs, defStyle);
		}

		@Override
		protected MovementMethod getDefaultMovementMethod() {
			return ArrowKeyMovementMethod.getInstance();
		}

		@Override
		public InputConnection onCreateInputConnection(EditorInfo outAttrs) {
			return new TerminalInputConnection(
					super.onCreateInputConnection(outAttrs), true);
		}

		@Override
		public Editable getText() {
			return (Editable) super.getText();
		}

		public void setSelection(int pos) {
			Selection.setSelection(getText(), pos);
		}

		public void setSelection(int start, int end) {
			Selection.setSelection(getText(), start, end);
		}
		
		private class TerminalInputConnection extends InputConnectionWrapper {

			public TerminalInputConnection(InputConnection target,
					boolean mutable) {
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
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					switch (event.getKeyCode()) {
					case KeyEvent.KEYCODE_ENTER:
						usb.sendText(NEWLINE);
						break;

					case KeyEvent.KEYCODE_DEL:
						usb.sendText(String.valueOf((char) 0x08));
						break;

					case KeyEvent.KEYCODE_DPAD_UP:
						usb.sendText(String.valueOf((char) 0x1b));
						usb.sendText(String.valueOf('['));
						usb.sendText(String.valueOf('A'));
						break;

					case KeyEvent.KEYCODE_DPAD_DOWN:
						usb.sendText(String.valueOf(0x1b));
						usb.sendText(String.valueOf('['));
						usb.sendText(String.valueOf('B'));
						break;

					case KeyEvent.KEYCODE_DPAD_RIGHT:
						usb.sendText(String.valueOf((char) 0x1b));
						usb.sendText(String.valueOf('['));
						usb.sendText(String.valueOf('C'));
						break;

					case KeyEvent.KEYCODE_DPAD_LEFT:
						usb.sendText(String.valueOf((char) 0x1b));
						usb.sendText(String.valueOf('['));
						usb.sendText(String.valueOf('D'));
						break;

					default:
						return true;
					}
				} else {
					return true;
				}
				return false;
			}

			public void sendKeyToView(int keycode) {
				super.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, keycode));
			}
			
			public void sendKeyEvents(String text) {
		        KeyCharacterMap mKeyCharacterMap = 
		            KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD);

		        KeyEvent[] events = mKeyCharacterMap.getEvents(text.toCharArray());

		        for (KeyEvent event2 : events) {
		            super.sendKeyEvent(new KeyEvent(event2.getAction(), event2.getKeyCode()));          
		        }
			}
		}

		public void HookSerialDevice(ISerialComm serial) {
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
		boolean got_r = false;

		public void OnReceived(byte[] data) {
			StringBuilder str = new StringBuilder();
			for (int i = 0; i < data.length; i++) {
				switch (data[i]) {
				case 0x1b: // ESC sequence
					escseq = 1;
					break;

				case '\n':
					str.append((char) data[i]);
					npos = length() + str.length();
					got_r = false;
					for(; cursorLeft > 0; cursorLeft--)
						inputConnection.sendKeyToView(KeyEvent.KEYCODE_DPAD_RIGHT); 

					break;

				case '\r':
					got_r = true;
					break;

				default:
					if (escseq > 0) {
						escseq++;
						if (escseq == 3) {
							if (data[i] == 'D') {
								cursorLeft++;	
								inputConnection.sendKeyEvents(str.toString());
								str.delete(0, str.length());
								inputConnection.sendKeyToView(KeyEvent.KEYCODE_DPAD_LEFT);
							}
							if (data[i] == 'C' && cursorLeft > 0) {
								cursorLeft--;		
								inputConnection.sendKeyEvents(str.toString());
								str.delete(0, str.length());
								inputConnection.sendKeyToView(KeyEvent.KEYCODE_DPAD_RIGHT); 
							}
							escseq = 0;
						}
					} else if (got_r) {
						int strstart = npos - length();
						if (strstart >= 0) {
							if(strstart < str.length() - 1)
								str.delete(strstart, str.length() - 1);
						} else {
							str.delete(0, str.length());

							if (npos < length())
								// setText(getText().subSequence(0, npos));
								str.append("\n"); // append Text because
													// replacing is too slow
						}
						got_r = false;
						str.append((char) data[i]);
					} else {
						for(;cursorLeft > 0; cursorLeft--) {							
							if(str.length() > 0) 
								str.deleteCharAt(str.length()-1);
							else {
								inputConnection.sendKeyToView(KeyEvent.KEYCODE_FORWARD_DEL);
							}
						}
						str.append((char) data[i]);
					}
					break;
				}
			}

			if(cursorLeft == 0) {
				// sending single key events is too slow, so append text if possible
				append(str.toString());
				setSelection(length());	
			}
			else {
				// we have to send single key events for cursor key handling 
				inputConnection.sendKeyEvents(str.toString());
			}		
		}
	}
}
