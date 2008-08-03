// maybe 'mappings' should be preset and used throughout the library


// class for storing some library wide options
BMOptions {
	classvar <>crossfade = 0.1;
	classvar <>numInputBusChannels = 12;
	classvar <>numOutputBusChannels = 96;
}

// Defines the minimum interface for an AudioChainElement

BMAbstractAudioChainElement {
	classvar <allChainElements;
	var <ins, <outs, <inNames, <outNames; // in the default case the getters return nil, as an element need not have both ins and outs
	var <group, <>server, <name, <callCmdPeriod = true;
	
	// for chain construction in BMAudioChainManager
	// overriding methods in subclasses should have these args, but may ignore them
	// if appropriate
	
	// maybe don't need two audio arrays here
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.subclassResponsibility(thisMethod);
	}
	
	*initClass {
		allChainElements = ();
	}
	
	// this should return an instance of our default GUI class
	// which builds the window itself
	gui { ^this.subclassResponsibility(thisMethod); } 
	
	// this should recreate our group and do any other necessary bookkeeping
	cmdPeriod { ^this.subclassResponsibility(thisMethod); } 
	
	callCmdPeriod_ { |bool| } // maybe change this later
	
	free {CmdPeriod.remove(this);} // overrride to do more complicated things
	
	makeGroup { ^this.subclassResponsibility(thisMethod); }
	
	makeName { ^(this.class.name ++ UniqueID.next)} 
	
	release { allChainElements[name] = nil}

}

BMAbstractAudioSource : BMAbstractAudioChainElement {

	// sources are not instantiated by the chain manager
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.shouldNotImplement(thisMethod);
	}
	
	asBMInOutArray { ^this.subclassResponsibility(thisMethod);}
	
	mappings {
		
	}
	
	mappings_ {|mappings|
		
	}
}

// valueArray holds the controller value in its native form
// setFaderValue should convert to 0-1 and send to the bus 
BMAbstractController {
	classvar <allControllers, <allControls;
	var <name, <bus, <busIndex, valueArray, labelArray, <server, <numFaders;
	
//	*new {
//		^super.new.addValuesToIndex;
//	}
	
	*initClass {
		allControllers = IdentityDictionary.new;
		allControls = IdentityDictionary.new;
	}
	
	*dumpAllValues {
		^allControllers.collect({|elem, key| key->(elem.getAllFaders)});
	}
	
	*getValueByName{|ctrlName|
		^allControls[ctrlName].value;
	}
	
	*setValueByName{|ctrlName, val|
		allControls[ctrlName].value_(val);
	}
	
	addControlsToIndex {
		this.faderNames.do({|ctrlName, i|
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
	
	getInputArray {
		^this.faderNames.collectAs({|item, i| item.asSymbol -> (i + busIndex)}, BMInOutArray);
	}
	
	// perhaps this should be more generalised and named something else like 'preset'
	mappings {
		^IdentityDictionary[\labels->this.getAllLabels, \faders->this.getAllFaders];
	}
	
	mappings_ {|mappings|
		this.setAllLabels(mappings[\labels]);
		this.setAllFaders(mappings[\faders]);
	}
	
	acceptsAutomation { ^false }
}

// don't make these yourself
BMControl {
	var <name, <controller, <ctrlNum, <>mapped = false;
	
	*new {|name, controller, ctrlNum|
		^super.newCopyArgs(name, controller, ctrlNum);
	}
	
	value {^controller.getFaderVal(ctrlNum) }
	
	value_ {|val| controller.setFaderVal(ctrlNum, val) }
}

BMAbstractGUI {
	var <name, <window;
	var onClose;
	
	onClose_{|func|
		onClose = onClose.addFunc(func);
	}
	
	makeWindow { ^this.subclassResponsibility(thisMethod);  }
}