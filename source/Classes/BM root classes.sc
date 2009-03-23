// maybe 'mappings' should be preset and used throughout the library


// class for storing some library wide and BEASTmulch System options
BMOptions {
	classvar <>crossfade = 0.1;
	classvar <>numInputBusChannels = 12;
	classvar <>numOutputBusChannels = 96;
	classvar <>numWireBufs = 512; // can be complicated
	classvar <>numAudioBusChannels = 1024; // we need a lot
	classvar <>allowMultipleControlMappings = false;
	
	*defaultServerOptions {
		^ServerOptions.new
			.numWireBufs_(numWireBufs)
			.numAudioBusChannels_(numAudioBusChannels)
			.numOutputBusChannels_(numOutputBusChannels)
			.numInputBusChannels_(numInputBusChannels);
	}
}

// Defines the minimum interface for an AudioChainElement

BMAbstractAudioChainElement {
	classvar <allChainElements;
	var <ins, <outs, <inNames, <outNames; // in the default case the getters return nil, as an element need not have both ins and outs
	var <target, <addAction, <group, <>server, <name;
	
	// for chain construction in BMAudioChainManager
	// overriding methods in subclasses should have these args, but may ignore them
	// if appropriate
	
//	// maybe don't need two audio arrays here
//	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
//		^this.subclassResponsibility(thisMethod);
//	}
	
	// simplest case
//	*new { |target, addAction = \addToTail, name| 
//		^super.new.init(target, addAction, name);
//	}
	
	// simplest case
	initNameAndTarget {|argtarget, argaddAction, argname|
		target = argtarget.asTarget;
		server = target.server;
		addAction = argaddAction;
		name = argname ?? {this.makeName};
		allChainElements[name] = this;
		this.makeGroup;
	}
	
	*initClass {
		allChainElements = ();
	}
	
	// this should return an instance of our default GUI class
	// which builds the window itself
	gui { ^this.subclassResponsibility(thisMethod); } 
	
//	// this should recreate our group and do any other necessary bookkeeping
//	cmdPeriod { ^this.subclassResponsibility(thisMethod); } 
	
//	callCmdPeriod_ { |bool| } // maybe change this later
//	
//	free {CmdPeriod.remove(this);} // overrride to do more complicated things
	
	// this way if you make them in order
	makeGroup { group = Group.new(target, addAction); }
	
	makeName { ^(this.class.name ++ UniqueID.next)} 
	
	release { allChainElements[name] = nil}
	
	loadPiece { } // do nothing by default

}

// should all audio sources be timeReferences?
// should they support a rudimentary clock here?
BMAbstractAudioSource : BMAbstractAudioChainElement {

//	// sources are not instantiated by the chain manager
//	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
//		^this.shouldNotImplement(thisMethod);
//	}

//	// sources addToHead
////	*new { |target, addAction = \addToHead, name| 
////		^super.new.init(target, addAction, name);
////	}
	
	asBMInOutArray { ^this.subclassResponsibility(thisMethod);}
	
	mappings {
		
	}
	
	mappings_ {|mappings|
		
	}
	
	// experimental time ref support
	play { ^nil }
	
	pause { ^nil }
	
	stop { ^nil }
	
	togglePlay { ^nil }
	
	setTime { }
	
}

// valueArray holds the controller value in its native form
// setFaderValue should convert to 0-1 and send to the bus 
BMAbstractController {
	classvar <allControllers, <allControls;
	var <name, <bus, <busIndex, valueArray, labelArray, <server, <numFaders;
	var spec;
	
//	*new {
//		^super.new.addValuesToIndex;
//	}
	
	*initClass {
		allControllers = IdentityDictionary.new;
		allControls = IdentityDictionary.new;
		CmdPeriod.add(this);
	}
	
	*cmdPeriod {
		allControls.do({|v| v.mappedTo_(nil)});
	}
	
	*dumpAllValues {
		"\n///////////////////\nDumping all Controller Values\n".postln;
		allControllers.keysValuesDo({|key, elem| 
			(key ++ ": ").post;
			elem.getAllFaders.postcs;
			"\n".post
		});
		"///////////////////".postln;
	}
	
	*getValueByName{|ctrlName|
		^allControls[ctrlName].value;
	}
	
	*setValueByName{|ctrlName, val|
		allControls[ctrlName].value_(val);
	}
	
	*masterInOutArray {
		^allControllers.values.collect({|item| item.asBMInOutArray }).flat.as(BMInOutArray);
	}
	
	addControlsToIndex {
		this.faderNames.do({|ctrlName, i|
			ctrlName = ctrlName.asSymbol;
			allControls[ctrlName] = BMControl(ctrlName, this, i + 1);
		});
	}
	
	getFaderVal { |faderNum| ^this.subclassResponsibility(thisMethod) }
	
	setFaderVal { |faderNum, val| this.subclassResponsibility(thisMethod) }
	
	getAllFaders { ^this.subclassResponsibility(thisMethod) }
	
	setAllFaders {|array| this.subclassResponsibility(thisMethod)}
	
	setFaders {|array| this.subclassResponsibility(thisMethod)} 
	
	setLabel { |fader, name|
		this.subclassResponsibility(thisMethod)
	}
	
	// by default controllers have no labels
	getLabel { |fader| ^nil }
	
	getAllLabels { ^nil}
	
	setAllLabels { |array| }
	
	faderNames {^Array.fill(numFaders, {|i| name.asString ++ "-" ++ (i+1)})}
	
//	getInputArray {
//		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
//	}

	asBMInOutArray {
		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
	}
	
	// perhaps this should be more generalised and named something else like 'preset'
	mappings {
		^IdentityDictionary[\labels->this.getAllLabels, \faders->this.getAllFaders];
	}
	
	mappings_ {|mappings|
		mappings = mappings ? ();
		this.setAllLabels(mappings[\labels]);
		this.setAllFaders(mappings[\faders]);
	}
	
	acceptsAutomation { ^false }
	
	spec { ^spec.asSpec; }
	
	calibrate {
		("No calibration to do for " ++ name ++ ".").postln;
	} 
}

// don't make these yourself
BMControl {
	var <name, <controller, <ctrlNum, <mappedTo, <automator, <>lastAutomated;
	
	*new {|name, controller, ctrlNum|
		^super.newCopyArgs(name, controller, ctrlNum);
	}
	
	mappedTo_ {|to| mappedTo = to; this.changed(\mappedTo) }
	
	automator_ {|atmtr| automator = atmtr; this.changed(\automator) }
	
	value {^controller.getFaderVal(ctrlNum) }
	
	value_ {|val| controller.setFaderVal(ctrlNum, val) }
	
	controllerSpec { ^controller.spec }
	
	displaySpec { ^mappedTo.asSpec }
}

BMAbstractGUI {
	var <name, <window;
	var onClose;
	
	onClose_{|func|
		onClose = onClose.addFunc(func);
	}
	
	makeWindow { ^this.subclassResponsibility(thisMethod);  }
}