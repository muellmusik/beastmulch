// n channels of decorrelated reverb
// wet only
BMMultiFreeVerb {
	
	*arV{|numChans, in, room = 0.5, damp = 0.5, dev = 0.01, mul = 1.0, add = 0.0|
		var scal;
		scal = -3.dbamp.pow(numChans - 1) * mul // compensate amplitude for numChans
		^Array.fill(numChans, {FreeVerb.ar(in, 1, room + dev.rand2, damp + dev.rand2, scal, add)})
	}

	*arA{|numChans, in, room = 0.5, damp = 0.5, mul = 1.0, add = 0.0|
		var scal, verb;
		scal = -3.dbamp.pow(numChans - 1) * mul; // compensate amplitude for numChans
		verb = FreeVerb.ar(in, 1, room, damp);
		^Array.fill(numChans, {AllpassL.ar(verb, 0.02, LFNoise2.kr(100).range(0.01, 0.01005), 0.01, scal) })
	}
	
	*ar{|numChans, in, room = 0.5, damp = 0.5, decor = 0.5, mul = 1.0, add = 0.0|
		var scal, verb, bufs, copies;
		scal = -3.dbamp.pow(numChans - 1) * mul; // compensate amplitude for numChans
		verb = FreeVerb.ar(in, 1, room, damp);
		//bufs = {LocalBuf(max(128, ControlDur.ir * SampleRate.ir * 2))} ! numChans;
		bufs = {LocalBuf(128, 1)} ! numChans;
		verb = FFT(bufs[0], verb);
		verb = {|i| PV_Copy(verb, bufs[i + 1]) } ! (numChans - 1) ++ [verb];
		verb = PV_Decorrelate(verb, 1, 1);
		^IFFT(verb) * scal;
	}

} 
		
		