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

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.support.v4.app.Fragment;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Environment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.AdapterView.OnItemClickListener;

/** Select a directory or a file. 
 * 
 * */
public class FileSelectFragment extends Fragment implements OnItemClickListener {

    public interface FileSelectedListener {
        void fileSelected(File file);
    }
	/*
	 * Use the unicode "back" triangle to indicate there is a parent 
	 * directory rather than an icon to minimise file dependencies.
	 * 
	 * You may have to find an alternative symbol if the font in use 
	 * doesn't support this character. 
	 * */ 
	final String PARENT =  "\u25C0"; 
	
	private IFileSelectCallbacks mCallbacks;
	private ArrayList<File> fileList;
	
	// The widgets required to provide the UI.
	private TextView titleLine;
	private String titleString = "";
	private String fileExtString = "";
	private TextView selectedPath;
	private TextView selectedFile;
	private TextView selectedFileExt;
	private ListView directoryView;

	// The directory the user has selected.
	private File currentDirectory;
	private File currentFile;

	// How the popup is to be used.
	private Mode selectionMode;
	
	// ID returned in the callbacks
	private int actionID;
	
	// Filtered view of the directories.
	private FilenameFilter fileFilter;	

	
	/** How do we want to use the selector? */
	public enum Mode {
		DirectorySelector,
		FileSelector
	}
	
	/** 
	 * Signal to / request action of host activity.
	 * 
	 * */
	public interface IFileSelectCallbacks {

		/**  
		 * Hand selected path and name to context for use.
		 * If user cancels absolutePath and filename are handed out as null.
		 * 
		 *  @param absolutePath - Absolute path to target directory.
		 *  @param fileName     - Filename. Will be null if Mode = DirectorySelector
		 *    
		 * */
		public void onConfirmSelect(int actionID, String absolutePath, String fileName);
		
		
		/** Allow the client activity to check file content / format whilst the user
		 *  still has the popup in view.
		 *  
		 *  The alternative is to provide a custom filter that examines file content
		 *  as it goes, but that could get very slow very quickly especially for binary
		 *  files. 
		 *  
		 * */
		public boolean isValid(int actionID, String absolutePath, String fileName);
	
	}


	/** Provide a standard filter to match any file with an extension in the supplied list. 
	 *  Case insensitive..
	 *  Directories are always accepted.
	 *  
	 *   @param fileExtensions List of file extensions including full stop. .xml, .txt etc. 
	 * */
	public static FilenameFilter FiletypeFilter(final ArrayList<String> fileExtensions) {
		
		  FilenameFilter fileNameFilter = new FilenameFilter() {
        	  
	            @Override
	            public boolean accept(File directory, String fileName) {
	            
	            	boolean matched = false;
	            	File f = new File(String.format("%s/%s", directory.getAbsolutePath(), fileName));
	            	
	            	// We let all directories through.
	            	matched = f.isDirectory();
	            	
	            	if (!matched) {
		            	for(String s : fileExtensions) {
		            		s = String.format(".{0,}\\%s$", s);
		            		s = s.toUpperCase(Locale.getDefault());
		            		fileName = fileName.toUpperCase(Locale.getDefault());
		            		matched = fileName.matches(s);
		            		if (matched) {
		            			break;
		            		}
		            	}
	            	}
	            	
	               return matched; 
	            	
	            }
	         };	
	         
	         return fileNameFilter;
		
	}
	
	/** Create new instance of a file save fragment. 
	 * 
	 * @param Mode - Directory selector or File selector?
	 * @param actionID - user ID returned to the callbacks
	 * @param callbacks - see comments in IFileSelectCallbacks
	 * */
    public FileSelectFragment(Mode selectionMode, int actionID, IFileSelectCallbacks callbacks) {
    	super();
    	this.actionID = actionID;
    	this.selectionMode = selectionMode;
    	this.mCallbacks = callbacks;
        fileList = new ArrayList<File>();
    }	
	
    /** Optional. Allow restriction of file names/types displayed for selection. 
     * @param fileFilter - May be null. Custom rule for selecting directories/files.
     * 
     * */
    public void setFilter(ArrayList<String> allowedExtensions) {
    	this.fileFilter = FiletypeFilter(allowedExtensions);
    	fileExtString = "";
    	if(allowedExtensions.size() == 1) {
    		fileExtString += allowedExtensions.get(0);
    		
    		if(selectedFileExt != null)
    			selectedFileExt.setText(fileExtString);
    	}
    }
    
    public void setTitle(String title) {
    	titleString = title;
    	if(titleLine != null)
    		titleLine.setText(title);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
        Bundle savedInstanceState) {
    	
        // Inflate the layout for this fragment
        View view = inflater.inflate(R.layout.fileselect, container, false);

        titleLine = (TextView) view.findViewById(R.id.title);
        if(titleString != "")
        	titleLine.setText(titleString);

		/* 
		 * Set up initial sub-directory list.
		 * 
		 * */
		currentDirectory = Environment.getExternalStorageDirectory();
		fileList = getDirectoryContent(currentDirectory);
		DirectoryDisplay displayFormat = new DirectoryDisplay(getActivity(), fileList);

		LinearLayout.LayoutParams listViewLayout = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
				(int)(getActivity().getWindow().getDecorView().getHeight() * 0.5),
                                                                                 0.0F);
		directoryView = (ListView)view.findViewById(R.id.directoryView);
		directoryView.setLayoutParams(listViewLayout);
		directoryView.setAdapter(displayFormat);
		directoryView.setOnItemClickListener(this);
		
		selectedPath = (TextView) view.findViewById(R.id.selectedPath);
		selectedPath.setText(currentDirectory.getAbsolutePath() + "/");

