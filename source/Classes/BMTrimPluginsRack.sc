BMTrimPluginsRack : BMAbstractAudioChainElement {
	
	var strips;
	
	*new { |ins, group, server, name|
		^super.new.init(ins, group, server ? Server.default, name);
		// default name is class
	}
	
	init {|argins, arggroup, argserver, argname|
		ins = argins;
		outs = argins;
		group = arggroup;
		server = argserver;
		name = argname  ? this.makeName;
		inNames = ins.keys;
		outNames = outs.keys;
		if(group.isNil, {this.makeGroup});
		strips = ();
		inNames.do({|chanName|
			strips[chanName] = BMTrimPluginsStrip(group, ins[chanName]);
		});
		CmdPeriod.add(this);
		allChainElements[name] = this;
	}

	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.new(inAudioArray, group, server, name);
	}
	
	mappings { ^strips.collect({|strip, name| strip.mappings});}
	
	mappings_ { |dict| dict.keysValuesDo({|name, mappings| 
		strips[name].notNil.if({
			strips[name].mappings_(mappings)});
		},{ error("Plugin Strip:" + name + "not defined.") });
	}
	
	at { |channel| ^strips[channel] }
	
	// this should return an instance of our default GUI class
	// which builds the window itself
	gui { ^BMTrimPluginsRackGUI(this) } 
	
	cmdPeriod {
		server.makeBundle(nil, { 
			server.sync;
			this.makeGroup;
			server.sync;
			strips.do({|strip|
				strip.target = group;
				strip.clear;
				server.sync;
			});
		});
		//this.changed;
	} 
	
	callCmdPeriod_ { |bool| 
		bool.if({ CmdPeriod.add(this); }, {CmdPeriod.remove(this);});
		callCmdPeriod = bool;
	}
	
	makeGroup { group = Group.tail(server); }
	
	free {
		strips.do{| pluginsStrip | 
			pluginsStrip.plugins.do{| plugin, i | 
				pluginsStrip.removePlugin(i) 
			} 
		};
		SystemClock.sched(BMOptions.crossfade, { group.free; group = strips = nil });
		CmdPeriod.remove(this)
		
	}

	
	////// Automated Stuff
	
	// add delays to eliminate precedence effect
	// assumes distances are in meters
	compensateDistance { 
		var rads, diff, farthest, plugin;
		
		rads = ins.select({|in| in.value.isBMSpeaker}).collect({|speaker| speaker.value.rad });
		farthest = rads.maxItem;
		ins.do({|speaker| 
			speaker.value.isBMSpeaker.if({
				diff = farthest - speaker.value.rad;
				if(diff > 0, { // farthest uncompensated
					// speed of sound 344 m/s at 21 degrees C in dry air
					plugin = BMPlugin('Distance Compensate').set(\delayTime, diff / 344);
					this[speaker.value.name].addPlugin(plugin); 
				});
			});
		});
	}
	
	// auto add plugins by speaker spec
	// requires a plugin spec name and a preset
	autoPlugins { 
		var plugin;
		ins.do({|speaker|
			speaker.value.isBMSpeaker.if({
				speaker.value.spec.plugins.do({|plgin| 
					// name, preset
					plugin = BMPlugin(plgin[0]).preset_(plgin[1]);
					this[speaker.value.name].addPlugin(plugin); 
				});
			});
		});
	}
	
	// balance speakers
	autoTrim { 
		var powered, min, diff;
		
		min = ins.select({|in| in.value.isBMSpeaker})
			.collect({|speaker| speaker.value.autoTrim })
			.minItem; 
		ins.do({|speaker| 
			speaker.value.isBMSpeaker.if({
				diff = min - speaker.value.autoTrim;
				if(diff < 0, { 
					this[speaker.value.name].trim_(diff); 
				});
			});
		});
	}
	
	// balance powered speakers
