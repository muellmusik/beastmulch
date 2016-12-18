(
Routine({

var player, controllersList;
var pieceUpdater,  changeFromPlayerGUI = true, newPresetGUIFunc, addButton, backupManager;
var arrays, outputArray, systemConfig;
var controllers, inputs, inputArray, privateIns, privateOuts;
var concertManager, configManager, pieces, configurations;
var chainFunc, chain, cond, configsGUI, configsGUIFunc;
var controllerAutomator, bmMenu, configMenu, utilMenu, autoMenuItem, autoGUI, helpMenu;
var pinkNoiseFunc, speakersWindow;
var cocoaApp, cocoaMainMenu, showHideFunc;


s.options = BMOptions.defaultServerOptions;

// init midi
BMMIDIPort.init;

backupManager	= BMBackup.new;
outputArray	= backupManager.rememberWorkspace([ \concert, \system, \speakers ],  { BMInOutArray.new });
//postf("outarray %\n", outputArray);
~outputArray = outputArray;


// recall system config
systemConfig = backupManager.rememberWorkspace([ \concert, \system, \sysconfig ],  { BMSystemAppConfig.new });

s.options.numInputBusChannels = systemConfig.numInChannels;

0.5.wait;

pieces		= backupManager.rememberWorkspace([ \concert, \pieces ],  { List[] });
configurations= backupManager.rememberWorkspace(\configurations,  { (dict:  IdentityDictionary[], names: List[ 'all off' ]) });

// make sure we have enough output channels
if(outputArray.size > 0, {
	s.options.numOutputBusChannels = max(
		s.options.numOutputBusChannels,
		outputArray.values.asUGenInput.maxItem + 1
	).asInteger;
});

// boot
cond = Condition.new;
s.bootSync(cond);

0.2.wait;

if(systemConfig.controllers.size == 0, {
	systemConfig.controllers.add('Sample Controller' -> (paramsDict: (name: "Sample Controller", numControls: 8), class: BMVirtualController));
});

// config controllers recall workspace and system config
controllersList = systemConfig.controllers.collectAs({|assoc|
	assoc.value[\class].newFromParamDict(assoc.value[\paramsDict], s);
}, List);


{




player = BMSoundFilePlayer.new(systemConfig.numSoundFilePlayerChans, name: 'Player');

chainFunc = {
	controllers = BMAbstractController.masterInOutArray;
	inputs = [player, BMInOutArray.hardwareInputArray];
	inputArray = BMInOutArray[];
	inputs.do({|input| inputArray = inputArray ++ input.asBMInOutArray });
	privateIns = BMInOutArray.privateBusBlock('Virtual In', systemConfig.numVirtIns);
	privateOuts = BMInOutArray.privateBusBlock('Virtual Out', systemConfig.numVirtOuts);

	~getIns = inputArray;
	[
		inputs,
		BMAudioMatrix(inputArray, privateIns, name: 'Virtual Inputs'),
		BMTrimPluginsRack(inputArray ++ privateIns, name:'Pre-Processing'),
		BMAmpControlMatrix(controllers, inputArray ++ privateIns, name:'Input Levels'),
		BMAudioMatrix(inputArray ++ privateIns, outputArray ++ privateOuts, name: 'Audio Routing'),
		BMAmpControlMatrix(controllers, outputArray ++ privateOuts, name:'Output Levels'),
		BMTrimPluginsRack(outputArray ++ privateOuts, name: 'Post-Processing'),
		BMMultichannelPluginsRack(inputArray ++ privateIns, outputArray, name: 'Multichannel'),
		BMAudioMatrix(privateOuts, outputArray, name: 'Virtual Outputs'),
		BMMasterFader(name: 'Master Fader')
	]

};

chain = chainFunc.value;

controllerAutomator = BMControllerAutomator(BMAbstractController.masterInOutArray.keys, player);
~controllerAutomator = controllerAutomator;
bmMenu = "BEASTmulch";

MainMenu.register(Action("SoundFile Display / Controller Automation", {
	if(autoGUI.isNil, {~gui = autoGUI = controllerAutomator.gui.onClose_({autoGUI = nil})}, {
		autoGUI.window.front;
	});
}), bmMenu);


MainMenu.register(Action("Visualise Speakers", {BMSpeakerListVisualiser(outputArray)}), bmMenu);

MainMenu.register(Action("Show Virtual Controllers", {
	BMAbstractController.allControllers.do({|ctllr|
		if(ctllr.class == BMVirtualController, {BMVirtualControllerSliders(ctllr)});
	})
}), bmMenu);

configManager	= BMConfigurations(configurations, backupManager);
concertManager= BMConcert(pieces, configManager, [ outputArray, inputArray ], BMAbstractController.allControllers, controllerAutomator, systemConfig, backupManager);

~concertManager = concertManager;

MainMenu.register(Action.separator, bmMenu);

configMenu = "Configuration";

MainMenu.register(Action("Configure System", {
	BMSystemConfigAppGUI(systemConfig, {|res|
		concertManager.setSysConfig(res);
		concertManager.storeSession(configManager);
		BMAlert("To change the system configuration the system must be restarted.\nPress okay to do this now.", [["ok", Color.black, Color.new255(51, 111, 203, 255 * 0.95)]], [{thisProcess.recompile;}], background: Color.white, color: Color.red, border:false);
	});
}), bmMenu, configMenu);

MainMenu.register(Action("Configure Speakers", {
	if(speakersWindow.isNil, {
   		speakersWindow = BMSpeakerArrayGUI(outputArray, {|newSpeakers|
   			outputArray = newSpeakers;
   			concertManager.setSpeakers(newSpeakers);
   			concertManager.storeSession(configManager);
   			BMAlert(
   				"To change the speaker array the system must be restarted.\nDo you want to do this now?",
   				[["cancel", Color.black, Color.new255(51, 111, 203, 255 * 0.95)],
   				["ok", Color.black, Color.new255(51, 111, 203, 255 * 0.95)]],
   				[nil, {thisProcess.recompile;}],
   				background: Color.white,
   				color: Color.red,
   				border:false);
   		}, "Speaker Array Definition", 129 @ 34);
   		speakersWindow.onClose_({ speakersWindow = nil })
   	})
}), bmMenu, configMenu);



{
configsGUIFunc =  {| chain | BMConfigurationsGUI(chain, configManager, concertManager, "Configuration Editor", 550 @ 550) };
configsGUI	 = configsGUIFunc.value(chain);
BMConcertGUI(concertManager, configManager, outputArray, chain, chainFunc, configsGUIFunc, controllerAutomator, "Concert Editor", 110 @ 550);
}.defer;

// PinkNoise Tester

pinkNoiseFunc = {

var pinkWin, startButton, synth;
var outputControl, outputNumber, ampControl;
var cmdPeriodFunc;
s.options.numOutputBusChannels = 96;
s.waitForBoot({
SynthDef(\PinkTest, {|out, amp| Out.ar(out, PinkNoise.ar(amp) * Decay2.ar(Impulse.ar(2), 0.1)); }).send(s);

// make the window
pinkWin = Window("PinkNoise Tester", Rect(20, 600, 416, 100), false);
pinkWin.front; // make window visible and front window.
pinkWin.view.decorator = FlowLayout(pinkWin.view.bounds);

// add a button to start and stop the sound.
startButton = RoundButton(pinkWin, 75 @ 20).extrude_(false).canFocus_(false);
startButton.states = [
	["Start", Color.black, Color.green.alpha_(0.2)],
	["Stop", Color.white, Color.red.alpha_(0.2)]
];
startButton.action = {|view|
		if (view.value == 1) {
			// start sound

			synth = Synth.tail(s.defaultGroup, \PinkTest, [\amp, ampControl.value.dbamp, \out,
				outputArray[outputControl.menu.item]]);

		};
		if (view.value == 0) {
			// stop sound
			synth.free;
		};
};


// create controls for all parameters
pinkWin.view.decorator.nextLine;

outputControl = EZPopUpMenu(pinkWin, 230 @ 20, "Hardware Out: ", outputArray.keys, {|ez|
	synth.set("out", outputArray[ez.menu.item]);
	outputNumber.string_("Channel:" + (outputArray[ez.menu.item].asControlInput + 1));

}, labelWidth: 100);

outputControl.menu.background_(Color.white.alpha_(0.2)).font_(Font("Helvetica-Bold", 12));

outputNumber = StaticText(pinkWin, Rect(0,0,166,20))
	.string_("Channel:" + (outputArray.first.value.asControlInput + 1))
	.font_(Font("Helvetica-Bold", 12))
	.align_(\right);

pinkWin.view.decorator.nextLine.nextLine;
ampControl = EZSlider(pinkWin, 400 @ 24, "Amp", \db,
	{|ez| synth.set("amp", ez.value.dbamp); }, -34);

pinkWin.view.decorator.nextLine;
StaticText(pinkWin, Rect(0,0,300,80)).string_("Outputs are Hardware, not SC, i.e. 1 = 1");


// set start button to zero upon a cmd-period
cmdPeriodFunc = { startButton.value = 0; };
CmdPeriod.add(cmdPeriodFunc);

// stop the sound when window closes and remove cmdPeriodFunc.
pinkWin.onClose = {
	synth.free;
	CmdPeriod.remove(cmdPeriodFunc);
};
})

};

// Utilities
utilMenu = "Utilities";
MainMenu.register(Action("Test Outputs", pinkNoiseFunc), bmMenu, utilMenu);
MainMenu.register(Action("Meter Bridge", {s.meter}), bmMenu, utilMenu);
MainMenu.register(Action("Auto-balance Speakers", {
	BMAutoBalancerGUI(outputArray, {
		// we operated in place so we can just store with no restart


		concertManager.storeSession(configManager);

	}, s);
}), bmMenu, utilMenu);
MainMenu.register(Action("Dump Node Tree", {s.queryAllNodes(false)}), bmMenu, utilMenu);
MainMenu.register(Action("Dump All Controller Values", {BMAbstractController.dumpAllValues}), bmMenu, utilMenu);
MainMenu.register(Action("Calibrate Controllers", {
	{ BMAbstractController.allControllers.do({|ctrlr| ctrlr.calibrate.wait;}); }.fork;
}), bmMenu, utilMenu);



MainMenu.register(Action.separator, bmMenu, utilMenu);
MainMenu.register(Action("Restart System", {thisProcess.recompile}), bmMenu, utilMenu);

// Help
helpMenu = "Help";

// needs rethinking
/*MenuItem(helpMenu, "BEASTmulch System Quickstart").action_({
var help;
help = Document.open("BEASTmulch System Quickstart".findHelpFile).editable_(false);
help.bounds = help.bounds.resizeBy(700, 400);
});*/


/*MenuItem(helpMenu, "Go to BEASTmulch Home Page").action_({


"open http://www.beast.bham.ac.uk/research/mulch.shtml".unixCmd;

});*/
MainMenu.register(Action("Go to BEASTmulch Home Page", {"open http://www.birmingham.ac.uk/facilities/ea-studios/research/mulch.aspx".unixCmd;}), helpMenu);
}.defer(1);
}).play(AppClock);
)
