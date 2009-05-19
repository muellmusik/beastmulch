// uses a control bus on the server to map values
// bend goes from 0 to 16384 and is mapped to values between 0 and 1 (assumes amplitude for the moment)
// assumes MIDIClient is initialised

BMAbstractMackie : BMAbstractController {
	var <uid, <outPort, <outUid, <midiout;
	var <>sysexHdr, faderRoutine;
	
	*new { |uid, name, server|
		^super.new.init(uid, name, server ? Server.default).addControlsToIndex;
	}
	
	*newFromParamDict {|dict, server| 
		^this.new(dict[\uid], dict[\name], server);
	}
	
	*parameterList { 
		var class;
		class = this;
		^(
			name: [Symbol, {class.makeName}, "Name"],
			uid: [Integer, [-inf, inf, \linear, 1, 0].asSpec, "MIDI Source uid"]
		); 
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
		this.setNumControls;
		this.setSysexHdr;
		valueArray = Array.fill(numControls, {0});
		labelArray = Array.fill(numControls, {"      "});
		bus = Bus.control(server, numControls);
		busIndex = bus.index;
		this.setOutUid.startListening;
		midiout = MIDIOut(outPort, outUid);
		// turn off touch sensitive faders
		midiout.sysex(sysexHdr ++ Int8Array[16r0c, 1, 16rf7]);
		nameString = "Controller" + name.asString + "/////////////// Welcome to BEASTmulch";
		nameString = nameString ++ String.fill(111 - nameString.size, {$ });
		titleArray = (sysexHdr ++ Int8Array[16r12, 0] 
			++ nameString.collectAs({arg item; item.ascii}, Int8Array)).add(16rf7);
		midiout.sysex(titleArray);
		spec = [0, 16384, 'cos', 0.0].asSpec;
		this.updateAllFaders(valueArray);
		allControllers[name] = this;
	}
	
	setNumControls {
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
		value = spec.map(bend);
		server.sendMsg("/c_set", busIndex + chan, bend);
		valueArray[chan] = value;
		midiout.bend(chan, value); // loopback bend to fader
	}
		
	updateAllFaders { |array|
		array.do({|item, i| this.updateValue(i, item)});
	}
	
	// assumes fader 1 = 1 not 0
	// returns value between 0 and 1
	getVal { |controlNum| ^spec.unmap(valueArray[controlNum -1]) }
	
	setVal { |controlNum, val| this.updateValue(controlNum -1, val) }
	
	getAllValues { ^valueArray.collect({|val| spec.unmap(val)}) }
	
	setAllValues {|array| array.do({|item, i| this.updateValue(i, item); });}
	
	setLabel { |controlNum, name|
		var label;
		//name.notNil.if({label = name.asString;}, {label = "      "});
		label = name.asString;
		if(label.size > 6, {"Label too long".warn; }, 
			{label = label ++ String.fill(6 - label.size, {$ })}); // pad to block
		label = label.copyFromStart(5);
		labelArray.put(controlNum - 1, label);
		midiout.sysex(sysexHdr ++ Int8Array[16r12, (controlNum - 1) * 7 + 16r38] 
			++ label.collectAs({arg item; item.ascii}, Int8Array).add(16rf7));
	}
	
	getLabel { |controlNum| ^labelArray[controlNum-1] }
	
	getAllLabels { ^labelArray }
	
	setAllLabels { |array| { array.do({|item, i| this.setLabel(i+1, item); 0.1.wait}); }.fork}
	
	// for faders
//	getInputArray {
//		^this.controlNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
//	}
	
	//controlNames {^Array.fill(numControls, {|i| name.asString ++ "-" ++ (i+1)})}

	acceptsAutomation { ^true }
}

BMMackieCU : BMAbstractMackie {
	var buttonRoutine, buttonOffRoutine, buttonFuncDict, masterFaderSynth;
		
	*humanName {  ^"Mackie CU"  }
	
	init { |arguid, argname, argserver|
		super.init(arguid, argname, argserver);
	}
	
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
	
	setNumControls { numControls = 9; }
	
//	controlNames { ^Array.fill(numControls - 1, {|i| name.asString ++ "-" ++ (i+1)})
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
		midiout.sysex(sysexHdr.copy.add(16rB0).addAll(message).add(-9));
	}
	
	setSysexHdr {
		sysexHdr = Int8Array[ -16, 0, 0, 102, 20];
	}
	
	update {|changed, what ...args|
	
		if(what == \time, { this.setTimeString(args.first.getTimeString) });
		if(what == \stop, { SystemClock.sched(1.0, {this.setTimeString(0.getTimeString)}) });
	}

}


BMMackieXT : BMAbstractMackie {

	*humanName {  ^"Mackie XT"  }

	setNumControls { numControls = 8; }
	
	setSysexHdr {
		sysexHdr = Int8Array[ -16, 0, 0, 102, 21];
	}

}

