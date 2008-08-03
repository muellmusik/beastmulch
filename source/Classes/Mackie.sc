// uses a control bus on the server to map values
// bend goes from 0 to 16384 and is mapped to values between 0 and 1 (assumes amplitude for the moment)
// assumes MIDIClient is initialised

BMAbstractMackie : BMAbstractController {
	var <uid, <outPort, <outUid, <midiout, spec;
	var <>sysexHdr, faderRoutine;
	
	*new { |uid, name, server|
		^super.new.init(uid, name, server ? Server.default).addControlsToIndex;
	}
	
	startListening { 
		faderRoutine = Routine({
			var	event, port, channel, bend;
			loop {
				event = MIDIIn.waitBend(uid);
				this.updateValue(event.chan, event.b);
			}
		}).play;
	}
	
	init { |arguid, argname, argserver|
		var titleArray, nameString;
		uid = arguid;
		name = argname;
		server = argserver.postln;
		("Server: " ++ server).postln;
		this.setNumFaders;
		this.setSysexHdr;
		valueArray = Array.fill(numFaders, {0});
		labelArray = Array.fill(numFaders, {"      "});
		bus = Bus.control(server, numFaders);
		busIndex = bus.index;
		this.setOutUid.startListening;
		midiout = MIDIOut(outPort, outUid);
		// turn off touch sensitive faders
		midiout.sysex(outUid, sysexHdr ++ Int8Array[16r0c, 1, 16rf7]);
		nameString = "Controller" + name.asString + "/////////////// Welcome to BEAST";
		nameString = nameString ++ String.fill(111 - nameString.size, {$ });
		titleArray = (sysexHdr ++ Int8Array[16r12, 0] 
			++ nameString.collectAs({arg item; item.ascii}, Int8Array)).add(16rf7);
		midiout.sysex(outUid, titleArray);
		//spec = [1, 16385, -3].asSpec; // exp so must offset values by 1
		spec = Env([0, 1], [16384], \sine);
		this.updateAllFaders(valueArray);
		allControllers[name] = this;
	}
	
	setNumFaders {
		^this.subclassResponsibility(thisMethod);
	}
	
	setSysexHdr {
		^this.subclassResponsibility(thisMethod);
	}
	
	setOutUid {
		MIDIClient.sources.do({ |source, i| 
			if(source.uid == uid, { 
				outUid = MIDIClient.destinations[i].uid;
				outPort = i;
			});	
		});
		outUid.isNil.if({("destination for" + uid + "not found.").warn});
	}
	
	updateValue { |chan, bend|
		var value;
		// map for amplitude
		//value = spec.unmap(bend.post + 1); // exp warp so can't have zero
		//" ".post;
		value = spec.at(bend);
		server.sendMsg("/c_set", busIndex + chan, value);
		valueArray[chan] = bend;
		midiout.bend(chan, bend); // loopback bend to fader
	}
		
	updateAllFaders { |array|
		array.do({|item, i| this.updateValue(i, item)});
	}
	
	// assumes fader 1 = 1 not 0
	// returns 14 bit value
	getFaderVal { |faderNum| ^valueArray[faderNum -1] }
	
	setFaderVal { |faderNum, val| this.updateValue(faderNum -1, val) }
	
	getAllFaders { ^valueArray }
	
	setAllFaders {|array| { array.do({|item, i| this.updateValue(i, item); 0.1.wait; }); }.fork;}
	
	setLabel { |fader, name|
		var label;
		//name.notNil.if({label = name.asString;}, {label = "      "});
		label = name.asString;
		if(label.size > 6, {"Label too long".warn; }, 
			{label = label ++ String.fill(6 - label.size, {$ })}); // pad to block
		label = label.copyFromStart(5);
		labelArray.put(fader - 1, label);
		midiout.sysex(outUid, sysexHdr ++ Int8Array[16r12, (fader - 1) * 7 + 16r38] 
			++ label.collectAs({arg item; item.ascii}, Int8Array).add(16rf7));
	}
	
	getLabel { |fader| ^labelArray[fader-1] }
	
	getAllLabels { ^labelArray }
	
	setAllLabels { |array| { array.do({|item, i| this.setLabel(i+1, item); 0.1.wait}); }.fork}
	
	// for faders
//	getInputArray {
//		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
//	}
	
	//faderNames {^Array.fill(numFaders, {|i| name.asString ++ "-" ++ (i+1)})}

	acceptsAutomation { ^true }
}

