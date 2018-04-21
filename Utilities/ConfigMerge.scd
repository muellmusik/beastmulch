
// choose the master config from which data will be copied
Dialog.openPanel({|path| ~configMasterPath = path.postln; });

// choose the config(s) to be updated
Dialog.openPanel({|path| ~configsToUpdatePaths = path.postln; }, multipleSelection: true);

(

var dataToCopy;
var masterConfData;
var outputConfigs;
var outPath;

outputConfigs = IdentityDictionary.new;

dataToCopy = [
	// 'Audio Routing',
	'Output Levels',
	// 'Player',
	'Input Levels',
	'Pre-Processing',
	// 'Master Trim',
	// 'Virtual Outputs',
	'Post-Processing',
	// 'Virtual Inputs',
	// 'Multichannel',
];

masterConfData = Object.readTextArchive(~configMasterPath).value;

// masterConfData.postcs;

~configsToUpdatePaths.do({|path, i|
	var tempConf, tempConfData, tempConfKey;
	// path.postln;
    tempConf = Object.readTextArchive(path);
	tempConfData = tempConf.value;
	tempConfKey = tempConf.key;
	dataToCopy.do({|copyData|
		"Copying % to % config".format(copyData, tempConfKey).postln;
		tempConfData.put(copyData, masterConfData.at(copyData));
	});
	outputConfigs[tempConfKey.asSymbol] = tempConfData;
});

outPath = "~/Desktop".standardizePath +/+ "config-merge-exports";
File.mkdir(outPath);

outputConfigs.associationsDo({|assoc|
	assoc.writeArchive(outPath +/+ assoc.key ++ ".bmconf");
});
)
