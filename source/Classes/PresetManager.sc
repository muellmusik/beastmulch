
PresetManager {
	
	classvar <presetsDir;
	
	var <presetName, <pieceDir, <zeroPad = 4;
	 
	*initClass {
		
		presetsDir	= "~/Library/Application Support/BEASTMulch/Presets/".standardizePath;
		if (presetsDir.pathMatch.isEmpty) { systemCmd("mkdir -p" + presetsDir.escapeChar($ )); ("creating directory: " + presetsDir).inform }
		 
	 }
	 
	*new { | presetName | 

	 	^super.newCopyArgs(presetName).init
	 
	 }
	
	
	init {
	
		pieceDir	= presetsDir ++ presetName ++ "/"
	
	}
	
	
	presetName_ {| x |
		
		presetName 	= x;
		pieceDir 		= presetsDir ++ presetName ++ "/";
		
	}
	
	
	checkDirForPiece {
 		
 		if (pieceDir.pathMatch.isEmpty) { systemCmd("mkdir" + pieceDir.escapeChar($ )); ("creating directory: " + pieceDir).inform };

	}
	 
	 	 
	lastID{
	 	
	 	^((pieceDir ++ presetName + "-*")
	 		 .pathMatch
	 	   	 .collect{| presetName | presetName[ presetName.size - zeroPad ..] }.asInteger.maxItem 
	 	   	? 0 
	 	 )
	 	 
	 }
	 
	 
	add {| preset |
		var id;

	     this.checkDirForPiece;
	 	id = (this.lastID + 1).asStringToBase(width: zeroPad);
	 	preset.writeTextArchive(pieceDir ++ presetName + "-" + id);
	 	^this
	 
	 }
	
	
	get {| id |
		
		id 	= (id ?? { this.lastID }).asStringToBase(width: zeroPad);
		^Object.readTextArchive(pieceDir ++ presetName + "-" + id)
	 	
	 } 
	 
}

NewPresetGUI {
	var defaultName, presetNames, origin, window, presetNameField;
	var >onClose;

	
	*new {| defaultName, presetNames, origin |
		^super.newCopyArgs(defaultName, presetNames, origin).makeWindow(origin ? (420 @ 360));
	}
	
		
	makeWindow {| origin |
		var presetName, copyDict;
		
		copyDict 	= IdentityDictionary[];
		window 	= SCWindow("New Preset", Rect(origin.x, origin.y, 340, 200), false).userCanClose_(false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		SCStaticText(window, 130 @ 20).string = "Preset Name?";
		presetNameField 	= SCTextField(window, 180 @ 20)
						   		    .string_(defaultName)
						   		    .action_({| field | presetName = field.value });

		[ 'Copy audio routing?' -> true, 'Copy control routing?' -> true, 'Copy initial fader state?' -> false ]
		 .do{| assoc |
			 
			 copyDict.add(assoc);
			 window.view.decorator.nextLine;
			 SCStaticText(window, 130 @ 20).string_(assoc.key.asString);
			 SCButton(window, 180 @ 20)
			 	    .states_([ "Yes", "No" ].collect( [ _, Color.black, Color.clear ]))
				    .action_({| button | copyDict[assoc.key] = if (button.value == 0) { true } { false } })
				    .value_(if (assoc.value) { 0 } { 1 })
			 
			 };
		    
		window.view.decorator.shift(0, 30);
		
		SCButton(window, 155 @ 20)
			   .states_([[ "Cancel", Color.black, Color.clear ]])
			   .action_({	window.close; 
			   		    	this.changed(\cancel); 
			   		    	NotificationCenter.notify(this, \didClose) 
			   		   });
			   
		SCButton(window, 155 @ 20)
			   .states_([[ "Create New Preset", Color.white, Color.red ]])
			   .action_({	if (presetNames.indexOfEqual(presetName ? defaultName).isNil)
			   			   { window.close;
			   			     this.changed(\OK, presetName ? defaultName, copyDict); 
			   			     NotificationCenter.notify(this, \didClose)  
			   			   }
			   			   { this.makeErrorWindow(origin, window, presetNameField) }
			   		   })
			   .focus;

		window.onClose = { onClose.value };
		window.front
	}
	
	
	makeErrorWindow {| origin, window, presetNameField |
		var errorWindow;
		
		errorWindow = SCWindow("", Rect(420, 360, 340, 200+25), false, false)
					 .alwaysOnTop_(true)
					 .onClose = { errorWindow = nil };
		errorWindow.view.background	= Color.red(0.9);
		
		SCStaticText(errorWindow, Rect(20, 0, 320, 90))
		 .string_("Preset name already in use. Please enter a different name.")
		 .stringColor_(Color.white)
		 .font_(Font("Helvetica", 22));
		
		SCButton(errorWindow, Rect(92, 150, 155, 20))
		  .states_([[ "OK", Color.white, Color.red(0.9) ]])
		  .action_({	errorWindow.close; window.front; presetNameField.focus })
		  .focus;
			   
		errorWindow.front
	}
	
}