BMTimeSources {
	classvar timeReferences;
	
	*initClass {
		timeReferences = IdentityDictionary.new;
	}
	
	*addReference {|ref|
		timeReferences[ref.name] = ref;
	}
	
	*timeReferences { ^timeReferences.keys }
	
	*currentTime { |time, rate, reference|
		^time + (Main.elapsedTime - reference * rate);
	}

}