//	autoTrim { 
//		var powered, min, diff;
//		
//		ins.isSpeakerArray.if({
//			powered = ins.select({|speaker| 
//				speaker.value.spec.powered &&  speaker.value.spec.spl.notNil;
//			});
//			min = powered.collect({|speaker| speaker.value.spec.spl }).minItem; 
//			powered.do({|speaker| 
//				diff = min - speaker.value.spec.spl;
//				if(diff < 0, { 
//					this[speaker.value.name].trim_(diff); 
//				});
//			});
//		}, {"Not a BMSpeakerArray, can't auto trim".warn;}); 
//	}
}

BMTrimPluginsStrip {
	var <trim = 0, trimSynth, <plugins, <target, <server, <group, <input;
	
	*new {|target, input|
		^super.new.init(target, input);
	}
	
	clear { 
		plugins = List.new;
		this.makeNodes;
	}
	
	init {|argtarget, arginput|
		target = argtarget.asGroup;
		server = target.server;
		input = arginput;
		
		plugins = List.new;
		target.server.makeBundle(nil, {
			this.sendDef;
			server.sync;
			this.makeNodes; // first time only trim...
		});
	}
	
	mappings { 
		var dict;
		dict = IdentityDictionary.new;
		dict[\trim] = trim;
		dict[\plugins] = plugins.collect({|plugin|
			// could be a problem if pluginspec changes in the meantime
			[plugin.spec.name, plugin.numChannels, plugin.attributes, plugin.values];
		}); // these are in order
		^dict;
	}
	
	mappings_ { |dict| 
		this.plugins.do({|plugin| plugin.release;});
		plugins = List.new;
		this.trim_(dict[\trim]);
		dict[\plugins].do({|pluginArray|
			var plugin;
			plugin = BMPlugin(pluginArray[0], pluginArray[1], server, pluginArray[2]);
			this.addPlugin(plugin);
			pluginArray[3].keysValuesDo({|k, v| plugin.set(k, v)});
		});
		this.changed;
	}
	
	sendDef {
		SynthDef("BMTrim", {arg in, trim = 0, gate = 1;
			// XFade in new scaled value, crossfade out when freeing so no clicks
			XOut.ar(in, 
				EnvGen.kr(Env.asr(BMOptions.crossfade, 1, BMOptions.crossfade), gate, 
					doneAction: 2),
				In.ar(in, 1) * trim
			);
		}).send(target.server);
	}
	
	target_{|argtarget|
		target = argtarget.asGroup; 
		(target.asTarget.server != server).if({
			Error("Target server does not match Plugins' server.").throw;
		});
	
	}
	
	makeNodes { 
		server.makeBundle(nil, {
			group = Group.new(target);
			trimSynth = Synth.tail(group, "BMTrim", [in: input, trim: trim.dbamp]);
			plugins.do({|plgin|
				plgin.makeSynth(input, group, \addToTail);
			});
		});
		this.changed;
	}
	
	addPlugin {|plugin|
		plugins.add(plugin);
		server.makeBundle(nil, {
			server.sync; // wait for the plugin's def to arrive...
			plugin.makeSynth(input, group, \addToTail);
			// added at end, no need to reset order on server
			this.changed;
		});
	}
	
	removePlugin {|index|
		var toBeRemoved;
		toBeRemoved = plugins.removeAt(index);
		toBeRemoved.release; // free synth and resources
		// just removed, no need to reset order on server
		this.changed;
	}
	
	movePluginUp {|index|
		if(index > 0, {
			plugins.swap(index, index - 1);
			this.resetOrder;
			this.changed(\moveUp);
		});
	}

	movePluginDown {|index|
		if(index < (plugins.size -1), {
			plugins.swap(index, index + 1);
			this.resetOrder;
			this.changed(\moveDown);
		});
	}
	
	resetOrder {
		server.makeBundle(nil, {
			trimSynth.moveToTail(group);
			plugins.do({|plgin|
				plgin.synth.moveToTail(group);
			});
		});
	}
	
	trim_ {|newTrim| //in dB
		trim = newTrim;
		trimSynth.set(\trim, trim.dbamp);
		this.changed(\trim);
	}
}

