BEPartials {
	var <>size, <partialList, <dur = 0;

	*new { arg sdif;	
		^super.new.init(sdif);
	}
	
	init { arg sdif;	
		partialList = sdif.readFramesToPartials;	
		this.calcSizeAndDur;	
	}
	
	partialList_{|list|
		partialList = list;
		this.calcSizeAndDur;
	}
	
	calcSizeAndDur {
		size = partialList.size;
		partialList.do({|item| 
			var end;
			end = item[2].sum + item[0]; // duration
			dur = dur.max(end);
		});
	}
	
	// fades in or out partials with non-zero start and/or end amps
	fadeInOut {
		var fadein = 0.001, fadeout = 0.001; // loris standard
		partialList = partialList.collect({ arg partial;
			// fadein
			if(partial[3].first > 0,{
				partial[0] = partial[0] - fadein; // roll back startime slightly
				// roll back phase
				partial[1] = partial[1].insert(0, 
					partial[1].first - (2pi * partial[4].first * fadein)
				);
				
				partial[2] = partial[2].insert(0, fadein); // short fadein time segment
				partial[3] = partial[3].insert(0, 0); // amp zero
				partial[4] = partial[4].insert(0, partial[4].first); // extra freq
				partial[5] = partial[5].insert(0, partial[5].first); // extra bw
			});
			
			// fadeout
			if(partial[3].last > 0,{
				// extra phase
				partial[1] = partial[1].add(partial[1].last + (2pi * partial[4].last * fadeout));
				partial[2] = partial[2].add(fadeout); // short fadeout segment
				partial[3] = partial[3].add(0); // amp zero
				partial[4] = partial[4].add(partial[4].last); // extra freq
				partial[5] = partial[5].add(partial[5].last); // extra bw
			});
			partial
		});
	
	}
	
	ar {| stretch = 1, pitch = 1, bw, ioff = 0|
		var envs, recipStretch;
		stretch = stretch.value; // could be a function
		pitch = pitch.value;
		bw = bw.value;
		
		recipStretch = stretch.reciprocal; // calculate only once
		this.fadeInOut; // fade in and out non-zero partialtimes
		
		partialList.do({ arg item, i;
			var starttime, times, amps, phases, numSegs, theseEnvs, thisDelay, phaseEnv;
			starttime = item[0];
			// correct times for fadeins by compensating for stretch
			numSegs = item[2].size;
			times = Array.new(numSegs);
			amps = item[4];
			phases = Array.new(numSegs + 1);
			amps.do({|amp, j|
				if(j < numSegs, {
					if(amp == 0, {
						// null amps are phase reset points
						phases = phases.add(item[1][j]);
						// keep fadein times constant under stretch so that onset phase
						// is correct once start amp is reached
						times = times.add(item[2][j] * recipStretch)
					}, {
						phases = phases.add(-inf); // otherwise ignore instantaneous phase
						times = times.add(item[2][j]);
					});
				});
			});
			phases = phases.add(item[1].last);
			
			// freq, amp, bw
			theseEnvs = [Env(item[4], times), Env(item[3], times), Env(item[5], times)];
			
			thisDelay = starttime + (i * ioff);
			theseEnvs = theseEnvs
				.collect({|env, j|
					var levelScale = 1;
					if(j == 0, {levelScale = pitch});
					if(j == 2, {levelScale = bw}); 
					
					if(thisDelay > 0, {env = env.delay(thisDelay)});
				
					EnvGen.ar(env, levelScale: levelScale, 
						timeScale: stretch); 
			});
			
			// now add phasegen
			
			if(thisDelay > 0, {
				// initial -inf ensures reset on first partial
				phaseEnv = Env([-inf] ++ phases, [thisDelay] ++ times);
			}, {
				phaseEnv = Env(phases, times);
			});
			
			theseEnvs = theseEnvs.add(LorisPhaseGen.ar(phaseEnv, timeScale: stretch));
			
			envs = envs.addAll(theseEnvs);
		});

		^envs.unlace(4);
	}
	
}