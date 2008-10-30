
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
	
	rememberWorkspace {| path, newObjectFunc |
		if (lastStoredSession.notNil) 
   			{ ^(path.asArray.inject(lastStoredSession, {| prev, next | prev[next]})  ?? newObjectFunc) }
   			{ ^newObjectFunc.value }
	}

	watch{| object | object.addDependant(this) }
	
	stopWatching{| object | object.removeDependant(this) }

	checkDirForPiece {| backupDir |
 		if (backupDir.pathMatch.isEmpty) 
 		   { systemCmd("mkdir" + backupDir.escapeChar($ )); 
 		     ("creating directory: " + backupDir).inform; 
 		   }
	} 
	 	 
	lastID{| backupDir, backupName |
	 	^((backupDir ++ backupName + "-*")
	 		 .pathMatch
	 	   	 .collect{| backupName | PathName(backupName).endNumber }.asInteger.maxItem 
	 	   	? 0 
	 	 )
	 }
	
	makeSessionBackup {| concertManager, configManager | 
		var path, backup;

	     backup	= this.prepareForSessionBackup(concertManager.deepCopy, configManager.deepCopy);
	     path 	= backupsDirs[\concert] ++ "Concert" + "-" + Date.localtime.stamp;
	     backup.writeTextArchive(path);
	     this.savePreference(\lastStoredSessionPath, path)
	}
	
	prepareForSessionBackup {| concertManager, configManager | 
		var dict;

		dict		= configManager.dict;
		dict.removeAt('all off');
		^(concert: concertManager.concert, configurations: (dict: dict, names: configManager.names))
	}
	
	makeBackup {| backupType, backupName, backup |
		var id, path, backupDir;
		
		backupDir = backupsDirs[backupType]  ++ backupName ++ "/";
		this.checkDirForPiece(backupDir);
		id 	= this.lastID(backupDir, backupName) + 1;
		id 	= id.asStringToBase(width: max(zeroPad, id.asString.size));
		path	= backupDir ++ backupName + "-" + id;
		backup.writeTextArchive(path)
	 }
	 
	 savePreference{| key, value |
		var preferences;
	 			  
		preferences = Object.readTextArchive(preferencesPath) ?? { IdentityDictionary[] };
		preferences.add(key -> value);
		preferences.writeTextArchive(preferencesPath)
	 }
	 
	 getPreference{| key |
 		var preferences;
 			  
		preferences = Object.readTextArchive(preferencesPath);
 		if (preferences.notNil) { ^preferences[key] } { ^nil }
	 }
	 
	 update {| changed, change ... args |
	 	switch(change,
	 		\storeSession, { this.makeSessionBackup(*args) },
	 		\store, { this.makeBackup(*args) }
	 	)	
	 }
}