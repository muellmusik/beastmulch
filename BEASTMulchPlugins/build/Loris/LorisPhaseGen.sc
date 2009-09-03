LorisPhaseGen : UGen { // envelope generator	
	*ar { arg envelope, gate = 1.0, levelScale = 1.0, levelBias = 0.0, timeScale = 1.0, doneAction = 0;
		^this.multiNewList(['audio', gate, levelScale, levelBias, timeScale, doneAction, `envelope])
	}
	*kr { arg envelope, gate = 1.0, levelScale = 1.0, levelBias = 0.0, timeScale = 1.0, doneAction = 0;
		^this.multiNewList(['control', gate, levelScale, levelBias, timeScale, doneAction, `envelope])
	}
	*new1 { arg rate, gate, levelScale, levelBias, timeScale, doneAction, envelope;

		^super.new.rate_(rate).addToSynth.init([gate, levelScale, levelBias, timeScale, doneAction] 
			++ envelope.dereference.asArray); 
	}
 	init { arg theInputs;
 		// store the inputs as an array
 		inputs = theInputs;
 	}
	argNamesInputsOffset { ^2 }
}