BMTrimPluginsRackGUI : BMAbstractGUI {
	var trimPluginsRack, trimPluginsStripGUIs, defaultHelpString, descriptionHelpText;
	
	*new {|trimPluginsRack, name, origin|
		^super.new.init(trimPluginsRack, name ? trimPluginsRack.name)
			.makeWindow(origin ? (40@200));
	}
	
	init {|argtrimPluginsRack, argname|
		trimPluginsRack = argtrimPluginsRack;
		name = argname;
		trimPluginsStripGUIs = List.new;
	}
	
	makeWindow {|origin|
		var x, y, width, pluglist, numTypes, numStrips, stripGUIs, buttons;
		x = origin.x;
		y = origin.y;
		width = 4 + 170 + 4 + min(104 * trimPluginsRack.ins.size, 1078); // max 7 visible
		window = SCWindow(name, Rect.new(x, y, width, 618), false);
		window.view.decorator = FlowLayout(window.view.bounds);
		pluglist = SCScrollView(window, Rect(0, 0, 160, 508))
			.hasHorizontalScroller_(false)
			.hasBorder_(true);
		numTypes = BMPluginSpec.specs.size;
		numStrips = trimPluginsRack.ins.size;
		pluglist = SCVLayoutView(pluglist, Rect(4,4,150, numTypes * 24 + 4));
		BMPluginSpec.specs.keysDo({|piName| 
			SCDragSource(pluglist, Rect(0, 0, 150, 20)).string_("   " ++ piName.asString)
				.background_(Color.grey.alpha_(0.2))
				.font_(Font("Helvetica-Bold", 12))
				.beginDragAction_({BMPlugin(piName, 1)}) // one channel for now
				.mouseDownAction_({
					descriptionHelpText.string = piName ++ ": " ++ 
						BMPluginSpec.specs[piName].description;
				});
		});
		stripGUIs = SCScrollView(window, Rect(0, 0, width - 174, 508))
			.hasVerticalScroller_(false)
			.hasBorder_(true);
		stripGUIs.action = {window.refresh};
		//stripGUIs = SCHLayoutView(stripGUIs, Rect(4, 4, 104 * numStrips + 4, 500));
		stripGUIs = SCCompositeView(stripGUIs, Rect(4, 4, 104 * numStrips + 4, 500));
		stripGUIs.decorator = FlowLayout(stripGUIs.bounds, 0@0);
		trimPluginsRack.inNames.do({|chanName|
			trimPluginsStripGUIs.add(
				BMTrimPluginsStripGUI(trimPluginsRack[chanName], stripGUIs, chanName)
			);
		});
		defaultHelpString = "Click names at left for description.\nDrag from left to add plugins.\nDouble-click or select and press enter to edit plugin settings.\nCmd down and up arrows to change order.\nCmd drag to copy trim or a plugin and its settings to another channel.";
		window.view.decorator.nextLine;
		window.view.decorator.shift(20, 0);
		
		descriptionHelpText = SCStaticText(window, Rect(0, 0, width - 58, 100))
			.string_(defaultHelpString)
			.font_(Font("Helvetica-Bold", 12));
		
		buttons = SCVLayoutView(window, Rect(0, 0, 20, 70));
		TriggerView(buttons, Rect(0, 0, 20, 20))
			.string_(" ?")
			.font_(Font("Helvetica-Bold", 14))
			.colorOn_(Color.white.alpha_(0.2))
			.action_({descriptionHelpText.string = defaultHelpString;});
		TriggerView(buttons, Rect(0, 0, 20, 20))
			.string_("APi")
			.font_(Font("Helvetica-Bold", 8))
			.colorOn_(Color.white.alpha_(0.2))
			.action_({|v|v.value.if{trimPluginsRack.autoPlugins}});
		TriggerView(buttons, Rect(0, 0, 20, 20))
			.string_("ATr")
			.font_(Font("Helvetica-Bold", 8))
			.colorOn_(Color.white.alpha_(0.2))
			.action_({|v| v.value.if{trimPluginsRack.autoTrim}});
		TriggerView(buttons, Rect(0, 0, 20, 20))
			.string_("dT")
			.font_(Font("Helvetica-Bold", 12))
			.colorOn_(Color.white.alpha_(0.2))
			.action_({|v|v.value.if{trimPluginsRack.compensateDistance}});

		window.onClose = { 
			trimPluginsStripGUIs.do({|tpisg|
				tpisg.trimPluginsStrip.removeDependant(tpisg);
			});	
			onClose.value(this);
		};
		window.front;
	}
}

