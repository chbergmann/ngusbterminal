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

import java.util.ArrayList;
import java.util.List;

import ng.uavp.ch.ngusbterminal.MainActivity.ISerialReceive;
import ng.uavp.ch.ngusbterminal.MainActivity.ISerialSend;

import android.content.Context;

import com.ftdi.j2xx.D2xxManager;
import com.ftdi.j2xx.D2xxManager.FtDeviceInfoListNode;
import com.ftdi.j2xx.FT_Device;

public class UsbSerialComm implements ISerialSend {
	private static D2xxManager ftD2xx = null;
	private FT_Device ftDev = null;
	static Context global_context;
	boolean bReadTheadEnable = false;
	boolean bSoftwareFlowControl = false;
	WriteThread writeThread = null;

    private List<ISerialReceive> receiveHandlerList = new ArrayList<ISerialReceive>();
    
    // Method for listener classes to register themselves
    public void addReceiveEventHandler(ISerialReceive callback)
    {
    	receiveHandlerList.add(callback);
    }

    public void removeReceiveEventHandler(ISerialReceive callback)
    {
    	receiveHandlerList.remove(callback);
    }
	
    final byte XON = 0x11;    /* Resume transmission */
    final byte XOFF = 0x13;    /* Pause transmission */

    
    /* UART configuration with default settings */
	public static class UartSettings {
		public int  baudrate = 115200;
		public byte dataBits = 8;
		public byte stopBits = 1;
		public byte parity = 0;
		public byte flowControl = 0;
	};
	
	public UsbSerialComm(Context context) {
		global_context = context;
	}
	
	public String[] createDeviceList() throws D2xxManager.D2xxException
	{
	   	ftD2xx = D2xxManager.getInstance(global_context);
	   	
		int DevCount = ftD2xx.createDeviceInfoList(global_context);
		FtDeviceInfoListNode[] devList = new FtDeviceInfoListNode[DevCount];
		int DevCount2 = ftD2xx.getDeviceInfoList(DevCount, devList);
		
		String[] devStr = new String[DevCount2];
		for(int i=0; i<DevCount2; i++)
			devStr[i] = devList[i].description;
		
		return devStr;
	}
	
	public boolean openDevice(int index, UartSettings settings) {
		ftDev = ftD2xx.openByIndex(global_context, index);
		if(ftDev == null)
			return false;

		// configure port
		// reset to UART mode for 232 devices
		ftDev.setBitMode((byte) 0, D2xxManager.FT_BITMODE_RESET);

		ftDev.setBaudRate(settings.baudrate);
		
		byte parity;
		switch (settings.parity)
		{
		case 0:
			parity = D2xxManager.FT_PARITY_NONE;
			break;
		case 1:
			parity = D2xxManager.FT_PARITY_ODD;
			break;
		case 2:
			parity = D2xxManager.FT_PARITY_EVEN;
			break;
		case 3:
			parity = D2xxManager.FT_PARITY_MARK;
			break;
		case 4:
			parity = D2xxManager.FT_PARITY_SPACE;
			break;
		default:
			parity = D2xxManager.FT_PARITY_NONE;
			break;
		}

		ftDev.setDataCharacteristics(settings.dataBits, settings.stopBits, parity);

		short flowCtrlSetting;
		bSoftwareFlowControl = false;
		switch (settings.flowControl)
		{
		case 0:
			flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
			break;
		case 1:
			flowCtrlSetting = D2xxManager.FT_FLOW_RTS_CTS;
			break;
		case 2:
			flowCtrlSetting = D2xxManager.FT_FLOW_DTR_DSR;
			break;
		case 3:
			flowCtrlSetting = D2xxManager.FT_FLOW_XON_XOFF;
			break;
		default:
			bSoftwareFlowControl = true;
			flowCtrlSetting = D2xxManager.FT_FLOW_NONE;
			break;
		}

		ftDev.setFlowControl(flowCtrlSetting, XON, XOFF);
		
		bReadTheadEnable = true;
		ReadThread rthread = new ReadThread();
		rthread.start();
		
		writeThread = new WriteThread();
		writeThread.start();
		return true;
	}
	
	public boolean isOpen() {
		if(ftDev == null)
			return false;
		
		return ftDev.isOpen();
	}
	
	public void closeDevice() {
		bReadTheadEnable = false;
		if(writeThread != null)
			writeThread.Stop();
		
		if(ftDev == null)
			return;
		
		ftDev.close();
	}

	@Override
	public int sendBytes(byte[] data) {
		if(ftDev == null || !ftDev.isOpen())
			return -1;
		
		return writeThread.queueWrite(data);
	}

	@Override
	public int sendText(CharSequence text) {		
		byte[] ascii = new byte[text.length()];
		for(int i=0; i<text.length(); i++)
			ascii[i] = (byte)text.charAt(i);
		
		return sendBytes(ascii);
	}
	
	int bytes_sent = 0;
	
	class ReadThread extends Thread
	{
		public void run() {
			while (true == bReadTheadEnable) 
			{
				int readcount = ftDev.getQueueStatus();
				if (readcount > 0) {
					byte readbuffer[] = new byte[readcount];
					
					ftDev.read(readbuffer, readcount);
					
					for(ISerialReceive callback : receiveHandlerList) {
						callback.OnReceive(readbuffer);
					}
					
					if(bytes_sent - readcount <= 0)
						bytes_sent = 0;
					else
						bytes_sent -= readcount;
				}
				
				try {
					Thread.sleep(50);
				}
				catch (InterruptedException e) {
				}		
			}
		}
	}

	class WriteThread extends Thread {
		private final int SEND_MAX_BYTES = 127;
	    private byte[] writebuffer = new byte[8192];
	    private int rdptr;
	    private int wrptr;
	    public boolean threadEnable;
	    
	    public WriteThread() {
	    	threadEnable = true;
	    	bytes_sent = 0;
	    	rdptr = 0;
	    	wrptr = 0;
	    }
	    
		public void run() {
			byte[] toSend = new byte[1];
			while (true == threadEnable) 
			{
				synchronized(writebuffer) {
					try {
						writebuffer.wait();
					} catch (InterruptedException e1) {
						break;
					}
				}
				
				while(rdptr != wrptr) {					
					toSend[0] = writebuffer[rdptr];
					rdptr = ((rdptr+1) % writebuffer.length);
					ftDev.write(toSend);
					
					bytes_sent++;
					if(bSoftwareFlowControl) {
						while(bytes_sent >= SEND_MAX_BYTES) {
							try {
								Thread.sleep(50);
							}
							catch (InterruptedException e) {
							}							
						}
					}
				}				
			}
		}
		
		public int queueWrite(byte[] data) {
			for(int i=0; i<data.length; i++) {
				int new_wrptr = ((wrptr+1) % writebuffer.length);
				if(new_wrptr == rdptr)
					return i;
				
				writebuffer[wrptr] = data[i];
				wrptr = new_wrptr;
			}

			synchronized(writebuffer) {
				writebuffer.notifyAll();
			}
			return data.length;
		}
		
		public void Stop() {
			threadEnable = false;
			synchronized(writebuffer) {
				writebuffer.notifyAll();
			}	
		}
	}
}
