// class for storing some library wide options
BMOptions {
	classvar <>crossfade = 0.1;
}

// Defines the minimum interface for an AudioChainElement

BMAbstractAudioChainElement {
	var <ins, <outs, <inNames, <outNames; // in the default case the getters return nil, as an element need not have both ins and outs
	var <group, <>server, name, <callCmdPeriod = true;
	
	// for chain construction in BMAudioChainManager
	// overriding methods in subclasses should have these args, but may ignore them
	// if appropriate
	
	// maybe don't need two audio arrays here
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.subclassResponsibility(thisMethod);
	}
	
	// this should return an instance of our default GUI class
	// which builds the window itself
	gui { ^this.subclassResponsibility(thisMethod); } 
	
	// this should recreate our group
	cmdPeriod { ^this.subclassResponsibility(thisMethod); } 
	
	callCmdPeriod_ { |bool| } // maybe change this later
	
	makeGroup { ^this.subclassResponsibility(thisMethod); }
	
	name { ^name ? this.class.name } // this is probably a bad idea...
}

BMAbstractAudioSource : BMAbstractAudioChainElement {

	// sources are not instantiated by the chain manager
	*newFromChain { |controllerArray, inAudioArray, outAudioArray, group, server, name| 
		^this.shouldNotImplement(thisMethod);
	}
	
	asInOutArray { ^this.subclassResponsibility(thisMethod);}
}

BMAbstractController {
	classvar <allControllers;
	
	*initClass {
		allControllers = IdentityDictionary.new;
	}
	
	*dumpAllValues {
		^allControllers.collect({|elem, key| key->(elem.getAllFaders)});
	}

}

BMAbstractGUI {
	var <name, <window;
	var onClose;
	
	onClose_{|func|
		onClose = onClose.addFunc(func);
	}
	
	makeWindow { ^this.subclassResponsibility(thisMethod);  }
}