MackieCU : BMAbstractMackie {
	var buttonRoutine, buttonOffRoutine, buttonFuncDict, masterFaderSynth;
	
	init { |arguid, argname, argserver|
		super.init(arguid, argname, argserver);
		this.addMasterFaderSynth;
		CmdPeriod.add(this)
	}
	
	// a little hacky but works
	addMasterFaderSynth {
		masterFaderSynth = {
			ReplaceOut.ar(0, In.ar(0, server.options.numOutputBusChannels) 
				* In.kr(busIndex + numFaders - 1, 1));
		}.play(RootNode(server), addAction: \addToTail);
	}
	
	cmdPeriod {this.addMasterFaderSynth;}
	
	startListening {
		buttonFuncDict = IdentityDictionary.new;
		buttonRoutine = Routine({
			var	event, port, channel, bend;
			loop {
				event = MIDIIn.waitNoteOn(uid);
				buttonFuncDict[event.note.asSymbol].value;
			}
		}).play;
		buttonOffRoutine = Routine({
			var	event, port, channel, bend;
			loop {
				event = MIDIIn.waitNoteOff(uid);
				buttonFuncDict[(\off ++ event.note).asSymbol].value;
			}
		}).play;
		super.startListening;
	}
	
	playFunc_ {|func| buttonFuncDict[\94] = func; }
	stopFunc_ {|func| buttonFuncDict[\93] = func; }
	ffFunc_ {|onfunc, offfunc| buttonFuncDict[\92] = onfunc; buttonFuncDict[\off92] = offfunc }
	fbFunc_ {|onfunc, offfunc| buttonFuncDict[\91] = onfunc; buttonFuncDict[\off91] = offfunc }
	
	setNumFaders { numFaders = 9; }
	
//	faderNames { ^Array.fill(numFaders - 1, {|i| name.asString ++ "-" ++ (i+1)})
//		//.add(name.asString ++ "-mstr")
//	}
	
	// later allow for precision
	setTimeString {|timeString|
		var message;
		message = Int8Array.new;
		timeString.copyFromStart(9)
			.collectAs({arg item; item}, Array)
			.select({|item| item.isDecDigit})
			.collectAs({arg item; item.digit}, Int8Array)
			.reverse
			.do({|item, i| message = message.addAll(Int8Array[16r42 + i, 16r30 + item])});
		// add decimal
		message.put(3, message[3] + 16r40);
		message = message.copyToEnd(2);
		midiout.sysex(outUid, sysexHdr.copy.add(16rB0).addAll(message).add(-9));
	}
	
	setSysexHdr {
		sysexHdr = Int8Array[ -16, 0, 0, 102, 20];
	}
	
	update {|changed, what ...args|
	
		if(what == \time, { this.setTimeString(args.first.getTimeString) });
		if(what == \stop, { SystemClock.sched(1.0, {this.setTimeString(0.getTimeString)}) });
	}

}

MackieTimeDispatcher {
	var uid, port, midiout, sysexHdr;
	
	*new{|uid, port|
		^super.newCopyArgs(uid, port).init;
	}
	
	init {
		midiout = MIDIOut(port, uid);
		sysexHdr = Int8Array[ -16, 0, 0, 102, 20];
	}
	
	
}

MackieXT : BMAbstractMackie {

	setNumFaders { numFaders = 8; }
	
	setSysexHdr {
		sysexHdr = Int8Array[ -16, 0, 0, 102, 21];
	}

}

MackieCUNoMaster : BMAbstractMackie {

	setNumFaders { numFaders = 9; }
	
	setSysexHdr {
		sysexHdr = Int8Array[ -16, 0, 0, 102, 20];
	}

}

// interfaces is an array
MackieLabelsGUI {
	var interfaces, window, <>onClose, nameField, faderList, masterDict, faderNames;
	
	*new {|interfaces, origin|
		^super.newCopyArgs(interfaces).init.makeWindow(origin ? (40@200));
	}
	
	init {
		masterDict = IdentityDictionary.new;
		faderNames = Array.new(interfaces.size * 8 + 1);
		interfaces.do({|interface|
			var names;
			names = interface.faderNames;
			faderNames = faderNames.addAll(names);
			names.do({|name, i| masterDict.add(name.asSymbol -> [interface, i + 1])});
		});
	}
	
	makeWindow { |origin|
		var x, y;
		x = origin.x;
		y = origin.y;
		
		window = SCWindow("Fader Labels", Rect.new(x, y, 220, 350), false);
		window.view.decorator = FlowLayout(window.view.bounds, Point(10, 10), Point(10, 10));
		
		SCStaticText.new(window, Rect(10,10,100,40)).font_(Font("CrushNo47", 20)).string = "Faders";
		window.view.decorator.nextLine;
		faderList = SCListView(window, Rect(0, 0, 200, 200)).canReceiveDragHandler = false;
		faderList.action = {
			var info, string;
			info = masterDict[faderList.item.asSymbol];
			string = info[0].getLabel(info[1]);
			nameField.string = if(string == "      ", {""}, {string});
		};
		faderList.items = faderNames.asArray;
		faderList.background = HiliteGradient(Color.green.alpha_(0.3), Color.blue.alpha_(0.9),
			steps: 256, frac: 0.66);
		window.view.decorator.nextLine;	
		
		nameField = SCTextField(window, Rect(10,10,100,20));
		nameField.action = {|view|
			var info;
			info = masterDict[faderList.item.asSymbol];
			info[0].setLabel(info[1], view.value);
			faderList.focus;
		};
		SCStaticText.new(window, Rect(10,10,80,20)).font_(Font("CrushNo47", 14)).string = "Fader Label";
		
		faderList.doAction;
		window.onClose = { onClose.value(this)};	
		window.front;	
	}
	
}