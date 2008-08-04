
BMBackup {	
	classvar <backupsDirs, <preferencesPath;
	var <zeroPad = 4,  <>list, <lastStoredSession;

	*initClass {
		backupsDirs	= (piece: 		"~/Library/Application Support/BEASTMulch/Backups/Pieces/".standardizePath,
					   configuration:	"~/Library/Application Support/BEASTMulch/Backups/Configurations/".standardizePath,
					   concert:		"~/Library/Application Support/BEASTMulch/Backups/Concerts/".standardizePath,
					   system:		"~/Library/Application Support/BEASTMulch/Backups/Systems/".standardizePath
					  );
		
		backupsDirs.values
				  .collect{| dir | 
						 if (dir.pathMatch.isEmpty) 
						    { systemCmd("mkdir -p" + dir.escapeChar($ )); ("creating directory: " + dir).inform }
				 };
		
		preferencesPath = "~/Library/Application Support/BEASTMulch/Preferences".standardizePath
	}
	 
	*new { ^super.new.init }
	
	init {
		var lastStoredSessionPath;
		
		lastStoredSessionPath = this.getPreference(\lastStoredSessionPath);
		if (lastStoredSessionPath.notNil) {lastStoredSession =  Object.readTextArchive(lastStoredSessionPath) };
	}
	
//	presetName_ {| x |
//		presetName 	= x;
//		pieceDir 		= presetsDirs[presetType]  ++ presetName ++ "/";
//	}
//	
//	backupType_ {| x |
//		backupType 	= x;
//		backupDir 		= backupsDirs[backupType]  ++ backupName ++ "/";
//	}
	
	checkDirForPiece {| backupDir |
 		if (backupDir.pathMatch.isEmpty) 
 		   { systemCmd("mkdir" + backupDir.escapeChar($ )); 
 		     ("creating directory: " + backupDir).inform; 
// 		     this.storedPresets;
// 		     this.changed 
 		   }
	} 
	 	 
	lastID{| backupDir, backupName |
	 	^((backupDir ++ backupName + "-*")
	 		 .pathMatch
	 	   	 .collect{| backupName | backupName[ backupName.size - zeroPad ..] }.asInteger.maxItem 
	 	   	? 0 
	 	 )
	 }
	
	makeSessionBackup {| concertManager, configManager | 
		var path, backup;

	     backup	= this.prepareForBackup(concertManager, configManager);
	     path 	= backupsDirs[\concert] ++ "BMConcert" + "-" + Date.localtime.stamp;
	     backup.writeTextArchive(path);
	     this.savePreference(\lastStoredSessionPath, path);
	 	^this
	
	}
	
	prepareForBackup {| concertManager, configManager | 
					var dict;

					dict		= configManager.dict.deepCopy;
					dict.removeAt('all off');
					^(concert: concertManager.concert.deepCopy, configurations: (dict: dict, names: configManager.names))
	}
	
	add {| backupType, backupName, backup |
		var id, path, backupDir;
		
		backupDir = backupsDirs[backupType]  ++ backupName ++ "/";
		this.checkDirForPiece(backupDir);
		id 	= (this.lastID(backupDir, backupName) + 1).asStringToBase(width: zeroPad);
		path 	= backupDir ++ backupName + "-" + id;
		backup.writeTextArchive(path);
		^this
	 }
	 	
//	get {| id |
//		 id = (id ?? { this.lastID }).asStringToBase(width: zeroPad);
//	     ^Object.readTextArchive(backupDir ++ backupName + "-" + id)
//	 } 
	 
	 
//	 getByName {| backupName | ^Object.readTextArchive(backupDir ++ backupName) } 
	 
	 
	 savePreference{| key, value |
	 			  var preferences;
	 			  
	 			  preferences = Object.readTextArchive(preferencesPath) ?? { IdentityDictionary[] };
	 			  preferences.add(key -> value);
	 			  preferences.writeTextArchive(preferencesPath);
	 			  ^this
	 }
	 
	 getPreference{| key |
	 			  var preferences;
	 			  
	 			  preferences = Object.readTextArchive(preferencesPath);
	 			  if (preferences.notNil) { ^preferences[key] } { ^nil }
	 }
	 
	 update {| changed, change |
	 		
	 		
	 }
	 			  
	 
//	storedPresets {
//	 	list	= (presetsDirs[presetType]++"*").standardizePath.pathMatch.collect(_.basename)
//	 		  .sort
//	 		  /*.collect(_.asSymbol)*/;
//	 	^list
//	 }
}


	
//NewPresetGUI {
//	var defaultName, presetNames, origin, window, presetNameField;
//	var >onClose;
//
//	
//	*new {| defaultName, presetNames, origin |
//		^super.newCopyArgs(defaultName, presetNames, origin).makeWindow(origin ? (490 @ 360));
//	}
//	
//		
//	makeWindow {| origin |
//		var presetName, copyDict;
//		
//		copyDict 	= IdentityDictionary[];
////		window 	= SCWindow("New Preset", Rect(origin.x, origin.y, 340-140+60, 230), false).userCanClose_(false);
//		window 	= SCWindow("New Preset", Rect(origin.x, origin.y + 100, 340-140+60, 230 - 100), false).userCanClose_(false);
//		
//		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
//		
//		SCStaticText(window, 50 @ 20).string = "Name:";
//		presetNameField 	= SCTextField(window, 180 @ 20)
//						   		    .string_(defaultName)
//						   		    .action_({| field | presetName = field.value });
//
////		[ 'Copy audio routing?' -> true, 'Copy control routing?' -> true, 'Copy fader labels?' -> true, 'Copy initial fader state?' -> false ]
////		 .do{| assoc |
////			 
////			 copyDict.add(assoc);
////			 window.view.decorator.nextLine;
////			 
////			 ToggleView(window, 240 @ 20)
////			 		.caption_(assoc.key.asString)
////			 		.hitColor_(Color.red(alpha: 0.2))
////			 		.action_({| button | copyDict[assoc.key] = button.value.postln })
////			 		.value_(assoc.value)
////			 
//////			 SCButton(window, 180 @ 20)
//////			 	    .states_([ "Yes", "No" ].collect( [ _, Color.black, Color.clear ]))
//////				    .action_({| button | copyDict[assoc.key] = if (button.value == 0) { true } { false } })
//////				    .value_(if (assoc.value) { 0 } { 1 })
////			 
////			 };
//
//
//		[ 'Do you want to copy everything?' -> true ]
//		 .do{| assoc |
//			 
//			 copyDict.add(assoc);
//			 window.view.decorator.nextLine;
//			 
//			 ToggleView(window, 240 @ 20)
//			 		.caption_(assoc.key.asString)
//			 		.hitColor_(Color.green(alpha: 0.2))
//			 		.action_({| button | copyDict[assoc.key] = button.value.postln })
//			 		.value_(assoc.value)
//			 
////			 SCButton(window, 180 @ 20)
////			 	    .states_([ "Yes", "No" ].collect( [ _, Color.black, Color.clear ]))
////				    .action_({| button | copyDict[assoc.key] = if (button.value == 0) { true } { false } })
////				    .value_(if (assoc.value) { 0 } { 1 })
//			 
//			 };
//			 
//			 
//			 
//			 
//
//		window.view.decorator.shift(0, 30);
//		
//		SCButton(window, 115 @ 20)
//			   .states_([[ "Cancel", Color.black, Color.clear ]])
//			   .action_({	window.close; 
//			   		    	this.changed(\cancel); 
//			   		    	NotificationCenter.notify(this, \didClose) 
//			   		   });
//			   
//		SCButton(window, 115 @ 20)
//			   .states_([[ "OK", Color.white, Color.green ]])
//			   .action_({	if (presetNames.indexOfEqual(presetName ? defaultName).isNil)
//			   			   { window.close;
//			   			     this.changed(\OK, presetName ? defaultName, copyDict); 
//			   			     NotificationCenter.notify(this, \didClose)  
//			   			   }
//			   			   { this.makeErrorWindow(origin, window, presetNameField) }
//			   		   })
//			   .focus;
//
//		window.onClose = { onClose.value };
//		window.front
//	}
//	
//	
//	makeErrorWindow {| origin, window, presetNameField |
//		var errorWindow;
//		//Rect(origin.x, origin.y, 340-140+60, 230),
//		//Rect(420, 360, 340, 230 + 25)
////		errorWindow = SCWindow("", Rect(490, 360, 260, 230 + 25), false, false)
//		errorWindow = SCWindow("", Rect(490, 360 + 100, 260, 230 + 25 - 100), false, false)
//					 .alwaysOnTop_(true)
//					 .onClose = { errorWindow = nil };
//		errorWindow.view.background	= Color.red(0.9);
//		
//		SCStaticText(errorWindow, Rect(20, 0, 240, 130))
//		 .string_("Preset name already in use. Please enter a different name.")
//		 .stringColor_(Color.white)
//		 .font_(Font("Helvetica", 14));
//		
//		SCButton(errorWindow, Rect(70, 180, 118, 20))
//		  .states_([[ "OK", Color.white, Color.red(0.9) ]])
//		  .action_({	errorWindow.close; window.front; presetNameField.focus })
//		  .focus;
//			   
//		errorWindow.front
//	}
	
//}