/*
objects should be an array of BM objects
would be nice to support other objects, but maybe tricky

maybe should use nowExecutingPath by default and save next to doc rather than app support directory?

can be recursive
*/

BMPresetManager {
	var <name, path, archived, objects, presetDict;
	
	*new {|name, objects, path, archived = true|
		^super.newCopyArgs(name, path, archived).init(objects);
	}
	
	init {|objects|
		objects = objects.collectAs({|object| 
			object.name->object;
		}, IdentityDictionary);
		// initialise path, using app support dir if nil
		path.isNil.if({
			path = "~/Library/Application Support/BEASTMulch/Backups/Presets/".standardizePath;
		});
		path = path.withTrailingSlash ++ name ++ ".bmpreset";
		
		// initialise preset dictionary, reading from existing if needed
		if(archived && {File.exists(path)}, {
			presetDict = Object.readArchive(path);
			presetDict.notNil.if({
				"Presets read from existing file: %\n".postf(path);
			});
			if(presetDict['BMLib Version'] != BMOptions.version, {
				this.convertDict;	
			});
		}, {
			presetDict = IdentityDictionary.new;
			presetDict['BMLib Version'] = BMOptions.version;
			presetDict['BMPresetManager Name'] = name;
			presetDict['Object Names'] = objects.keys;
		});
	}
	
	store {|presetname|
		presetDict[presetname] = objects.collect({|object| object.name->object.mappings });
		
		archived.if({presetDict.writeArchive(path)});
	}
	
	remove {|presetname|
		presetDict[presetname] = nil;
		
		archived.if({presetDict.writeArchive(path)});
	}
	
	export {|expath|
		presetDict.writeArchive(expath)
	}
	
	import {|impath|
		var newDict;
		try {
			newDict = Object.readArchive(impath);
			if(newDict.isKindOf(Dictionary).not || {newDict['Object Names'] != objects.keys}, {newDict.throw});
			presetDict = newDict;
			archived.if({presetDict.writeArchive(path)});
		} {	|thrown|
			"BMPresetManager import failed".error;
			"with file at path %:\n".postf(impath);
			thrown.postcs;
		}
	}
	
//	addObject {|object|}
//	
//	removeObject {|object|}
	
	// hook for future use
	convertDict {}
	
	// for recursive objects
	mappings { ^presetDict }
	
	mappings_ {|newDict|
		presetDict = newDict;
		archived.if({presetDict.writeArchive(path)});	
	}
}