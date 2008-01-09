// SC3 granulator class in the style of Barry Truax POD system. Created Toronto 19.06.03

// Consider having a private bus argument for internal stuff

// thisThread.seconds should be Main.elapsedTime?

BMSwarmGranulator {
	classvar <>latency = 0.05;
	var <>bufnum, server, <granGroup, sRate, playing = false, freed = false, clock;
	var <>intarget, <>inaddAction, envDefName, env, numChan, granBus;
	var decayTime, <targetDef;
	var <curEnvir;
	
	*new { arg bufferNumber, numChannels = 2, sampleRate = 44100, target = nil,
		 addAction = \addToHead, targetDef = "PodGrain" ;
		^super.new.init(bufferNumber, numChannels, sampleRate, target, addAction, targetDef);
	}
	
//	*newFromPath { arg path, server;
//		// automatic buffer allocation; You must manually free it;
//	}
	
	*initClass {
		SynthDef.writeOnce("PodGrain",{ arg i_out=0, i_sampbufnum, pitchScale = 1.0, dur = 0.05, 
			pointer, offset = 0.0, level = 1.0, loop;
			var thisStart, thisDur, grain;
			thisStart = pointer + IRand(0, offset);
			grain = EnvGen.ar(Env.sine, 1.0, level, 0.0, dur, 2) 
				* PlayBuf.ar(1,i_sampbufnum,pitchScale * BufRateScale.ir(i_sampbufnum),
					1, thisStart,loop);
			OffsetOut.ar(i_out,grain);
		});
	
	}
	
	init { arg bufferNumber, numChannels, sampleRate, target, addAction, def;
		sRate = sampleRate;
		server = target.asTarget.server;
		bufnum = bufferNumber;
		intarget = target;
		inaddAction = addAction;
		numChan = numChannels.asInteger;
		targetDef = def;
		envDefName = "system-GranulatorEnv" ++ numChan;
		SynthDef(envDefName, {
			arg i_out=0, attack, decay, amp = 1.0, gate = 1, i_in;
			var input, output;
			input = In.ar(i_in, numChannels);
			output = input * EnvGen.kr(Env.asr(attack, 1.0, decay), gate, amp, 0, 1.0, 7);
			// free the nodes in the group when released
			Out.ar(i_out, output);
		}).send(server);
	}
	
	play { arg pitch = 1, stretch = 1, dur = 0.05, durRand = 0.1, delay = 0.0, delRand = 0,
		offset = 0.05, mul = 1, numGrains = 12, loop = 1, out = 0, attack = 0, decay = 0.1, 
		outFunc ... targetArgs;
		var rout, thisEnvir, granBusIndex, groupID;
		var startBund;
		outFunc = outFunc ? { numChan.rand };
		thisEnvir = (pitch: pitch, stretch: stretch, dur: dur, durRand: durRand, delay: delay, 
			delRand: delRand, offset: offset, mul: mul, numGrains: numGrains, loopF: loop, 
			targetArgs: targetArgs, targetDef: targetDef, outFunc: outFunc);
		curEnvir = thisEnvir;
		playing.not.if({
			playing = true;
			CmdPeriod.add(this);
			decayTime = decay;
			clock = TempoClock.new;
			granBus = Bus.audio(server, numChan);
			granBusIndex = granBus.index;
			startBund = server.makeBundle(false, {
				granGroup = Group.new(intarget, inaddAction);
				
				env = Synth.new(envDefName, ["i_in", granBusIndex, "i_out", out, "attack", 
					attack, "decay", decay], granGroup, \addToTail);
			});
			groupID = granGroup.nodeID;
			rout = Routine.new({
				var now, thisStart, nextTime, oldNow, oldStart = 0.0, thisDur;
				oldNow = thisThread.seconds;
				inf.do({ arg i;
					thisDur = thisEnvir.dur.value + linrand(thisEnvir.durRand.value);
					now = thisThread.seconds;
					thisStart = (((now - oldNow) * thisEnvir.stretch.value.reciprocal) 
						+ oldStart);
					server.listSendBundle(latency, startBund ++  
						[["/s_new", thisEnvir.targetDef, -1, 0, groupID, 
						"i_sampbufnum", bufnum, "pointer", (thisStart * sRate).asInteger, "dur", 
						thisDur, "offset", (thisEnvir.offset.value * sRate), "pitchScale", 
						thisEnvir.pitch.value, "level", thisEnvir.mul.value/
						thisEnvir.numGrains.value, "i_out", granBusIndex + 						thisEnvir.outFunc.value, "loop", thisEnvir.loopF.value] ++ 
						thisEnvir.targetArgs.value]);
					startBund = nil;
					// used to be "i_out", (i%numChan)
					// sendBundle can be timestamped, but no performance gain
					oldStart = thisStart;
					oldNow = now;
					nextTime = thisDur + thisEnvir.delay.value + linrand(thisEnvir.delRand.value)/
						thisEnvir.numGrains.value;
					nextTime.yield;
					
				});
			});
			rout.play(clock);
		}, {"Already Playing".inform});
	}
	
	stop { this.release(0.1);}
	
	release { arg time;
		var oldbus, oldclock, releaseTime;
		playing.if({ 
			playing = false;
			CmdPeriod.remove(this);
			releaseTime = time ? decayTime;
			server.sendBundle((releaseTime) + 0.05, granGroup.freeMsg);
			granGroup = nil;
			env.release(releaseTime);
			env = nil;
			oldbus = granBus; granBus = nil;
			oldclock = clock; 
			clock = nil; 
			SystemClock.sched(releaseTime - (latency ? 0) - 0.05, {oldclock.stop;});
			SystemClock.sched(releaseTime + 0.05, {oldbus.free;});
		
		},{ "Not Playing".inform; });
	}
	
	free {
		freed.not.if({
			playing.if({ this.release(0.1) });
		}, {"Already freed".inform});
	}
	
	cmdPeriod { this.stop; }
	
	doesNotUnderstand { arg selector ... args; // assume I know what I'm doing...
		curEnvir.perform(selector, *args);
	}
	// this persists
	targetDef_ { arg def;
		playing.if({curEnvir.targetDef = def });
		targetDef = def;
	}
	
	loop_ { arg flag;
		curEnvir.loopF = flag;
	}
}