		selectedFile = (TextView) view.findViewById(R.id.selectedFile);
		selectedFileExt = (TextView) view.findViewById(R.id.fileExt);
		if(fileFilter != null)
    		selectedFileExt.setText(fileExtString);

		Button positiveButton = (Button) view.findViewById(R.id.buttonOK);
		positiveButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {

				String absolutePath = currentDirectory.getAbsolutePath();
				String filename = selectedFile.getText().toString();
				
				if(!filename.contains(".") && fileFilter != null)
					filename += fileExtString;

				if (mCallbacks.isValid(actionID, absolutePath, filename)) {
					// dismiss();
					mCallbacks.onConfirmSelect(actionID, absolutePath, filename);
				}
			}
		});

		Button negativeButton = (Button) view.findViewById(R.id.buttonCancel);
		negativeButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				mCallbacks.onConfirmSelect(actionID, "", "");
			}
		});

		Button mkdirButton = (Button) view.findViewById(R.id.buttonMkdir);
		mkdirButton.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				if(selectedFile.getText().length() == 0) {
					selectedFileExt.setText("");
					selectedFile.setHint(R.string.hint_dirname);
					return;
				}
				File folder = new File(selectedPath.getText() + "/" + selectedFile.getText());
				if (!folder.exists()) {
				   folder.mkdir();
				   selectedFileExt.setText(fileExtString);
				   selectedFile.setHint(R.string.hint_filename_unadorned);
				   RefreshDirList();
				}				
			}
		});
		
        return view;
    }
	

	/** Single short click/press selects a file.
	 * or opens a directory in file select mode
	 * */
	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int pos, long id) {

		currentFile = null;
		
		String path = currentDirectory.getAbsolutePath();
		if (currentDirectory.getParent() != null) {
			path += "/";
		}
		selectedPath.setText(path);	

		if (pos >= 0 || pos < fileList.size()) {
			currentFile = fileList.get(pos);
			
			String name = currentFile.getName();
			
			if (!currentFile.isDirectory() && 
			    !name.equals(PARENT) &&
			    selectionMode == Mode.FileSelector ) {
				selectedFile.setText(currentFile.getName());
				if(currentFile.getName().contains("."))
					selectedFileExt.setText("");
			}
				
			if ((currentFile.isDirectory() || name.equals(PARENT)) 
					&& selectionMode == Mode.FileSelector) {

				File selected = fileList.get(pos);
				// Are we going up or down?
				if (name.equals(PARENT)) {
					currentDirectory = currentDirectory.getParentFile();
				}
				else {
					currentDirectory = 	selected;
				}

				RefreshDirList();
			}
				
		}
		
	}
	
	private void RefreshDirList() {
		// Refresh the listview display for the newly selected directory.
		fileList = getDirectoryContent(currentDirectory);
		DirectoryDisplay displayFormatter = new DirectoryDisplay(getActivity(), fileList);
		directoryView.setAdapter(displayFormatter);
		
		// Update the path TextView widgets.  Tell the user where he or she is and clear the selected file.
		currentFile = null;
		String path = currentDirectory.getAbsolutePath();
		if (currentDirectory.getParent() != null) {
			path += "/";
		}

		selectedPath.setText(path);	
		if (selectionMode == Mode.FileSelector) {
			selectedFile.setText(null);
		}
		
	}


	/** Identify all sub-directories and files within a directory. 
	 *  @param directory The directory to walk.
	 * */
	private ArrayList<File> getDirectoryContent(File directory) {
		
		ArrayList<File> displayedContent = new ArrayList<File>();
		File[] files = null;

		if (fileFilter != null) {
	        files = directory.listFiles(fileFilter);
		}
		else {
			files = directory.listFiles();
		}
			
		// Allow navigation back up the tree when the directory is a sub-directory.
		if (directory.getParent() != null) {
			displayedContent.add(new File(PARENT));
		}
		
		// Get the content in this directory.
		if (files != null) {
			for (File f : files) {

				boolean canDisplay = true;

				if (selectionMode == Mode.DirectorySelector && !f.isDirectory()) {
					canDisplay = false;
				}
				
				canDisplay = (canDisplay && !f.isHidden());
				
				if (canDisplay) {
					displayedContent.add(f);
				}
			}
		}
		
		return displayedContent;
		
	}
	
	/** Display the sub-directories in a selected directory. 
	 * 
	 * */
	private class DirectoryDisplay 
		extends ArrayAdapter<File> {
		
		public DirectoryDisplay(Context context, List<File> displayContent) {
			super(context, android.R.layout.simple_list_item_1, displayContent);
		}
		
		/** Display the name of each sub-directory. 
		 * */
		@Override
		public View getView(int position, View convertView, ViewGroup parent) {

			int iconID = R.drawable.ic_file;
			// We assume that we've got a parent directory...
			TextView textview = (TextView) super.getView( position, convertView, parent );
			
			// If we've got a directory then get its name.
			// If it's a file we show the file icon, if a directory then the directory icon.
			if ( fileList.get(position) != null ) {
				String name = fileList.get(position).getName();
				textview.setText(name);

				if (fileList.get(position).isDirectory()) {
					iconID = R.drawable.ic_dir;
				}

				if (name.equals(PARENT)) {
					iconID = -1;
				}
				
				// Icon to the left of the text.
				if (iconID > 0 ){
					Drawable icon = getActivity().getResources().getDrawable( iconID );
					textview.setCompoundDrawablesWithIntrinsicBounds(icon,null, null, null );	
				}
				
			}

			return textview;
		}		
			
	}

}