// only in a larger GUI
BMTrimPluginsStripGUI {
	var <trimPluginsStrip, containerView, ezKnob, labelView, listView;
	
	*new { |trimPluginsStrip, parent, name, origin|
		^super.new.init(trimPluginsStrip, parent).makeGUI(parent, name, origin ? 0@0);
	 }
	 
	 init {|argtrimPluginsStrip|
	 	trimPluginsStrip = argtrimPluginsStrip;
	 	trimPluginsStrip.addDependant(this);
	 }
	 
	 makeGUI{|parent, name, origin|
	 	name.postln;
	 	containerView = SCCompositeView(parent, Rect(origin.x, origin.y, 100, 500));
	 	containerView.decorator = FlowLayout(containerView.bounds);
	 	labelView = SCStaticText(containerView, Rect(0, 0, 100, 30))
	 		.font_(Font("Helvetica-Bold", 13))
	 		.background_(Color.grey.alpha_(0.3))
	 		.string_(" " ++ name);
	 	ezKnob = EZKnob(containerView, 50@20, " Trim (dBFS)", \db.asSpec, 
	 		{|ez| trimPluginsStrip.trim_(ez.value);}, trimPluginsStrip.trim, false, 96, 70);
	 	ezKnob.labelView.align_(\left).font_(Font("Helvetica-Bold", 12));
	 	ezKnob.numberView.boxColor_(Color.white.alpha_(0.3));
	 	listView = SCListView(containerView, Rect(0, 0, 100, 334))
	 		.items_(trimPluginsStrip.plugins.collect({|plugin| plugin.spec.name}));
	 	listView.enterKeyAction = {
	 		var plgin;
	 		plgin = trimPluginsStrip.plugins[listView.value];
	 		plgin.notNil.if({plgin.gui}); 
	 	}; // can duplicate
	 	listView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
	 		block { |break|
				if((modifiers == 11534600) && (unicode == 63233), {
					trimPluginsStrip.movePluginDown(listView.value);
					break.value;
				});
				if((modifiers == 11534600) && (unicode == 63232), {
					trimPluginsStrip.movePluginUp(listView.value);
					break.value;
				});
				if(unicode == 127, {trimPluginsStrip.removePlugin(listView.value)});
				listView.defaultKeyDownAction(char,modifiers,unicode);
			}
		};
		listView.mouseDownAction = {|view, x, y, modifiers, buttonNumber, clickCount|
			if(clickCount == 2, {
				listView.enterKeyAction.value;
			});
		};
		listView.canReceiveDragHandler = { SCView.currentDrag.isKindOf(BMPlugin) };
		listView.receiveDragHandler = { trimPluginsStrip.addPlugin(SCView.currentDrag) };
		listView.beginDragAction = { trimPluginsStrip.plugins[listView.value].copy };
	 }
	 
	 update {|tpv, what|
	 	if(what == \trim, {ezKnob.value = trimPluginsStrip.trim;});
	 	listView.items_(trimPluginsStrip.plugins.collect({|plugin| plugin.spec.name}));
	 	switch(what,
	 		\moveDown, {listView.value = listView.value + 1},
	 		\moveUp, {listView.value = listView.value - 1}
	 	)
	 }

}