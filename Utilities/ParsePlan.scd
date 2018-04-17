// first export excel file as TSV (Unix line endings) in beastmulch order, then...
~path = Dialog.openPanel({|path| ~path = path.postln; }); // select Tab separated values file. N.B. ensure Unix line endings

x = TabFileReader.read(~path).postcs;

/*
Assumptions:
Indices start from 0
Row 1 is labels, so we skip that
Columns are  speaker type, name, order (unused), output (normal not SC so subtract 1), ...
*/

(
l = x.copyToEnd(1).collectAs({|speak|
	var spec, speaktype;
	speaktype = speak[0];
	spec = case(
		{ speaktype.beginsWith("ATC") }, {'SCM50'},
		{ speaktype.beginsWith("APG") }, {'MC24'},
		{ speaktype.beginsWith("8030") }, {'8030A'},
		{ speaktype.beginsWith("8040") }, {'8040A'},
		{ speaktype.beginsWith("8050") }, {'8050A'},
		{ speaktype.beginsWith("1037") }, {'1037C'},
		{ speaktype.beginsWith("Tweet") }, {'Tweeters'},
		{ speaktype.beginsWith("7070") }, {'7070A'},
		{ speaktype.beginsWith("1094") }, {'1094A'},
		{ speaktype.beginsWith("Lynx") }, {'Lynx'}
	); // nil will default to 'Generic'
	BMSpeaker(speak[1].asSymbol.postln, speak[3].interpret - 1, spec:spec ? 'Generic');

}, BMInOutArray);
)

l.keys.do(_.postln); // see if this looks correct...

Dialog.savePanel({|path| l.writeArchive(path); }); // use the .spk extension

// now we'll generate some basic configs with routing only
(
~numPlayerChans = 8;
~numHardwareIns = 16;
~hardwareInsOffset = 8;
~inputNames = ~numPlayerChans.collect({|i| ("Player-" ++ (i + 1)).asSymbol }) ++ ~numHardwareIns.collect({|i| ("Hardware In " ++ (i + 1)).asSymbol });

// first collect the names and column nums of all configs. These are all that end with "Recv".
~configNames = IdentityDictionary.new;
x[0].do({|key, i|
	if(key.containsi("Recv"), {
		var loc;
		loc = key.find("Recv", true);
		key = key.copyFromStart(loc - 1);
		key = key.stripWhiteSpace; // drop leading or trailing spaces
		~configNames[key.asSymbol] = i;
	});
});

// make a template with some sensible defaults supplied
~template = IdentityDictionary[ 'Audio Routing'->IdentityDictionary.new, 'Output Levels'->IdentityDictionary.new, 'Player'->IdentityDictionary.new, 'Input Levels'->IdentityDictionary.new, 'Master Fader'->IdentityDictionary.new, 'Pre-Processing'->IdentityDictionary.new, 'Virtual Outputs'->IdentityDictionary.new, 'Post-Processing'->IdentityDictionary.new, 'Virtual Inputs'->IdentityDictionary.new, 'Multichannel'->IdentityDictionary.new ];

l.keys.do({|name|
	~template['Post-Processing'][name] = IdentityDictionary[ ('trim' -> 0), ('plugins' -> [  ]) ];
});

~inputNames.do({|name|
	~template['Pre-Processing'][name] = IdentityDictionary[ ('trim' -> 0), ('plugins' -> [  ]) ];
	~template['Audio Routing'][name] = List.new;
});

~finalConfigs = IdentityDictionary.new;

// now we iterate over the configs and collect the audio assigns

// player
~configNames.keysValuesDo({|key, ind|
	var params, thisConfig;
	thisConfig = ~template.deepCopy;
	x.copyToEnd(1).do({|speak|
		var name, assigns;
		name = speak[1].asSymbol;
		assigns = "[%]".format(speak[ind].stripWhiteSpace.tr(Char.space, $,)).interpret;
		assigns = assigns.collect({|num| ("Player-" ++ num).asSymbol });
		assigns.do({|assign|
			if(~inputNames.includes(assign), {
				thisConfig['Audio Routing'][assign].add(name);
			});
		});
	});
	~finalConfigs[key] = thisConfig;
});

// hardware
~configNames.keysValuesDo({|key, ind|
	var params, thisConfig;
	thisConfig = ~template.deepCopy;
	x.copyToEnd(1).do({|speak|
		var name, assigns;
		name = speak[1].asSymbol;
		assigns = "[%]".format(speak[ind].stripWhiteSpace.tr(Char.space, $,)).interpret;
		assigns = assigns.collect({|num| ("Hardware In " ++ (num + ~hardwareInsOffset)).asSymbol });
		assigns.do({|assign|
			if(~inputNames.includes(assign), {
				thisConfig['Audio Routing'][assign].add(name);
			});
		});
	});
	~finalConfigs[(key ++ " Hardware").asSymbol] = thisConfig;
});

)

// save and write the configs to original dir
(
~finalConfigs.associationsDo({|assoc|
	assoc.writeArchive(~path.dirname +/+ assoc.key);
})

)
