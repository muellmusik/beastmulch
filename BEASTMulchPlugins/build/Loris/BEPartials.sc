BEPartials {
	var <name, <size, <partialList, <dur;

	*new { arg name, sdif;	
		^super.newCopyArgs(name).init(sdif);
	}
	
	init { arg sdif;	
		partialList = sdif.readFramesToPartials;		
		size = partialList.size;
		// calculate dur
		partialList.do({|item| 
			var end;
			end = item[2].sum + item[0]; // duration
			dur = dur.max(end);
		});
	}
	
	// fades in or out partials with non-zero start and/or end amps
	fadeInOut { arg list;
		var fadein = 0.001, fadeout = 0.01;
		list.do({ arg partial;
			// fadein
			if(partial[3].first > 0,{
				partial[0] = partial[0] - fadein; // roll back start slightly
				partial[1] = partial[1] - (2pi * partial[4].first * fadein); // roll back phase
				partial[2] = partial[2].insert(0, fadein); // short fadein time segment
				partial[3] = partial[3].insert(0, 0); // amp zero
				partial[4] = partial[4].insert(0, partial[4].first); // extra freq
				partial[5] = partial[5].insert(0, partial[5].first); // extra bw
			});
			
			// fadeout
			if(partial[3].last > 0,{
				partial[2] = partial[2].add(fadeout); // short fadeout segment
				partial[3] = partial[3].add(0); // amp zero
				partial[4] = partial[4].add(partial[4].last); // extra freq
				partial[5] = partial[5].add(partial[5].last); // extra bw
			});
		});
	
	}
	
	ar {| stretch = 1, pitch = 1, bw, ioff = 0|
		var envs, recipStretch;
		stretch = stretch.value; // could be a function
		pitch = pitch.value;
		bw = bw.value;
		
		recipStretch = stretch.reciprocal; // calculate only once
		partialList.fadeInOut; // fade in and out non-zero partialtimes
		partialList.do({ arg item, i;
			var starttime, times, amps, phases, numSegs, theseEnvs, thisDelay, phaseEnv;
			//item.postln;
			starttime = item[0];
			// correct times for fadeins by compensating for stretch
			numSegs = item[2].size;
			times = Array.new(numSegs);
			amps = item[4];
			phases = Array.new(numSegs + 1);
			amps.do({|amp, i|
				if(amp == 0 && (i != numSegs), {
					// null amps are phase reset points
					phases = phases.add(item[1][i]);
					// keep fadein times constant under stretch so that onset phase
					// is correct once start amp is reached
					times = times.add(item[2][i] * recipStretch)
				}, {
					phases = phases.add(-inf); // otherwise ignore instantaneous phase
					times = times.add(item[2][i]);
				});
			});
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