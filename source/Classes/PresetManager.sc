
PresetManager {
	
	classvar <presetsDirs;
	
	var <presetType, <presetName, <pieceDir, <zeroPad = 4,  <>list;

	 
	*initClass {
		
		presetsDirs	= 
				(
				   piece: 	"~/Library/Application Support/BEASTMulch/Presets/Piece Presets/".standardizePath,
				   concert:	"~/Library/Application Support/BEASTMulch/Presets/Concert Configuration/".standardizePath,
				   system:	"~/Library/Application Support/BEASTMulch/Presets/System Setup/".standardizePath
				);
		
		presetsDirs.values
				 .collect{| dir | 
						 if (dir.pathMatch.isEmpty) 
						    { systemCmd("mkdir -p" + dir.escapeChar($ )); ("creating directory: " + dir).inform }
						}		 
	 
	}
	 
	 
	*new { | presetType, presetName | 

	 	^super.newCopyArgs(presetType, presetName).init
	 
	}
	 
	
	init {
	
		pieceDir		= presetsDirs[presetType] ++ presetName ++ "/";
		this.storedPresets
	
	}
	
	
	presetName_ {| x |
		
		presetName 	= x;
		pieceDir 		= presetsDirs[presetType]  ++ presetName ++ "/";
		
	}
	
	presetType_ {| x |
		
		presetType 	= x;
		pieceDir 		= presetsDirs[presetType]  ++ presetName ++ "/";
		
	}
	
	
	checkDirForPiece {
 		
 		if (pieceDir.pathMatch.isEmpty) 
 		   { systemCmd("mkdir" + pieceDir.escapeChar($ )); 
 		     ("creating directory: " + pieceDir).inform; 
 		     this.storedPresets;
 		     this.changed 
 		   }

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
	 
	 
	 storedPresets {
	 
	 	list	= (presetsDirs[presetType]++"*").standardizePath.pathMatch.collect(_.basename)
	 		  .sort
	 		  /*.collect(_.asSymbol)*/;
	 	^list
	 
	 }
	 
	
}


	
NewPresetGUI {
	var defaultName, presetNames, origin, window, presetNameField;
	var >onClose;

	
	*new {| defaultName, presetNames, origin |
		^super.newCopyArgs(defaultName, presetNames, origin).makeWindow(origin ? (490 @ 360));
	}
	
		
	makeWindow {| origin |
		var presetName, copyDict;
		
		copyDict 	= IdentityDictionary[];
//		window 	= SCWindow("New Preset", Rect(origin.x, origin.y, 340-140+60, 230), false).userCanClose_(false);
		window 	= SCWindow("New Preset", Rect(origin.x, origin.y + 100, 340-140+60, 230 - 100), false).userCanClose_(false);
		
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		SCStaticText(window, 50 @ 20).string = "Name:";
		presetNameField 	= SCTextField(window, 180 @ 20)
						   		    .string_(defaultName)
						   		    .action_({| field | presetName = field.value });

//		[ 'Copy audio routing?' -> true, 'Copy control routing?' -> true, 'Copy fader labels?' -> true, 'Copy initial fader state?' -> false ]
//		 .do{| assoc |
//			 
//			 copyDict.add(assoc);
//			 window.view.decorator.nextLine;
//			 
//			 ToggleView(window, 240 @ 20)
//			 		.caption_(assoc.key.asString)
//			 		.hitColor_(Color.red(alpha: 0.2))
//			 		.action_({| button | copyDict[assoc.key] = button.value.postln })
//			 		.value_(assoc.value)
//			 
////			 SCButton(window, 180 @ 20)
////			 	    .states_([ "Yes", "No" ].collect( [ _, Color.black, Color.clear ]))
////				    .action_({| button | copyDict[assoc.key] = if (button.value == 0) { true } { false } })
////				    .value_(if (assoc.value) { 0 } { 1 })
//			 
//			 };


		[ 'Do you want to copy everything?' -> true ]
		 .do{| assoc |
			 
			 copyDict.add(assoc);
			 window.view.decorator.nextLine;
			 
			 ToggleView(window, 240 @ 20)
			 		.caption_(assoc.key.asString)
			 		.hitColor_(Color.green(alpha: 0.2))
			 		.action_({| button | copyDict[assoc.key] = button.value.postln })
			 		.value_(assoc.value)
			 
//			 SCButton(window, 180 @ 20)
//			 	    .states_([ "Yes", "No" ].collect( [ _, Color.black, Color.clear ]))
//				    .action_({| button | copyDict[assoc.key] = if (button.value == 0) { true } { false } })
//				    .value_(if (assoc.value) { 0 } { 1 })
			 
			 };
			 
			 
			 
			 

		window.view.decorator.shift(0, 30);
		
		SCButton(window, 115 @ 20)
			   .states_([[ "Cancel", Color.black, Color.clear ]])
			   .action_({	window.close; 
			   		    	this.changed(\cancel); 
			   		    	NotificationCenter.notify(this, \didClose) 
			   		   });
			   
		SCButton(window, 115 @ 20)
			   .states_([[ "OK", Color.white, Color.green ]])
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
		//Rect(origin.x, origin.y, 340-140+60, 230),
		//Rect(420, 360, 340, 230 + 25)
//		errorWindow = SCWindow("", Rect(490, 360, 260, 230 + 25), false, false)
		errorWindow = SCWindow("", Rect(490, 360 + 100, 260, 230 + 25 - 100), false, false)
					 .alwaysOnTop_(true)
					 .onClose = { errorWindow = nil };
		errorWindow.view.background	= Color.red(0.9);
		
		SCStaticText(errorWindow, Rect(20, 0, 240, 130))
		 .string_("Preset name already in use. Please enter a different name.")
		 .stringColor_(Color.white)
		 .font_(Font("Helvetica", 14));
		
		SCButton(errorWindow, Rect(70, 180, 118, 20))
		  .states_([[ "OK", Color.white, Color.red(0.9) ]])
		  .action_({	errorWindow.close; window.front; presetNameField.focus })
		  .focus;
			   
		errorWindow.front
	}
	
}