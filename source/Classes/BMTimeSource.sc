BMTimeSource {
	classvar timeReferences;
	
	var reference, lastTime, lastRate = 1, lastReferenceTime, <>useReferenceTimes = true;

	*new {|referenceName, startTime| 
		^super.newCopyArgs(timeReferences[referenceName], startTime).init;
	}
	
	init { reference.addDependant(this) }
	
	*initClass {
		timeReferences = IdentityDictionary.new;
	}
	
	*addReference {|ref|
		timeReferences[ref.name] = ref;
	}
	
	*timeReferences { ^timeReferences.keys }
	
	currentTime { 
		^if(lastTime.notNil, {
			lastTime + (Main.elapsedTime - lastReferenceTime * lastRate);
		}, {nil})
	}

	update {arg changed, what, time, rate, referenceTime; 
		if(what == \time, {
			(useReferenceTimes && lastReferenceTime.notNil).if({
				lastReferenceTime = referenceTime;
			}, {lastReferenceTime = Main.elapsedTime;}); // use 'now' if not
			
			lastTime = time;
			lastRate = rate;
		
		});
	}
}