// n channels of decorrelated reverb
// wet only
BMMultiFreeVerb {
	
	*ar{|numChans, in, room = 0.5, damp = 0.5, dev = 0.01, mul = 1.0, add = 0.0|
		var scal;
		scal = -3.dbamp.pow(numChans - 1) * mul // compensate amplitude for numChans
		^Array.fill(numChans, {FreeVerb.ar(in, 1, room + dev.rand2, damp + dev.rand2, scal, add)})
	}

} 
		
		