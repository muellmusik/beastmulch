// operates on the Speakerlist directly
BMAutoBalancer {

	*run {|speakerList, okayFunc, server, in = 0, onlyFullRange = true, normalize = true|
		var target, responders, min, diff;
		target = server.asTarget;
		server = target.server;
		responders = ();
		{
			this.sendDef(server);
			server.sync;
			"\\\\\\\\\\\\\\\ Auto Level Balance Starting\n".postln;
			speakerList.do({|speaker, index|
				var synth, array, responder, count = 0;
				if(speaker.isBMSpeaker and: {speaker.spec.fullRange || onlyFullRange.not}, {
					array = Array.new(3);
					responder = OSCresponderNode(server.addr, 'BM-AutoBalance', {|time, resp, msg|
						if(msg[2] == index, {
							count = count + 1;
							array.add(msg[3]);
							if(count == 3, {
								speaker.autoTrim = array.mean.ampdb;
								"Level for %: % dBFS (RMS)\n".postf(speaker.name, speaker.autoTrim);
								responders[speaker.name] = nil;
								resp.remove;
							});
						});
					});
					responders[speaker.name] = responder;
					responder.add;
					3.do({|i|
						synth = Synth("BMAutoBalance", [in: in, out: speaker, id: index]);
						1.wait;
					});

				}, {"Item % not a normal Speaker, skipping it...\n".postf(index + 1)});
				
			});
			1.wait;
			"\nChecking results".postln;
			responders.keysValuesDo({|key, value|
				"% failed\n".postf(key);
				value.remove;
			});
			
			(responders.size == 0).if({
				"Results Complete\n".postln;
				normalize.if({
					"Normalizing".postln;
					min = speakerList.select({|speaker| 
						speaker.isBMSpeaker and: {speaker.spec.fullRange || onlyFullRange.not}
					}).collectAs({|speaker| speaker.value.autoTrim }, Array).minItem; 
					speakerList.do({|speaker| 
						if(speaker.isBMSpeaker and: 
							{speaker.spec.fullRange || onlyFullRange.not}, {
							diff = min - speaker.autoTrim;
							if(diff <= 0, { 
								speaker.autoTrim = diff;
								"Normalized Autotrim for %: % dBFS\n".postf(speaker.name, diff);
							});
						});
					});
				});
				okayFunc.value(speakerList);
			});
			"\n\\\\\\\\\\\\\\\ Auto Level Balance Done\n".postln;
			
		}.fork;
	
	}
	
	*sendDef {|server|
		SynthDef("BMAutoBalance", {|out, in = 0, amp = 0.3, id|
			var max, trig;
			trig = Impulse.ar(0);
			Out.ar(out, PinkNoise.ar(amp) * EnvGen.kr(Env.linen, timeScale: 0.3));
			max = RunningMax.ar(RunningSum.ar(SoundIn.ar(in).squared));
			SendReply.ar(DelayN.ar(trig, 0.3, 0.3), 'BM-AutoBalance', [max], id);
			FreeSelf.kr(DelayN.ar(trig, 0.35, 0.35)); // slightly later
		}).send(server);
	
	}

}

//BMAutoBalancerGUI : BMAbstractGUI {
//	
//	*new {| startArray, okayFunc, name, origin |
//		  ^super.new.init(startArray.deepCopy ?? { BMInOutArray[]}, okayFunc, name)
//		  	.makeWindow(origin ? (40@200));
//	}
//	
//	init {|startArray, argokayFunc, argname|
//		outputArray = startArray;
//		okayFunc = argokayFunc;
//		name = argname;
//	}
//
//}