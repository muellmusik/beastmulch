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
			end = item[1].sum + item[0]; // duration
			dur = dur.max(end);
		});
	}
	
	// fades in or out partials with non-zero start and/or end amps
	fadeInOut {
		var fadein = 0.001, fadeout = 0.001; // loris standard
		partialList = partialList.collect({ arg partial;
			// fadein
			if(partial[5].first > 0,{
				partial[0] = partial[0] - fadein; // roll back startime slightly
				// roll back phase
				partial[3] = partial[3].insert(0, 
					partial[3].first - (2pi * partial[2].first * fadein)
				);
				
				partial[1] = partial[1].insert(0, fadein); // short fadein time segment
				partial[5] = partial[5].insert(0, 0); // amp zero
				partial[2] = partial[2].insert(0, partial[2].first); // extra freq
				partial[4] = partial[4].insert(0, partial[4].first); // extra bw
			});
			
			// fadeout
			if(partial[5].last > 0,{
				// extra phase
				partial[3] = partial[3].add(partial[3].last + (2pi * partial[2].last * fadeout));
				partial[1] = partial[1].add(fadeout); // short fadeout segment
				partial[5] = partial[5].add(0); // amp zero
				partial[2] = partial[2].add(partial[2].last); // extra freq
				partial[4] = partial[4].add(partial[4].last); // extra bw
			});
			partial
		});
	
	}
	
	ar {| stretch = 1, pitch = 1, bw = 1|
		var envs, recipStretch, oldStretch;
		this.fadeInOut; // fade in and out non-zero partial starts and ends
		
		partialList.do({ arg partial, i;
			var starttime, times, amps, phases, numSegs, theseEnvs, phaseEnv, thisStretch;
			starttime = partial[0];
			// correct times for fadeins by compensating for stretch
			numSegs = partial[1].size;
			times = Array.new(numSegs);
			amps = partial[5];
			phases = Array.new(numSegs + 1);
			
			thisStretch = stretch.value;
			// if stretch is a shared UGen no sense in creating multiple divide UGens
			if(thisStretch != oldStretch, {
				recipStretch = stretch.reciprocal;
			});
			oldStretch = thisStretch;
			amps.do({|amp, j|
				if(j < numSegs, {
					if(amp == 0, {
						// null amps are phase reset points
						phases = phases.add(partial[3][j]);
						// keep fadein times constant under stretch so that onset phase
						// is correct once start amp is reached
						times = times.add(partial[1][j] * recipStretch)
					}, {
						phases = phases.add(-inf); // otherwise ignore instantaneous phase
						times = times.add(partial[1][j]);
					});
				});
			});
			phases = phases.add(partial[3].last);
			
			// freq, bw, amp
			theseEnvs = [Env(partial[2], times), Env(partial[4], times), Env(partial[5], times)];
			
			theseEnvs = theseEnvs
				.collect({|env, j|
					var levelScale = 1;
					if(j == 0, {levelScale = pitch.value});
					if(j == 1, {levelScale = bw.value}); 
					
					if(starttime > 0, {env = env.delay(starttime)});
				
					EnvGen.ar(env, levelScale: levelScale, 
						timeScale: thisStretch); 
			});
			
			// now add phasegen
			
			if(starttime > 0, {
				// initial -inf ensures reset on first partial
				phaseEnv = Env([-inf] ++ phases, [starttime] ++ times);
			}, {
				phaseEnv = Env(phases, times);
			});
			
			// freq, phase, bw, amp as in BEOsc
			theseEnvs = theseEnvs.insert(1, LorisPhaseGen.ar(phaseEnv, timeScale: stretch));
			
			envs = envs.addAll(theseEnvs);
		});

		^envs.unlace(4);
	}
	
}