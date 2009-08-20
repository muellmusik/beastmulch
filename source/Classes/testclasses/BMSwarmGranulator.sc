// SC3 granulator class in the style of Barry Truax POD system. Created Toronto 19.06.03

// Consider having a private bus argument for internal stuff

// thisThread.seconds should be Main.elapsedTime?

BMSwarmGranulator {
	classvar <>latency = 0.05;
	var speakerList, boidSpace;
	var <>buffer, bufnum, server, <granGroup, sRate, playing = false, freed = false, clock;
	var <>intarget, <>inaddAction, envDefName, env, numChan, granBus;
	var decayTime, <targetDef;
	var <curEnvir;
	var kdTree;
	
	*new { arg speakerList, boidSpace, buffer, target = nil, addAction = \addToHead, targetDef = "PodGrain" ;
		^super.new.init(speakerList, boidSpace, buffer, target, addAction, targetDef);
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
	
	init { arg argspeakerList, argboidSpace, argbuffer, target, addAction, def;
		speakerList = argspeakerList;
		boidSpace = argboidSpace;
		server = target.asTarget.server;
		buffer = argbuffer;
		bufnum = buffer.bufnum;
		sRate = buffer.sampleRate ? 44100;
		intarget = target;
		inaddAction = addAction;
		speakerList = speakerList.select(_.isBMSpeaker);
		numChan = speakerList.size;
		targetDef = def;
		envDefName = "SmGranEnv" ++ speakerList.identityHash;
		SynthDef(envDefName, {
			arg attack, decay, amp = 1.0, gate = 1, i_in;
			var input, output;
			input = In.ar(i_in, numChan);
			output = input * EnvGen.kr(Env.asr(attack, 1.0, decay), gate, amp, 0, 1.0, 7);
			// free the nodes in the group when released
			speakerList.do({|spkr, i|
				Out.ar(spkr.index.postln, input[i].postln);
			});
		}).send(server);
		this.initKDTree;
	}
	
	initKDTree {
		var boundaries, maxXinv;
		boundaries = speakerList.boundaries;
		// normalise to maxX with 0 still centered
		maxXinv = [boundaries[0][0], boundaries[1][0]].abs.maxItem.reciprocal;
		kdTree = KDTree(speakerList.associationsCollectAs({|assoc, i| 
			var x, y, z;
			x = assoc.value.x * maxXinv;
			y = assoc.value.y * maxXinv;
			z = assoc.value.z * maxXinv;
			
			[x, y, z, i] 
			
		}, Array), lastIsLabel: true);
	}
	
	play { arg pitch = 1, stretch = 1, dur = 0.05, durRand = 0.1, delay = 0.0, delRand = 0,
		offset = 0.05, mul = 1, loop = 1, attack = 0, decay = 0.1 ... targetArgs;
		var rout, thisEnvir, granBusIndex, groupID;
		var startBund, outFunc;
		outFunc = Routine({
			loop({
				kdTree.nearest(boidSpace.next.pos)[0].label.yield;
			})
		});
		thisEnvir = (pitch: pitch, stretch: stretch, dur: dur, durRand: durRand, delay: delay, 
			delRand: delRand, offset: offset, mul: mul, loopF: loop, 
			targetArgs: targetArgs, targetDef: targetDef, outFunc: outFunc);
		curEnvir = thisEnvir;
		playing.not.if({
			playing = true;
			//CmdPeriod.add(this);
			decayTime = decay;
			clock = TempoClock.new;
			granBus = Bus.audio(server, numChan);
			granBusIndex = granBus.index;
			startBund = server.makeBundle(false, {
				granGroup = Group.new(intarget, inaddAction);
				
				env = Synth.new(envDefName, ["i_in", granBusIndex, "attack", 
					attack, "decay", decay], granGroup, \addToTail);
			});
			groupID = granGroup.nodeID;
			rout = Routine.new({
				var now, thisStart, nextTime, oldNow, oldStart = 0.0, thisDur, numGrains;
				oldNow = thisThread.seconds;
				inf.do({ arg i;
					numGrains = boidSpace.numBoids;
					thisDur = thisEnvir.dur.value + linrand(thisEnvir.durRand.value);
					now = thisThread.seconds;
					thisStart = (((now - oldNow) * thisEnvir.stretch.value.reciprocal) 
						+ oldStart);
					server.listSendBundle(latency, startBund ++  
						[["/s_new", thisEnvir.targetDef, -1, 0, groupID, 
						"i_sampbufnum", bufnum, "pointer", (thisStart * sRate).asInteger, "dur", 
						thisDur, "offset", (thisEnvir.offset.value * sRate), "pitchScale", 
						thisEnvir.pitch.value, "level", thisEnvir.mul.value/
						numGrains, "i_out", granBusIndex + 						thisEnvir.outFunc.value, "loop", thisEnvir.loopF.value] ++ 
						thisEnvir.targetArgs.value]);
					startBund = nil;
					// used to be "i_out", (i%numChan)
					// sendBundle can be timestamped, but no performance gain
					oldStart = thisStart;
					oldNow = now;
					nextTime = thisDur + thisEnvir.delay.value + linrand(thisEnvir.delRand.value)/ numGrains;
					nextTime.yield;
					
				});
			});
			rout.play(clock);
		}, {"Already Playing".inform});
	}
	
	// this needs to be fixed?
	stop { this.release(0.1);}
	
	release { arg time;
		var oldbus, oldclock, releaseTime;
		playing.if({ 
			playing = false;
			//CmdPeriod.remove(this);
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
	
//	cmdPeriod { this.stop; }
	
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
