BMTrimPluginsRack : BMAbstractAudioChainElement {
	
	var strips;
	
	*new { |ins, group, server, name|
		^super.new.init(ins, group, server ? Server.default, name ? this.name);
		// default name is class
	}
	
	init {|argins, arggroup, argserver, argname|
		ins = argins;
		outs = argins;
		group = arggroup;
		server = argserver;
		name = argname;
		inNames = ins.keys;
		outNames = outs.keys;
		if(group.isNil, {this.makeGroup});
		strips = ();
		inNames.do({|chanName|
			strips[chanName] = BMTrimPluginsStrip(group, ins[chanName]);
		});
		CmdPeriod.add(this);
	}

	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.new(inAudioArray, group, server, name);
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
				strip.makeNodes;
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
	
}

BMTrimPluginsStrip {
	var <trim = 0, trimSynth, <plugins, <target, <server, <group, <input;
	
	*new {|target, input|
		^super.new.init(target, input);
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
		var x, y, width, pluglist, numTypes, numStrips, stripGUIs;
		x = origin.x;
		y = origin.y;
		width = 4 + 170 + 4 + min(104 * trimPluginsRack.ins.size, 1078); // max 7 visible
		window = SCWindow(name, Rect.new(x, y, width, 608), false);
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
		stripGUIs = SCHLayoutView(stripGUIs, Rect(4, 4, 104 * numStrips + 4, 500));
		trimPluginsRack.inNames.do({|chanName|
			trimPluginsStripGUIs.add(
				BMTrimPluginsStripGUI(trimPluginsRack[chanName], stripGUIs, chanName)
			);
		});
		defaultHelpString = "Click names at left for description.\nDrag from left to add plugins.\nSelect and press enter to edit plugin settings.\nCmd down and up arrows to change order.\nCmd drag to copy a plugin and its settings to another channel.";
		window.view.decorator.nextLine;
		window.view.decorator.shift(20, 0);
		
		descriptionHelpText = SCStaticText(window, Rect(0, 0, width - 58, 80))
			.string_(defaultHelpString)
			.font_(Font("Helvetica-Bold", 12));
		TriggerView(window, Rect(0, 0, 20, 20))
			.caption_(" ?")
			.font_(Font("Helvetica-Bold", 14))
			.action_({descriptionHelpText.string = defaultHelpString;});

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
	 	containerView = SCCompositeView(parent, Rect(origin.x, origin.y, 100, 500));
	 	containerView.decorator = FlowLayout(containerView.bounds);
	 	labelView = SCStaticText(containerView, Rect(0, 0, 100, 30))
	 		.font_(Font("Helvetica-Bold", 14))
	 		.background_(Color.grey.alpha_(0.3))
	 		.string_(" " ++ name);
	 	ezKnob = EZKnob(containerView, 50@20, " Trim (dBFS)", \db.asSpec, 
	 		{|ez| trimPluginsStrip.trim_(ez.value);}, trimPluginsStrip.trim, false, 96, 70);
	 	ezKnob.labelView.align_(\left);
	 	ezKnob.numberView.boxColor_(Color.white.alpha_(0.3));
	 	listView = SCListView(containerView, Rect(0, 0, 100, 334))
	 		.items_(trimPluginsStrip.plugins.collect({|plugin| plugin.spec.name}));
	 	listView.enterKeyAction = {trimPluginsStrip.plugins[listView.value].gui }; // can duplicate
	 	listView.keyDownAction = { arg view,char,modifiers,unicode,keycode;
			if((modifiers == 11534600) && (unicode == 63233), {
				trimPluginsStrip.movePluginDown(listView.value);
			});
			if((modifiers == 11534600) && (unicode == 63232), {
				trimPluginsStrip.movePluginUp(listView.value);
			});
			if(unicode == 127, {trimPluginsStrip.removePlugin(listView.value)});
			listView.defaultKeyDownAction(char,modifiers,unicode);
		};
		listView.canReceiveDragHandler = { SCView.currentDrag.isKindOf(BMPlugin) };
		listView.receiveDragHandler = { trimPluginsStrip.addPlugin(SCView.currentDrag) };
		listView.beginDragAction = { trimPluginsStrip.plugins[listView.value].copy };
	 }
	 
	 update {|tpv, what|
	 	if(what == \trim, {ezKnob.value = trimPluginsStrip.trim;});
	 	listView.items_(trimPluginsStrip.plugins.collect({|plugin| plugin.spec.name}));
//	 	switch(what,
//	 		\moveDown, {listView.value = listView.value + 1},
//	 		\moveUp, {listView.value = listView.value - 1}
//	 	)
	 }

}